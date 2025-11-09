package com.cheatcrusher.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatcrusher.data.local.LocalDataRepository
import com.cheatcrusher.data.local.OfflineRepository
import com.cheatcrusher.data.local.LocalHistoryItem
import com.cheatcrusher.data.local.PendingSubmission
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import com.cheatcrusher.data.work.PendingUploadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubmissionItem(
    val quizTitle: String,
    val rollNumber: String,
    val status: String, // submitted | pending
    val submittedAtMillis: Long?
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
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(SubmissionUiState())
    val uiState: StateFlow<SubmissionUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val history: List<LocalHistoryItem> = localRepo.listHistory()
                val pending: List<PendingSubmission> = offlineRepo.listPendingSubmissions()
                val cached = offlineRepo.listCachedQuizzes()

                val submittedItems = history.map { h ->
                    SubmissionItem(
                        quizTitle = h.quizTitle,
                        rollNumber = h.rollNumber,
                        status = "submitted",
                        submittedAtMillis = h.submittedAt
                    )
                }

                // Deduplicate pending entries by responseId to avoid multiple rows from rapid taps
                val pendingDistinct = pending.distinctBy { it.responseId }
                val pendingItems = pendingDistinct.map { p ->
                    SubmissionItem(
                        quizTitle = SubmissionStatusMapper.resolveTitleForPending(p.quizId, cached),
                        rollNumber = p.rollNumber,
                        status = "pending",
                        submittedAtMillis = null
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
                WorkManager.getInstance(context).enqueue(work)
                // Optimistically refresh; worker updates and removes entries
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
