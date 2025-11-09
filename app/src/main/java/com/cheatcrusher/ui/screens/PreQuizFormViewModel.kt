package com.cheatcrusher.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatcrusher.data.firebase.FirestoreRepository
import com.cheatcrusher.data.local.LocalDataRepository
import com.cheatcrusher.data.local.OfflineRepository
import com.cheatcrusher.domain.FormField
import com.cheatcrusher.domain.Quiz
import com.cheatcrusher.util.DeviceUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreQuizFormUiState(
    val quiz: Quiz? = null,
    val fields: List<FormField> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PreQuizFormViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val deviceUtils: DeviceUtils,
    private val offlineRepository: OfflineRepository,
    private val localDataRepository: LocalDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PreQuizFormUiState())
    val uiState: StateFlow<PreQuizFormUiState> = _uiState.asStateFlow()

  fun loadQuiz(quizId: String, useCachedIfAvailable: Boolean = true) {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, error = null)
      // Try offline first if requested
      val cached = if (useCachedIfAvailable) offlineRepository.getCachedQuizById(quizId) else null
      val offlineQuiz = cached?.let { offlineRepository.parseCachedQuiz(it) }
      if (offlineQuiz != null) {
        val initial = if (offlineQuiz.preJoinFields.isNotEmpty()) offlineQuiz.preJoinFields else defaultSchema()
        val schema = ensureEmail(initial)
          val prefilled = prefillValues(schema)
          _uiState.value = _uiState.value.copy(
            quiz = offlineQuiz,
            fields = schema,
            values = prefilled,
            isLoading = false
          )
        return@launch
      }
      firestoreRepository.getQuizById(quizId).fold(
        onSuccess = { quiz ->
          val initial = if (quiz.preJoinFields.isNotEmpty()) quiz.preJoinFields else defaultSchema()
          val schema = ensureEmail(initial)
          val prefilled = prefillValues(schema)
          _uiState.value = _uiState.value.copy(
            quiz = quiz,
            fields = schema,
            values = prefilled,
            isLoading = false
          )
        },
        onFailure = { e ->
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

    fun validate(): String? {
        val fields = _uiState.value.fields
        val values = _uiState.value.values
        fields.forEach { f ->
            if (f.required && (values[f.id].isNullOrBlank())) {
                return "${f.label} is required"
            }
        }
        return null
    }

    suspend fun buildPayloadAndCheckNoRetake(): Result<Pair<String, String>> {
        val values = _uiState.value.values
        val roll = values["roll"]?.trim() ?: ""
        val quizId = _uiState.value.quiz?.id ?: return Result.failure(Exception("Quiz not loaded"))
        val deviceId = try { deviceUtils.getDeviceId() } catch (e: Exception) { "" }

        // Check existing response by roll OR device to block retakes BEFORE starting
        val byRoll = firestoreRepository.getResponseByQuizAndRoll(quizId, roll)
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
}