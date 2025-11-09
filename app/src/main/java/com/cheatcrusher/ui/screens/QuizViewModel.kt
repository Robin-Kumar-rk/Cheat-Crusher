package com.cheatcrusher.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheatcrusher.data.firebase.FirestoreRepository
import com.cheatcrusher.domain.*
import com.cheatcrusher.util.DeviceUtils
import com.cheatcrusher.data.local.OfflineRepository
import com.cheatcrusher.data.local.LocalDataRepository
import com.cheatcrusher.data.work.PendingUploadWorker
import com.google.gson.Gson
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Constraints
import androidx.work.NetworkType
import com.cheatcrusher.util.TimeIntegrity
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizUiState(
    val quiz: Quiz? = null,
    val shuffledQuestions: List<Question> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val answers: Map<String, Answer> = emptyMap(),
    val timeRemainingSeconds: Int = 0,
    val isSubmitted: Boolean = false,
    val submittedResponseId: String? = null,
    val isPendingUpload: Boolean = false,
    val isFlagged: Boolean = false,
    val isDisqualified: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository,
    private val deviceUtils: DeviceUtils,
    private val offlineRepository: OfflineRepository,
    private val localRepository: LocalDataRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()
    
    private var timerJob: Job? = null
    private var currentResponseId: String? = null
    private var currentRollNumber: String? = null
    private var submissionStarted: Boolean = false
    
    fun loadQuiz(quizId: String, rollNumber: String, studentInfo: Map<String, String> = emptyMap()) {
        val normalizedRoll = rollNumber.trim()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            currentRollNumber = normalizedRoll
            
            try {
                // Offline-first: load cached quiz and parse
                val cached = offlineRepository.getCachedQuizById(quizId)
                val quiz = cached?.let { offlineRepository.parseCachedQuiz(it) }
                if (quiz == null) {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = "Quiz not downloaded. Enter download code first.")
                    return@launch
                }

                val shuffledQuestions = firestoreRepository.shuffleQuestionsForJoin(quiz, normalizedRoll)
                    .map { question -> firestoreRepository.shuffleOptionsForJoin(question, quiz, normalizedRoll) }
                val remaining = (quiz.durationSec).coerceAtLeast(0)
                _uiState.value = _uiState.value.copy(
                    quiz = quiz,
                    shuffledQuestions = shuffledQuestions,
                    timeRemainingSeconds = remaining,
                    isLoading = false
                )
                startTimer()
                currentResponseId = java.util.UUID.randomUUID().toString()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load quiz"
                )
            }
        }
    }
    
    private fun createInitialResponse(quiz: Quiz, rollNumber: String, studentInfo: Map<String, String> = emptyMap()) {
        val normalizedRoll = rollNumber.trim()
        viewModelScope.launch {
            try {
                val deviceId = try { deviceUtils.getDeviceId() } catch (e: Exception) { "" }
                val response = Response(
                    quizId = quiz.id,
                    rollNumber = normalizedRoll,
                    deviceId = deviceId,
                    studentInfo = studentInfo,
                    clientSubmittedAt = null,
                    serverUploadedAt = null
                )
                
                firestoreRepository.submitResponse(response).fold(
                    onSuccess = { responseId ->
                        currentResponseId = responseId
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            error = "Failed to initialize quiz: ${exception.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to initialize quiz: ${e.message}"
                )
            }
        }
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.timeRemainingSeconds > 0 && !_uiState.value.isSubmitted) {
                delay(1000)
                val currentTime = _uiState.value.timeRemainingSeconds - 1
                _uiState.value = _uiState.value.copy(timeRemainingSeconds = currentTime)
                
                if (currentTime <= 0) {
                    // Time's up - auto submit
                    submitQuiz()
                    break
                }
            }
        }
    }
    
    fun updateAnswer(questionId: String, optionIds: List<String>) {
        val currentAnswers = _uiState.value.answers.toMutableMap()
        currentAnswers[questionId] = Answer(
            questionId = questionId,
            optionIds = optionIds
        )
        _uiState.value = _uiState.value.copy(answers = currentAnswers)
    }
    
    fun updateTextAnswer(questionId: String, text: String) {
        val currentAnswers = _uiState.value.answers.toMutableMap()
        currentAnswers[questionId] = Answer(
            questionId = questionId,
            answerText = text
        )
        _uiState.value = _uiState.value.copy(answers = currentAnswers)
    }
    
    fun nextQuestion() {
        val currentIndex = _uiState.value.currentQuestionIndex
        val maxIndex = _uiState.value.shuffledQuestions.size - 1
        if (currentIndex < maxIndex) {
            _uiState.value = _uiState.value.copy(currentQuestionIndex = currentIndex + 1)
        }
    }
    
    fun previousQuestion() {
        val currentIndex = _uiState.value.currentQuestionIndex
        if (currentIndex > 0) {
            _uiState.value = _uiState.value.copy(currentQuestionIndex = currentIndex - 1)
        }
    }
    
    fun submitQuiz() {
        viewModelScope.launch {
            try {
                // Guard against rapid multiple taps
                if (submissionStarted || _uiState.value.isSubmitted) {
                    return@launch
                }
                submissionStarted = true
                // Block submission if disqualified
                if (_uiState.value.isDisqualified) {
                    _uiState.value = _uiState.value.copy(
                        error = "Submission blocked: You have been disqualified."
                    )
                    return@launch
                }

                val quiz = _uiState.value.quiz ?: return@launch
                val localResponseId = currentResponseId ?: java.util.UUID.randomUUID().toString()
                
                // Calculate score
                val answers = _uiState.value.answers.values.toList()
                val score = firestoreRepository.calculateScore(quiz, answers)
                
                // Attempt to upload final response (offline-first: create on submit)
                val deviceId = try { deviceUtils.getDeviceId() } catch (e: Exception) { "" }
                val response = Response(
                    id = localResponseId,
                    quizId = quiz.id,
                    rollNumber = currentRollNumber ?: "",
                    deviceId = deviceId,
                    studentInfo = emptyMap(),
                    answers = answers,
                    clientSubmittedAt = Timestamp.now(),
                    serverUploadedAt = null,
                    score = score,
                    gradeStatus = GradeStatus.AUTO,
                    appSwitchEvents = emptyList(),
                    disqualified = _uiState.value.isDisqualified,
                    flagged = _uiState.value.isFlagged
                )
                firestoreRepository.submitResponse(response).fold(
                    onSuccess = { responseId ->
                        _uiState.value = _uiState.value.copy(
                            isSubmitted = true,
                            submittedResponseId = responseId,
                            isPendingUpload = false
                        )

                        // Save local history entry for immediate submission
                        try {
                            val roll = currentRollNumber ?: ""
                            localRepository.saveHistory(
                                quizId = quiz.id,
                                quizTitle = quiz.title,
                                rollNumber = roll,
                                score = score,
                                submittedAtMillis = System.currentTimeMillis()
                            )
                        } catch (_: Exception) { /* swallow local save errors */ }
                        
                        // Stop timer
                        timerJob?.cancel()
                    },
                    onFailure = { exception ->
                        // Queue offline submission
                        try {
                            val gson = Gson()
                            val answersJson = gson.toJson(answers)
                            val studentInfoJson = "{}" // not needed for update
                            val roll = currentRollNumber ?: ""
                            offlineRepository.savePendingSubmission(
                                responseId = localResponseId,
                                quizId = quiz.id,
                                rollNumber = roll,
                                studentInfoJson = studentInfoJson,
                                answersJson = answersJson,
                                flagged = _uiState.value.isFlagged
                            )

                            // Enqueue worker with network constraint
                            val constraints = Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                            val workRequest = OneTimeWorkRequestBuilder<PendingUploadWorker>()
                                .setConstraints(constraints)
                                .build()
                            WorkManager.getInstance(context).enqueue(workRequest)

                            _uiState.value = _uiState.value.copy(
                                isSubmitted = true,
                                submittedResponseId = localResponseId,
                                error = null,
                                isPendingUpload = true
                            )
                            timerJob?.cancel()
                        } catch (e: Exception) {
                            _uiState.value = _uiState.value.copy(
                                error = "Failed to submit quiz: ${exception.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                // Also queue offline on exception
                try {
                    val quiz = _uiState.value.quiz ?: return@launch
                    val responseId = currentResponseId ?: return@launch
                    val answers = _uiState.value.answers.values.toList()

                    val gson = Gson()
                    val answersJson = gson.toJson(answers)
                    val studentInfoJson = "{}"
                    val roll = currentRollNumber ?: ""
                    offlineRepository.savePendingSubmission(
                        responseId = responseId,
                        quizId = quiz.id,
                        rollNumber = roll,
                        studentInfoJson = studentInfoJson,
                        answersJson = answersJson,
                        flagged = _uiState.value.isFlagged
                    )

                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    val workRequest = OneTimeWorkRequestBuilder<PendingUploadWorker>()
                        .setConstraints(constraints)
                        .build()
                    WorkManager.getInstance(context).enqueue(workRequest)

                    _uiState.value = _uiState.value.copy(
                        isSubmitted = true,
                        submittedResponseId = responseId,
                        error = null,
                        isPendingUpload = true
                    )
                    timerJob?.cancel()
                } catch (inner: Exception) {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to submit quiz: ${e.message}"
                    )
                }
            }
        }
    }
    
    fun onAppSwitchDetected() {
        // Prevent multiple app switch detections if already disqualified or submitted
        if (_uiState.value.isDisqualified || _uiState.value.isSubmitted) {
            Log.d("QuizViewModel", "App switch ignored - already disqualified or submitted")
            return
        }
        
        viewModelScope.launch {
            val quiz = _uiState.value.quiz ?: run {
                Log.e("QuizViewModel", "App switch detected but quiz is null!")
                return@launch
            }
            val responseId = currentResponseId ?: run {
                Log.e("QuizViewModel", "App switch detected but responseId is null!")
                return@launch
            }
            
            // Debug logging
            Log.d("QuizViewModel", "=== APP SWITCH DETECTED ===")
            Log.d("QuizViewModel", "Quiz ID: ${quiz.id}")
            Log.d("QuizViewModel", "Quiz Title: ${quiz.title}")
            Log.d("QuizViewModel", "Response ID: $responseId")
            Log.d("QuizViewModel", "Quiz onAppSwitch policy: ${quiz.onAppSwitch}")
            Log.d("QuizViewModel", "Current answers count: ${_uiState.value.answers.size}")
            Log.d("QuizViewModel", "Current question index: ${_uiState.value.currentQuestionIndex}")
            Log.d("QuizViewModel", "Time remaining: ${_uiState.value.timeRemainingSeconds}")
            
            // Log app switch event
            val event = AppSwitchEvent(
                timestamp = Timestamp.now(),
                state = "app_switched"
            )
            
            firestoreRepository.addAppSwitchEvent(responseId, event)
            
            // Apply penalty based on quiz settings
            when (quiz.onAppSwitch) {
                AppSwitchPolicy.FLAG -> {
                    Log.d("QuizViewModel", "Applying FLAG penalty")
                    _uiState.value = _uiState.value.copy(isFlagged = true)
                    firestoreRepository.updateResponseStatus(responseId, flagged = true)
                }
                AppSwitchPolicy.RESET -> {
                    Log.d("QuizViewModel", "Applying RESET penalty - BEFORE reset:")
                    Log.d("QuizViewModel", "  - Answers before: ${_uiState.value.answers}")
                    Log.d("QuizViewModel", "  - Question index before: ${_uiState.value.currentQuestionIndex}")
                    Log.d("QuizViewModel", "  - Time before: ${_uiState.value.timeRemainingSeconds}")
                    
                    // Clear all answers and restart
                    val now = Timestamp.now()
                    val remaining = ((quiz.endsAt.seconds - now.seconds).coerceAtLeast(0)).toInt()
                    _uiState.value = _uiState.value.copy(
                        answers = emptyMap(),
                        currentQuestionIndex = 0,
                        timeRemainingSeconds = remaining,
                        isFlagged = true // Also flag when resetting
                    )
                    
                    Log.d("QuizViewModel", "Applying RESET penalty - AFTER reset:")
                    Log.d("QuizViewModel", "  - Answers after: ${_uiState.value.answers}")
                    Log.d("QuizViewModel", "  - Question index after: ${_uiState.value.currentQuestionIndex}")
                    Log.d("QuizViewModel", "  - Time after: ${_uiState.value.timeRemainingSeconds}")
                    Log.d("QuizViewModel", "  - Is flagged: ${_uiState.value.isFlagged}")
                    
                    firestoreRepository.updateResponseStatus(responseId, flagged = true)
                    startTimer()
                    Log.d("QuizViewModel", "Timer restarted after RESET")
                }
                AppSwitchPolicy.DISQUALIFY -> {
                    Log.d("QuizViewModel", "Applying DISQUALIFY penalty")
                    firestoreRepository.updateResponseStatus(responseId, disqualified = true)
                    _uiState.value = _uiState.value.copy(
                        isDisqualified = true,
                        error = "You have been disqualified for switching apps. The quiz has been terminated."
                    )
                    timerJob?.cancel()
                }
            }
            Log.d("QuizViewModel", "=== APP SWITCH HANDLING COMPLETE ===")
        }
    }
    
    fun clearError() {
        // Don't clear error if user is disqualified - they must exit via dialog
        if (!_uiState.value.isDisqualified) {
            _uiState.value = _uiState.value.copy(error = null)
        }
    }
    
    fun clearDisqualificationError() {
        // Special function to clear disqualification error when exiting
        _uiState.value = _uiState.value.copy(
            error = null,
            isDisqualified = false
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
