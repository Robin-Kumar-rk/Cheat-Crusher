package com.cheatcrusher.data.firebase

import android.util.Log
import com.cheatcrusher.domain.*
import com.cheatcrusher.util.DeviceUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    
    /**
     * Get quiz by join code
     */
    suspend fun getQuizByCode(code: String): Result<Quiz> {
        return try {
            var querySnapshot = firestore.collection("quizzes")
                .whereEqualTo("downloadCode", code.uppercase())
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                // Fallback to legacy field
                querySnapshot = firestore.collection("quizzes")
                    .whereEqualTo("code", code.uppercase())
                    .limit(1)
                    .get()
                    .await()
            }
            
            if (querySnapshot.isEmpty) {
                Result.failure(Exception("Quiz not found with code: $code"))
            } else {
                val document = querySnapshot.documents.first()
                val quiz = document.toObject(Quiz::class.java)?.copy(id = document.id)
                    ?: return Result.failure(Exception("Failed to parse quiz data"))
                Result.success(quiz)
            }
        } catch (e: Exception) {
            // Map Firestore permission during inactive window to a friendly message
            val msg = e.message ?: "Unknown error"
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                Result.failure(Exception("Quiz is not available for joining at this time"))
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Get raw JSON and doc id by download code
     */
    suspend fun getQuizRawByDownloadCode(code: String): Result<Pair<String, String>> {
        return try {
            val querySnapshot = firestore.collection("quizzes")
                .whereEqualTo("downloadCode", code.uppercase())
                .limit(1)
                .get()
                .await()
            if (querySnapshot.isEmpty) {
                Result.failure(Exception("Quiz not found with download code: $code"))
            } else {
                val doc = querySnapshot.documents.first()
                val raw = (doc.get("rawJson") as? String) ?: ""
                Result.success(Pair(doc.id, raw))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get quiz by ID
     */
    suspend fun getQuizById(quizId: String): Result<Quiz> {
        return try {
            val document = firestore.collection("quizzes")
                .document(quizId)
                .get()
                .await()
            
            if (document.exists()) {
                val quiz = document.toObject(Quiz::class.java)?.copy(id = document.id)
                    ?: return Result.failure(Exception("Failed to parse quiz data"))
                
                // Debug logging for quiz loading
                Log.d("FirestoreRepository", "=== QUIZ LOADED FROM FIRESTORE ===")
                Log.d("FirestoreRepository", "Quiz ID: ${quiz.id}")
                Log.d("FirestoreRepository", "Quiz Title: ${quiz.title}")
                Log.d("FirestoreRepository", "Quiz Code: ${quiz.code}")
                Log.d("FirestoreRepository", "Download Code: ${quiz.downloadCode}")
                Log.d("FirestoreRepository", "Has rawJson: ${quiz.rawJson != null}")
                Log.d("FirestoreRepository", "Raw document data: ${document.data}")
                Log.d("FirestoreRepository", "Quiz onAppSwitch policy: ${quiz.onAppSwitch}")
                Log.d("FirestoreRepository", "=== END QUIZ LOADING DEBUG ===")
                
                Result.success(quiz)
            } else {
                Result.failure(Exception("Quiz not found"))
            }
        } catch (e: Exception) {
            // Map Firestore permission errors to a friendlier message
            val msg = e.message ?: "Unknown error"
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                Result.failure(Exception("Quiz is not available for joining at this time"))
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Submit quiz response
     */
    suspend fun submitResponse(response: Response): Result<String> {
        return try {
            val responseWithServerTime = response.copy(
                serverUploadedAt = Timestamp.now()
            )
            
            val documentRef = firestore.collection("responses")
                .add(responseWithServerTime)
                .await()
            
            Result.success(documentRef.id)
        } catch (e: Exception) {
            // Align error message with rules: creation fails if quiz isn't active
            // No roll constraints: only active window matters.
            val msg = e.message ?: "Unknown error"
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                Result.failure(Exception("Quiz is not active right now"))
            } else {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Get response by ID
     */
    suspend fun getResponseById(responseId: String): Result<Response> {
        return try {
            val document = firestore.collection("responses")
                .document(responseId)
                .get()
                .await()
            
            if (document.exists()) {
                val response = document.toObject(Response::class.java)?.copy(id = document.id)
                    ?: return Result.failure(Exception("Failed to parse response data"))
                Result.success(response)
            } else {
                Result.failure(Exception("Response not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a single response by quizId and rollNumber
     */
    suspend fun getResponseByQuizAndRoll(quizId: String, rollNumber: String): Result<Response> {
        return try {
            val querySnapshot = firestore.collection("responses")
                .whereEqualTo("quizId", quizId)
                .whereEqualTo("rollNumber", rollNumber.trim())
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                Result.failure(Exception("No result found for this roll number"))
            } else {
                val document = querySnapshot.documents.first()
                val response = document.toObject(Response::class.java)?.copy(id = document.id)
                    ?: return Result.failure(Exception("Failed to parse response data"))
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a single response by quizId and deviceId
     */
    suspend fun getResponseByQuizAndDeviceId(quizId: String, deviceId: String): Result<Response> {
        return try {
            val querySnapshot = firestore.collection("responses")
                .whereEqualTo("quizId", quizId)
                .whereEqualTo("deviceId", deviceId.trim())
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                Result.failure(Exception("No result found for this device"))
            } else {
                val document = querySnapshot.documents.first()
                val response = document.toObject(Response::class.java)?.copy(id = document.id)
                    ?: return Result.failure(Exception("Failed to parse response data"))
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    


    /**
     * Update response with app switch event
     */
    suspend fun addAppSwitchEvent(responseId: String, event: AppSwitchEvent): Result<Unit> {
        return try {
            val responseRef = firestore.collection("responses").document(responseId)
            val document = responseRef.get().await()
            
            if (document.exists()) {
                val response = document.toObject(Response::class.java)
                    ?: return Result.failure(Exception("Failed to parse response data"))
                
                val updatedEvents = response.appSwitchEvents.toMutableList()
                updatedEvents.add(event)
                
                responseRef.update("appSwitchEvents", updatedEvents).await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Response not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update response status (flagged, disqualified)
     */
    suspend fun updateResponseStatus(
        responseId: String,
        flagged: Boolean? = null,
        disqualified: Boolean? = null
    ): Result<Unit> {
        return try {
            val responseRef = firestore.collection("responses").document(responseId)
            val updates = mutableMapOf<String, Any>()
            
            flagged?.let { updates["flagged"] = it }
            disqualified?.let { updates["disqualified"] = it }
            
            if (updates.isNotEmpty()) {
                responseRef.update(updates).await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update response with final submission data
     */
    suspend fun updateResponse(
        responseId: String,
        answers: List<Answer>,
        clientSubmittedAt: Timestamp,
        score: Double,
        gradeStatus: GradeStatus,
        flagged: Boolean
    ): Result<Unit> {
        return try {
            val responseRef = firestore.collection("responses").document(responseId)
            val updates = mapOf(
                "answers" to answers,
                "clientSubmittedAt" to clientSubmittedAt,
                "score" to score,
                "gradeStatus" to gradeStatus.name,
                "flagged" to flagged
            )
            
            responseRef.update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    

    
    /**
     * Check if quiz can be joined
     */
    fun canJoinQuiz(quiz: Quiz): Boolean {
        val now = Timestamp.now()
        // Align app join window with Firestore rules: only allow joining
        // when the quiz is active (between startsAt and endsAt).
        // This prevents attempting a write that rules will deny.
        return now >= quiz.startsAt && now <= quiz.endsAt
    }
    
    // Roll number validation removed: anyone can join any quiz
    
    /**
     * Calculate quiz score based on answers
     */
    fun calculateScore(quiz: Quiz, answers: List<Answer>): Double {
        var totalScore = 0.0
        var totalWeight = 0.0
        
        quiz.questions.forEach { question ->
            totalWeight += question.weight
            
            val answer = answers.find { it.questionId == question.id }
            if (answer != null) {
                val isCorrect = when (question.type) {
                    QuestionType.MCQ -> {
                        answer.optionIds.size == 1 && 
                        answer.optionIds.first() in question.correct
                    }
                    QuestionType.MSQ -> {
                        answer.optionIds.sorted() == question.correct.sorted()
                    }
                    QuestionType.TEXT -> {
                        // Text answers need manual grading
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
    
    /**
     * Shuffle questions for a specific user
     */
    fun shuffleQuestionsForJoin(quiz: Quiz, rollNumber: String): List<Question> {
        if (!quiz.shuffleQuestions) return quiz.questions
        val seed = "${rollNumber}_${quiz.id}".hashCode().toLong()
        return quiz.questions.shuffled(kotlin.random.Random(seed))
    }
    
    /**
     * Shuffle options for a specific user
     */
    fun shuffleOptionsForJoin(question: Question, quiz: Quiz, rollNumber: String): Question {
        if (!quiz.shuffleOptions || question.options.isEmpty()) return question
        val seed = "${rollNumber}_${quiz.id}_${question.id}".hashCode().toLong()
        val shuffledOptions = question.options.shuffled(kotlin.random.Random(seed))
        return question.copy(options = shuffledOptions)
    }
}
