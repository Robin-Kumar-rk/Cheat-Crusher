package com.cheatcrusher.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatcrusher.data.local.OfflineRepository
import com.cheatcrusher.domain.Answer
import com.cheatcrusher.domain.QuestionType
import com.cheatcrusher.domain.Quiz
import com.cheatcrusher.domain.Response
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnswersUiState(
    val quiz: Quiz? = null,
    val breakdown: List<AnswerBreakdownItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val savedAnsCode: String? = null,
    val hasStudentAnswers: Boolean = false
)

@HiltViewModel
class AnswersViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnswersUiState())
    val uiState: StateFlow<AnswersUiState> = _uiState.asStateFlow()

    fun load(quizId: String, pendingId: Long?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val cached = offlineRepository.getCachedQuizById(quizId)
                if (cached == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Quiz not found locally")
                    return@launch
                }
                val quiz = offlineRepository.parseCachedQuiz(cached)
                if (quiz == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to parse quiz")
                    return@launch
                }
                val savedAns = offlineRepository.readAnswerCode(quiz.id)

                var answers: List<Answer> = emptyList()
                var hasStudent = false
                if (pendingId != null && pendingId > 0) {
                    val pending = offlineRepository.getPendingById(pendingId)
                    if (pending != null && pending.quizId == quiz.id) {
                        try {
                            val type = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, Answer::class.java).type
                            answers = com.google.gson.Gson().fromJson(pending.answersJson, type)
                            hasStudent = true
                        } catch (_: Exception) { /* ignore */ }
                    }
                }
                val response = Response(
                    quizId = quiz.id,
                    rollNumber = "",
                    deviceId = "",
                    studentInfo = emptyMap(),
                    answers = answers,
                    clientSubmittedAt = null,
                    serverUploadedAt = null,
                    score = null,
                    gradeStatus = com.cheatcrusher.domain.GradeStatus.PENDING,
                    appSwitchEvents = emptyList(),
                    disqualified = false,
                    flagged = false
                )

                val breakdown = computeBreakdown(quiz, response)
                _uiState.value = AnswersUiState(
                    quiz = quiz,
                    breakdown = breakdown,
                    isLoading = false,
                    savedAnsCode = savedAns,
                    hasStudentAnswers = hasStudent
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

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

    fun saveAnsCode(quizId: String, code: String) {
        viewModelScope.launch {
            try {
                offlineRepository.saveAnswerCode(quizId, code)
                _uiState.value = _uiState.value.copy(savedAnsCode = code)
            } catch (_: Exception) { /* ignore */ }
        }
    }
}

