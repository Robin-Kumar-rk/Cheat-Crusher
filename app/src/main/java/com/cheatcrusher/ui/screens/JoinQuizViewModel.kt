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
                firestoreRepository.getQuizByCode(normalizedCode).fold(
                    onSuccess = { quiz ->
                        // Cache quiz offline with time integrity guard
                        viewModelScope.launch {
                            val cachedOk = offlineRepository.cacheQuiz(quiz, normalizedCode)
                            if (!cachedOk) {
                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    error = "Automatic network time is disabled. Enable auto date & time, then re-download the quiz."
                                )
                            } else {
                                // Enforce start window at client even if server rules are loose
                                if (!firestoreRepository.canJoinQuiz(quiz)) {
                                    _uiState.value = _uiState.value.copy(
                                        quiz = quiz,
                                        isLoading = false,
                                        error = "Quiz not started yet. It’s downloaded for offline and will unlock at the start time."
                                    )
                                } else {
                                    _uiState.value = _uiState.value.copy(
                                        quiz = quiz,
                                        joinedQuizId = quiz.id,
                                        isLoading = false
                                    )
                                }
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
    
    private fun canJoinQuiz(quiz: Quiz): Boolean {
        val now = Timestamp.now()
        // Match Firestore rules: joining is only allowed while active window.
        return now >= quiz.startsAt && now <= quiz.endsAt
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}