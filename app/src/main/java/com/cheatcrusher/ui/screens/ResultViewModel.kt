package com.cheatcrusher.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatcrusher.data.firebase.FirestoreRepository
import com.cheatcrusher.domain.Response
import com.cheatcrusher.domain.Quiz
import com.cheatcrusher.domain.QuestionType
import com.cheatcrusher.domain.Answer
import com.cheatcrusher.domain.Question
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Breakdown model for UI
data class AnswerBreakdownItem(
    val questionId: String,
    val questionText: String,
    val selectedOptionTexts: List<String>,
    val correctOptionTexts: List<String>,
    val earnedMarks: Double,
    val maxMarks: Double,
    val isCorrect: Boolean,
    val isTextQuestion: Boolean
)

data class ResultUiState(
    val response: Response? = null,
    val quiz: Quiz? = null,
    val breakdown: List<AnswerBreakdownItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ResultUiState())
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()
    
    fun loadResult(responseId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                firestoreRepository.getResponseById(responseId).fold(
                    onSuccess = { response ->
                        // After loading response, load quiz details to build breakdown
                        firestoreRepository.getQuizById(response.quizId).fold(
                            onSuccess = { quiz ->
                                val breakdown = computeBreakdown(quiz, response)
                                _uiState.value = _uiState.value.copy(
                                    response = response,
                                    quiz = quiz,
                                    breakdown = breakdown,
                                    isLoading = false
                                )
                            },
                            onFailure = { exception ->
                                _uiState.value = _uiState.value.copy(
                                    response = response,
                                    isLoading = false,
                                    error = exception.message ?: "Failed to load quiz details"
                                )
                            }
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "Failed to load result"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load result"
                )
            }
        }
    }
    
    // Build per-question breakdown for UI
    private fun computeBreakdown(quiz: Quiz, response: Response): List<AnswerBreakdownItem> {
        return quiz.questions.map { question ->
            val answer: Answer? = response.answers.find { it.questionId == question.id }
            val isText = question.type == QuestionType.TEXT
            
            val selectedOptionTexts: List<String> = when (question.type) {
                QuestionType.TEXT -> listOf(answer?.answerText ?: "")
                else -> {
                    val selectedIds = answer?.optionIds ?: emptyList()
                    question.options.filter { it.id in selectedIds }.map { it.text }
                }
            }
            
            val correctOptionTexts: List<String> = when (question.type) {
                QuestionType.TEXT -> emptyList()
                else -> {
                    val correctIds = question.correct
                    question.options.filter { it.id in correctIds }.map { it.text }
                }
            }
            
            val isCorrect = when (question.type) {
                QuestionType.MCQ -> {
                    val selected = answer?.optionIds ?: emptyList()
                    selected.size == 1 && selected.firstOrNull() in question.correct
                }
                QuestionType.MSQ -> {
                    val selected = (answer?.optionIds ?: emptyList()).sorted()
                    val correct = question.correct.sorted()
                    selected == correct
                }
                QuestionType.TEXT -> false
            }
            
            val earned = if (!isText && isCorrect) question.weight else 0.0
            
            AnswerBreakdownItem(
                questionId = question.id,
                questionText = question.text,
                selectedOptionTexts = selectedOptionTexts,
                correctOptionTexts = correctOptionTexts,
                earnedMarks = earned,
                maxMarks = question.weight,
                isCorrect = isCorrect,
                isTextQuestion = isText
            )
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}