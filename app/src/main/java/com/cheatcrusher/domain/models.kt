package com.cheatcrusher.domain

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import java.util.Date

data class Quiz(
    val id: String = "",
    val title: String = "",
    val code: String = "",
    val creatorId: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val startsAt: Timestamp = Timestamp.now(),
    val endsAt: Timestamp = Timestamp.now(),
    val durationSec: Int = 0,
    val allowLateUploadSec: Int = 0,
    val allowJoinAfterStart: Boolean = false,
    val maxLateSec: Int = 0,
    @PropertyName("onAppSwitch")
    val onAppSwitchString: String = "flag",
    val showResultsAt: Timestamp = Timestamp.now(),
    val showAnswersWithMarks: Boolean = false,
    val shuffleQuestions: Boolean = false,
    val shuffleOptions: Boolean = false,
    val autoDeleteAfterDays: Int = 7,
    val preJoinFields: List<FormField> = emptyList(),
    val questions: List<Question> = emptyList(),
    val editableUntilStart: Boolean = true
) {
    val onAppSwitch: AppSwitchPolicy
        get() = AppSwitchPolicy.fromString(onAppSwitchString)
}

// Roll number constraints removed: any student may join any quiz

enum class AppSwitchPolicy {
    FLAG, RESET, DISQUALIFY;
    
    companion object {
        fun fromString(value: String): AppSwitchPolicy {
            return when (value.lowercase()) {
                "flag" -> FLAG
                "reset" -> RESET
                "disqualify" -> DISQUALIFY
                else -> FLAG // Default fallback
            }
        }
    }
}

data class Question(
    val id: String = "",
    val type: QuestionType = QuestionType.MCQ,
    val text: String = "",
    val options: List<QuestionOption> = emptyList(),
    val correct: List<String> = emptyList(), // Option IDs for correct answers
    val weight: Double = 1.0
)

enum class QuestionType {
    MCQ, // Multiple Choice Question (single answer)
    MSQ, // Multiple Select Question (multiple answers)
    TEXT // Short text answer
}

data class QuestionOption(
    val id: String = "",
    val text: String = ""
)

data class Response(
    val id: String = "",
    val quizId: String = "",
    val rollNumber: String = "",
    val deviceId: String = "",
    val studentInfo: Map<String, String> = emptyMap(),
    val answers: List<Answer> = emptyList(),
    val clientSubmittedAt: Timestamp? = null,
    val serverUploadedAt: Timestamp? = null,
    val score: Double? = null,
    val gradeStatus: GradeStatus = GradeStatus.AUTO,
    val appSwitchEvents: List<AppSwitchEvent> = emptyList(),
    val disqualified: Boolean = false,
    val flagged: Boolean = false
)

data class FormField(
    val id: String = "",
    val label: String = "",
    val type: String = "text", // text, number, password
    val required: Boolean = false
)

enum class GradeStatus {
    AUTO, PENDING, GRADED
}

data class Answer(
    val questionId: String = "",
    val answerText: String? = null, // For text questions
    val optionIds: List<String> = emptyList() // For MCQ/MSQ questions
)

data class AppSwitchEvent(
    val timestamp: Timestamp = Timestamp.now(),
    val state: String = "" // "paused", "stopped", "resumed"
)

// Network and device utilities
data class NetworkState(
    val isConnected: Boolean = false,
    val isWifi: Boolean = false
)
