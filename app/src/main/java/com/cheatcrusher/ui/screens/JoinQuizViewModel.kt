package com.cheatcrusher.ui.screens
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatcrusher.data.firebase.FirestoreRepository
import com.cheatcrusher.data.local.LocalDataRepository
import com.cheatcrusher.data.local.OfflineRepository
import com.cheatcrusher.domain.Quiz
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinQuizUiState(
    val quiz: Quiz? = null,
    val joinedQuizId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class JoinQuizViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val localDataRepository: LocalDataRepository,
    private val offlineRepository: OfflineRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(JoinQuizUiState())
    val uiState: StateFlow<JoinQuizUiState> = _uiState.asStateFlow()
    
    fun findQuizByCode(code: String) {
        val normalizedCode = code.trim().uppercase()
        if (normalizedCode.length != 6) {
            _uiState.value = _uiState.value.copy(error = "Please enter a valid 6-character quiz code")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                // New offline-first path: fetch rawJson by download code, cache, and parse
                firestoreRepository.getQuizRawByDownloadCode(normalizedCode).fold(
                    onSuccess = { (docId, rawJson) ->
                        val ok = offlineRepository.cacheRawQuizFromDownload(docId, normalizedCode, rawJson)
                        if (!ok) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = "Failed to cache quiz for offline use"
                            )
                        } else {
                            val cached = offlineRepository.getCachedQuizById(docId)
                            val parsed = cached?.let { offlineRepository.parseCachedQuiz(it) }
                            if (parsed != null) {
                                _uiState.value = _uiState.value.copy(
                                    quiz = parsed,
                                    joinedQuizId = parsed.id,
                                    isLoading = false,
                                    error = null
                                )
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = "Failed to parse downloaded quiz"
                                )
                            }
                        }
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "Quiz not found"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to find quiz"
                )
            }
        }
    }
    
    // Removed server-based join window check; join is gated offline by allowedJoinCodes
    private fun canJoinQuiz(quiz: Quiz): Boolean = true
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
