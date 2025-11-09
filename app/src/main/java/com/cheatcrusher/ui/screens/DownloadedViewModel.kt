package com.cheatcrusher.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatcrusher.data.local.CachedQuiz
import com.cheatcrusher.data.local.LocalDataRepository
import com.cheatcrusher.data.local.OfflineRepository
import com.cheatcrusher.util.TimeIntegrity
import com.cheatcrusher.util.TimeSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadedItem(
    val cached: CachedQuiz,
    val secondsUntilStart: Long,
    val isActive: Boolean,
    val isAttempted: Boolean
)

data class DownloadedUiState(
    val items: List<DownloadedItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DownloadedViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
    private val localRepository: LocalDataRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadedUiState())
    val uiState: StateFlow<DownloadedUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val cached = offlineRepository.listCachedQuizzes()
                val history = localRepository.listHistory()
                val pending = offlineRepository.listPendingSubmissions()
                val attemptedIds = (history.map { it.quizId } + pending.map { it.quizId }).toSet()
                val items = cached.map { c ->
                    val snapshot = TimeSnapshot(
                        startsAtMillis = c.startsAtMillis,
                        endsAtMillis = c.endsAtMillis,
                        downloadedAtElapsedRealtime = c.downloadedAtElapsedRealtime
                    )
                    val now = TimeIntegrity.estimateServerNow(snapshot)
                    val secondsUntilStart = ((c.startsAtMillis - now) / 1000L).coerceAtLeast(0L)
                    val isActive = TimeIntegrity.isActiveNow(snapshot)
                    val isAttempted = attemptedIds.contains(c.quizId)
                    DownloadedItem(cached = c, secondsUntilStart = secondsUntilStart, isActive = isActive, isAttempted = isAttempted)
                }
                _uiState.value = DownloadedUiState(items = items, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteCached(quizId: String) {
        viewModelScope.launch {
            try {
                offlineRepository.deleteCachedQuiz(quizId)
                refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}