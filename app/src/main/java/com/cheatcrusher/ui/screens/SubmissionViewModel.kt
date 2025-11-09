package com.cheatcrusher.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatcrusher.data.local.LocalDataRepository
import com.cheatcrusher.data.local.OfflineRepository
import com.cheatcrusher.data.local.LocalHistoryItem
import com.cheatcrusher.data.local.PendingSubmission
import com.cheatcrusher.data.firebase.FirestoreRepository
import androidx.lifecycle.Observer
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import com.cheatcrusher.data.work.PendingUploadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubmissionItem(
    val quizId: String,
    val quizTitle: String,
    val rollNumber: String,
    val status: String, // submitted | pending
    val submittedAtMillis: Long?,
    val pendingId: Long? = null,
    val detailsMissing: Boolean = false,
    val score: Double? = null
)

data class SubmissionUiState(
    val items: List<SubmissionItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SubmissionViewModel @Inject constructor(
    private val localRepo: LocalDataRepository,
    private val offlineRepo: OfflineRepository,
    private val firestoreRepo: FirestoreRepository,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(SubmissionUiState())
    val uiState: StateFlow<SubmissionUiState> = _uiState.asStateFlow()
    private val workObservers = mutableMapOf<java.util.UUID, Observer<WorkInfo>>()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val history: List<LocalHistoryItem> = localRepo.listHistory()
                val pending: List<PendingSubmission> = offlineRepo.listPendingSubmissionsAny()
                val cached = offlineRepo.listCachedQuizzes()

                val submittedItems = history.map { h ->
                    SubmissionItem(
                        quizId = h.quizId,
                        quizTitle = h.quizTitle,
                        rollNumber = h.rollNumber,
                        status = "submitted",
                        submittedAtMillis = h.submittedAt,
                        score = h.score
                    )
                }

                // Deduplicate pending by responseId and hide ones that already have a submitted history
                val submittedKeys = history.map { it.quizId + "|" + it.rollNumber }.toSet()
                val pendingDistinct = pending
                    .distinctBy { it.responseId }
                    .filter { (it.quizId + "|" + it.rollNumber) !in submittedKeys }
                val pendingItems = pendingDistinct.map { p ->
                    val raw = try {
                        val cq = cached.find { it.quizId == p.quizId }
                        val rq = cq?.metadataJson
                        com.cheatcrusher.util.JoinCodeVerifier.parse(rq)
                    } catch (_: Exception) { null }
                    val info = try { com.google.gson.Gson().fromJson(p.studentInfoJson, Map::class.java) as Map<String, String> } catch (_: Exception) { emptyMap() }
                    val missing = (raw?.preForm?.fields ?: emptyList()).any { f -> f.required && (info[f.key]?.toString()
                        ?.isBlank() == true) }
                    // Compute local score for pending using cached quiz and answers
                    // Prefer remote quiz (IDs match answers captured during join); fallback to cached
                    val domainQuiz = try {
                        val remote = firestoreRepo.getQuizById(p.quizId)
                        remote.getOrNull()
                    } catch (_: Exception) { null } ?: run {
                        try { cached.find { it.quizId == p.quizId }?.let { offlineRepo.parseCachedQuiz(it) } } catch (_: Exception) { null }
                    }
                    val answers: List<com.cheatcrusher.domain.Answer> = try {
                        val type = object : com.google.gson.reflect.TypeToken<List<com.cheatcrusher.domain.Answer>>() {}.type
                        com.google.gson.Gson().fromJson(p.answersJson, type)
                    } catch (_: Exception) { emptyList() }
                    val localScore = domainQuiz?.let { calculateLocalScore(it, answers) }
                    SubmissionItem(
                        quizId = p.quizId,
                        quizTitle = SubmissionStatusMapper.resolveTitleForPending(p.quizId, cached),
                        rollNumber = p.rollNumber,
                        status = p.uploadStatus,
                        submittedAtMillis = null,
                        pendingId = p.id,
                        detailsMissing = missing,
                        score = localScore
                    )
                }

                _uiState.value = SubmissionUiState(
                    items = submittedItems + pendingItems,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun uploadAllPending() {
        viewModelScope.launch {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val work = OneTimeWorkRequestBuilder<PendingUploadWorker>()
                    .setConstraints(constraints)
                    .build()
                // Ensure only one bulk upload runs at a time
                WorkManager.getInstance(context).enqueueUniqueWork("pending_upload_all", ExistingWorkPolicy.KEEP, work)
                observeWork(work.id)
                // Optimistically refresh; worker updates and removes entries
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    fun uploadPending(id: Long) {
        viewModelScope.launch {
            try {
                // Mark immediately for responsive UI
                offlineRepo.markPendingUploading(id)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val input = workDataOf("pendingId" to id)
                val work = OneTimeWorkRequestBuilder<PendingUploadWorker>()
                    .setConstraints(constraints)
                    .setInputData(input)
                    .build()
                // Prevent duplicate scheduling if user taps twice
                WorkManager.getInstance(context).enqueueUniqueWork("pending_upload_" + id, ExistingWorkPolicy.KEEP, work)
                observeWork(work.id)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
    fun updatePendingDetails(id: Long, values: Map<String, String>) {
        viewModelScope.launch {
            try {
                val json = com.google.gson.Gson().toJson(values)
                offlineRepo.updatePendingStudentInfo(id, json)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deletePending(id: Long) {
        viewModelScope.launch {
            try {
                offlineRepo.deletePendingSubmission(id)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteSubmittedHistory(quizId: String, rollNumber: String) {
        viewModelScope.launch {
            try {
                localRepo.deleteHistoryForQuizAndRoll(quizId, rollNumber)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    private fun observeWork(id: java.util.UUID) {
        val live = WorkManager.getInstance(context).getWorkInfoByIdLiveData(id)
        val obs = Observer<WorkInfo> { info ->
            when {
                info.state == WorkInfo.State.RUNNING -> {
                    // Ensure UI reflects 'uploading'
                    refresh()
                }
                info.state.isFinished -> {
                    // Refresh to reflect success/failure and cleanup observers
                    refresh()
                    live.removeObserver(workObservers.remove(id) ?: return@Observer)
                }
            }
        }
        workObservers[id] = obs
        live.observeForever(obs)
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any observers
        val wm = WorkManager.getInstance(context)
        workObservers.forEach { (id, obs) ->
            wm.getWorkInfoByIdLiveData(id).removeObserver(obs)
        }
        workObservers.clear()
    }

    private fun calculateLocalScore(quiz: com.cheatcrusher.domain.Quiz, answers: List<com.cheatcrusher.domain.Answer>): Double {
        var totalScore = 0.0
        var totalWeight = 0.0

        quiz.questions.forEach { question ->
            totalWeight += question.weight

            val answer = answers.find { it.questionId == question.id }
            if (answer != null) {
                val isCorrect = when (question.type) {
                    com.cheatcrusher.domain.QuestionType.MCQ -> {
                        answer.optionIds.size == 1 &&
                        answer.optionIds.firstOrNull() in question.correct
                    }
                    com.cheatcrusher.domain.QuestionType.MSQ -> {
                        answer.optionIds.sorted() == question.correct.sorted()
                    }
                    com.cheatcrusher.domain.QuestionType.TEXT -> {
                        false
                    }
                }

                if (isCorrect) {
                    totalScore += question.weight
                }
            }
        }

        return if (totalWeight > 0) (totalScore / totalWeight) * 100.0 else 0.0
    }
}
