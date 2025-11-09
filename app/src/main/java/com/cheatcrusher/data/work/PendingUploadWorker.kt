package com.cheatcrusher.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.room.Room
import com.cheatcrusher.data.local.LocalDatabase
import com.cheatcrusher.data.local.LocalHistoryItem
import com.cheatcrusher.data.firebase.FirestoreRepository
import com.cheatcrusher.domain.Answer
import com.cheatcrusher.domain.GradeStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PendingUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = Room.databaseBuilder(
            applicationContext,
            LocalDatabase::class.java,
            "cheatcrusher_local.db"
        ).fallbackToDestructiveMigration().build()

        val dao = db.localDao()
        val firestore = FirebaseFirestore.getInstance()
        val repo = FirestoreRepository(firestore)
        val gson = Gson()

        return try {
            val targetId = inputData.getLong("pendingId", 0L)
            val pendingList = if (targetId > 0) {
                // If a specific ID was requested, fetch it regardless of status
                dao.getPendingById(targetId)?.let { listOf(it) } ?: emptyList()
            } else {
                // Otherwise process all eligible statuses
                dao.listPendingSubmissions("pending") + dao.listPendingSubmissions("uploading")
            }
            pendingList.forEach { item ->
                // Mark as uploading
                dao.updatePendingSubmissionStatus(item.id, "uploading", null)
                try {
                    // Fetch quiz to calculate score
                    val quizResult = repo.getQuizById(item.quizId)
                    if (quizResult.isFailure) {
                        dao.updatePendingSubmissionStatus(item.id, "failed", quizResult.exceptionOrNull()?.message)
                    } else {
                        val quiz = quizResult.getOrNull()!!
                        val answersType = object : TypeToken<List<Answer>>() {}.type
                        val answers: List<Answer> = gson.fromJson(item.answersJson, answersType)
                        val score = repo.calculateScore(quiz, answers)

                        // Build response and submit (create new doc)
                        val studentInfoType = object : TypeToken<Map<String, String>>() {}.type
                        val studentInfo: Map<String, String> = try { gson.fromJson(item.studentInfoJson, studentInfoType) } catch (_: Exception) { emptyMap() }

                        // Enforce required pre-form details before upload
                        val raw = com.cheatcrusher.util.JoinCodeVerifier.parse(quiz.rawJson)
                        val requiredOk = (raw?.preForm?.fields ?: emptyList()).all { f -> !f.required || !((studentInfo[f.key] ?: "").isBlank()) }
                        if (!requiredOk) {
                            dao.updatePendingSubmissionStatus(item.id, "failed", "Required details missing; please fill before upload")
                            return@forEach
                        }
                        val response = com.cheatcrusher.domain.Response(
                            quizId = item.quizId,
                            rollNumber = item.rollNumber,
                            deviceId = studentInfo["deviceId"] ?: "",
                            studentInfo = studentInfo,
                            answers = answers,
                            clientSubmittedAt = Timestamp.now(),
                            serverUploadedAt = null,
                            score = score,
                            gradeStatus = GradeStatus.AUTO,
                            appSwitchEvents = emptyList(),
                            disqualified = false,
                            flagged = item.flagged
                        )

                        val submitResult = repo.submitResponse(response)

                        submitResult.fold(
                            onSuccess = { _ ->
                                // Save local history if not already present, then delete pending
                                try {
                                    val existingHistory = dao.getHistoryForQuizAndRoll(item.quizId, item.rollNumber)
                                    if (existingHistory == null) {
                                        val historyItem = LocalHistoryItem(
                                            quizId = item.quizId,
                                            quizTitle = quiz.title,
                                            rollNumber = item.rollNumber,
                                            score = score,
                                            submittedAt = System.currentTimeMillis()
                                        )
                                        dao.insertHistory(historyItem)
                                    }
                                } catch (_: Exception) { /* ignore local history save errors */ }
                                // Delete pending record
                                dao.deletePendingSubmission(item.id)
                            },
                            onFailure = { e ->
                                dao.updatePendingSubmissionStatus(item.id, "failed", e.message)
                            }
                        )
                    }
                } catch (e: Exception) {
                    dao.updatePendingSubmissionStatus(item.id, "failed", e.message)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }
}
