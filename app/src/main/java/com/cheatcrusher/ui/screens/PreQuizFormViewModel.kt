package com.cheatcrusher.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatcrusher.data.firebase.FirestoreRepository
import com.cheatcrusher.data.local.LocalDataRepository
import com.cheatcrusher.data.local.OfflineRepository
import com.cheatcrusher.domain.FormField
import com.cheatcrusher.domain.Quiz
import com.cheatcrusher.util.DeviceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log

data class PreQuizFormUiState(
    val quiz: Quiz? = null,
    val fields: List<FormField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val requiresJoinCode: Boolean = false,
    val joinCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PreQuizFormViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val deviceUtils: DeviceUtils,
    private val offlineRepository: OfflineRepository,
    private val localDataRepository: LocalDataRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreQuizFormUiState())
    val uiState: StateFlow<PreQuizFormUiState> = _uiState.asStateFlow()

  fun loadQuiz(quizId: String, useCachedIfAvailable: Boolean = true) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      // Try offline first if requested
      Log.d("PreQuizForm", "Loading quizId=$quizId useCached=$useCachedIfAvailable")
      val cached = if (useCachedIfAvailable) offlineRepository.getCachedQuizById(quizId) else null
      val offlineQuiz = cached?.let { offlineRepository.parseCachedQuiz(it) }
      if (offlineQuiz != null) {
          Log.d("PreQuizForm", "Loaded offline quiz title=${offlineQuiz.title}")
          val initial = if (offlineQuiz.preJoinFields.isNotEmpty()) offlineQuiz.preJoinFields else defaultSchema()
          val schema = ensureEmail(initial)
          val prefilled = prefillValues(schema)
          val requiresJoin = requiresJoinCodeFromRaw(offlineQuiz.rawJson)
          _uiState.value = _uiState.value.copy(
            quiz = offlineQuiz,
            fields = schema,
            values = prefilled,
            requiresJoinCode = requiresJoin,
            isLoading = false
          )
        return@launch
      }
      firestoreRepository.getQuizById(quizId).fold(
        onSuccess = { quiz ->
          Log.d("PreQuizForm", "Loaded quiz from server title=${quiz.title}")
          val initial = if (quiz.preJoinFields.isNotEmpty()) quiz.preJoinFields else defaultSchema()
          val schema = ensureEmail(initial)
          val prefilled = prefillValues(schema)
          val requiresJoin = requiresJoinCodeFromRaw(quiz.rawJson)
          _uiState.value = _uiState.value.copy(
            quiz = quiz,
            fields = schema,
            values = prefilled,
            requiresJoinCode = requiresJoin,
            isLoading = false
          )
        },
        onFailure = { e ->
          Log.e("PreQuizForm", "Failed to load quiz", e)
          _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
      )
    }
    }

    private suspend fun getProfileMap(): Map<String, String> {
        val profile = try { localDataRepository.getStudentProfile() } catch (e: Exception) { null }
        return if (profile != null) {
            mapOf(
                "name" to profile.name,
                "email" to profile.email,
                "roll" to profile.rollNumber,
                "section" to profile.section
            )
        } else emptyMap()
    }

    private suspend fun prefillValuesAsync(fields: List<FormField>): Map<String, String> {
        val profile = getProfileMap()
        return fields.associate { f -> f.id to (profile[f.id] ?: "") }
    }

    private fun prefillValues(fields: List<FormField>): Map<String, String> {
        // Launch a background update to prefill; return empty first if needed
        // But since we're already in a coroutine context in loadQuiz, we prefer sync mapping using profile map
        // However localDataRepository call is suspend; we handled it in prefillValuesAsync, but here we assume we've called within coroutine
        // So this function will be replaced by a blocking fallback using available state; to keep simple, call suspend via runBlocking is not ideal
        // We will compute empty map here and then update asynchronously
        val empty = fields.associate { it.id to "" }
        viewModelScope.launch {
            val filled = prefillValuesAsync(fields)
            _uiState.value = _uiState.value.copy(values = filled)
        }
        return empty
    }

    private fun ensureEmail(fields: List<FormField>): List<FormField> {
        return if (fields.any { it.id == "email" }) {
            fields
        } else {
            fields + FormField(id = "email", label = "Email", type = "text", required = false)
        }
    }

    private fun defaultSchema(): List<FormField> = listOf(
        FormField(id = "name", label = "Name", type = "text", required = false),
        FormField(id = "email", label = "Email", type = "text", required = false),
        FormField(id = "roll", label = "Roll Number", type = "text", required = true),
        FormField(id = "section", label = "Section", type = "text", required = false)
    )

    fun updateValue(fieldId: String, value: String) {
        val newValues = _uiState.value.values.toMutableMap()
        newValues[fieldId] = value
        _uiState.value = _uiState.value.copy(values = newValues)
    }

    fun updateJoinCode(value: String) {
        _uiState.value = _uiState.value.copy(joinCode = value)
    }

    fun validate(): String? {
        // Enforce network-provided time (Automatic date & time) before joining
        val autoTime = com.cheatcrusher.util.TimeIntegrity.isAutoTimeEnabled(context)
        if (!autoTime) {
            val msg = "Please enable Automatic date & time to join this quiz"
            _uiState.value = _uiState.value.copy(error = msg)
            android.util.Log.w("PreQuizForm", "Auto time disabled: blocking join")
            return msg
        }
        // Do not block start on missing details; they are required before upload
        if (_uiState.value.requiresJoinCode) {
            val raw = com.cheatcrusher.util.JoinCodeVerifier.parse(_uiState.value.quiz?.rawJson)
            val code = (_uiState.value.joinCode).trim()
            val configuredPwd = raw?.unlockPassword?.trim()

            if (configuredPwd.isNullOrBlank()) {
                val msg = "Invalid join code"
                _uiState.value = _uiState.value.copy(error = msg)
                Log.w("PreQuizForm", "Join code invalid: unlock password missing in quiz config")
                return msg
            }

            // Runtime guard: on older Android versions, perform format/password check only (no time window)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val result = com.cheatcrusher.util.JoinCodeVerifier.verify(raw, code)
                if (!result.ok) {
                    val msg = result.error ?: "Invalid join code"
                    _uiState.value = _uiState.value.copy(error = msg)
                    Log.w("PreQuizForm", "Join code invalid: ${result.error}")
                    return msg
                } else {
                    // Clear previous error
                    _uiState.value = _uiState.value.copy(error = null)
                }
            } else {
                // Pre-O verification: check password and checksum for J2 codes, or password for legacy
                val ok = when {
                    code.startsWith("J2") -> {
                        val body = code.removePrefix("J2")
                        val parts = body.split("-")
                        if (parts.size != 2) false else {
                            val b64 = parts[0]
                            val checksum = parts[1]
                            try {
                                val payload = base64UrlDecodeCompat(b64)
                                val calc = sha256HexCompat(payload + configuredPwd.toByteArray())
                                calc.startsWith(checksum.lowercase())
                            } catch (e: Exception) {
                                false
                            }
                        }
                    }
                    else -> {
                        val pieces = code.split("|")
                        pieces.size == 2 && (pieces[0].trim() == configuredPwd)
                    }
                }
                if (!ok) {
                    val msg = "Invalid join code"
                    _uiState.value = _uiState.value.copy(error = msg)
                    Log.w("PreQuizForm", "Join code invalid (pre-O simple check)")
                    return msg
                } else {
                    _uiState.value = _uiState.value.copy(error = null)
                    Log.w("PreQuizForm", "Join code format valid on OS<26; time window not enforced locally")
                }
            }
        }
        return null
    }

    // Helpers for pre-O verification that avoid java.time
    private fun base64UrlDecodeCompat(s: String): ByteArray {
        var str = s.replace('-', '+').replace('_', '/')
        val pad = (4 - str.length % 4) % 4
        str += "=".repeat(pad)
        return android.util.Base64.decode(str, android.util.Base64.DEFAULT)
    }

    private fun sha256HexCompat(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    suspend fun buildPayloadAndCheckNoRetake(): Result<Pair<String, String>> {
        // Double-check auto time before building payload
        val autoTime = com.cheatcrusher.util.TimeIntegrity.isAutoTimeEnabled(context)
        if (!autoTime) {
            val msg = "Automatic date & time is required to join this quiz"
            _uiState.value = _uiState.value.copy(error = msg)
            android.util.Log.w("PreQuizForm", "Auto time disabled: blocking payload build")
            return Result.failure(Exception(msg))
        }
        val values = _uiState.value.values
        val roll = values["roll"]?.trim() ?: ""
        val quizId = _uiState.value.quiz?.id ?: return Result.failure(Exception("Quiz not loaded"))
        val deviceId = try { deviceUtils.getDeviceId() } catch (e: Exception) { "" }

        // Check existing response by roll OR device to block retakes BEFORE starting
        val byRoll = if (roll.isNotBlank()) {
            firestoreRepository.getResponseByQuizAndRoll(quizId, roll)
        } else {
            Result.failure(Exception("No roll provided"))
        }
        val byDevice = if (deviceId.isNotBlank()) firestoreRepository.getResponseByQuizAndDeviceId(quizId, deviceId) else Result.failure(Exception("No device id"))

        if (byRoll.isSuccess || byDevice.isSuccess) {
            val response = (byRoll.getOrNull() ?: byDevice.getOrNull())
            val status = buildString {
                append("Join blocked: already attempted this quiz")
                response?.let {
                    val parts = mutableListOf<String>()
                    if (it.disqualified) parts.add("disqualified")
                    if (it.flagged) parts.add("flagged")
                    val gs = it.gradeStatus.name.lowercase()
                    parts.add("status: $gs")
                    if (it.clientSubmittedAt != null) {
                        parts.add("submitted at: ${it.clientSubmittedAt.toDate()}")
                    }
                    if (parts.isNotEmpty()) {
                        append(" (" + parts.joinToString(", ") + ")")
                    }
                }
                if (byDevice.isSuccess && !byRoll.isSuccess) {
                    append(" — same device")
                }
            }
            _uiState.value = _uiState.value.copy(error = status)
            return Result.failure(Exception(status))
        }

        // Include deviceId in payload values (optional, for downstream display)
        val infoMap = values.toMutableMap().apply {
            if (deviceId.isNotBlank()) put("deviceId", deviceId)
        }
        val json = infoMap.entries.joinToString(prefix = "{", postfix = "}", separator = ",") {
            "\"${it.key}\":\"${it.value}\""
        }
        val infoB64 = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
        return Result.success(Pair(roll, infoB64))
    }

    private fun requiresJoinCodeFromRaw(rawJson: String?): Boolean {
        val raw = com.cheatcrusher.util.JoinCodeVerifier.parse(rawJson)
        return !raw?.unlockPassword.isNullOrBlank()
    }
}
