package com.cheatcrusher.data.local

import android.content.Context
import com.cheatcrusher.domain.Quiz
import com.cheatcrusher.util.TimeIntegrity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRepository @Inject constructor(
    private val db: LocalDatabase,
    @ApplicationContext private val context: Context
) {
    private val dao = db.localDao()
    private val gson = com.google.gson.Gson()

    suspend fun cacheQuiz(quiz: Quiz, code: String) : Boolean {
        // Require automatic network time to prevent clock tampering
        if (!TimeIntegrity.isAutoTimeEnabled(context)) {
            // Mark any cached instance as invalid to force re-download later
            dao.invalidateCachedQuiz(quiz.id)
            return false
        }

        val startsAtMillis = quiz.startsAt.toDate().time
        val endsAtMillis = quiz.endsAt.toDate().time
        val snapshot = TimeIntegrity.createSnapshot(startsAtMillis, endsAtMillis)

        val cached = CachedQuiz(
            quizId = quiz.id,
            quizCode = code.uppercase(),
            title = quiz.title,
            metadataJson = gson.toJson(quiz),
            startsAtMillis = startsAtMillis,
            endsAtMillis = endsAtMillis,
            downloadedAtElapsedRealtime = snapshot.downloadedAtElapsedRealtime,
            requiresNetworkTime = true,
            invalidated = false
        )
        dao.upsertCachedQuiz(cached)
        return true
    }

    suspend fun getCachedQuizById(id: String): CachedQuiz? = dao.getCachedQuizById(id)

    suspend fun listCachedQuizzes(): List<CachedQuiz> = dao.listCachedQuizzes()

    suspend fun deleteCachedQuiz(quizId: String) {
        dao.deleteCachedQuiz(quizId)
    }

    fun parseCachedQuiz(cached: CachedQuiz): com.cheatcrusher.domain.Quiz? {
        return try { gson.fromJson(cached.metadataJson, com.cheatcrusher.domain.Quiz::class.java) } catch (_: Exception) { null }
    }

    suspend fun savePendingSubmission(
        responseId: String,
        quizId: String,
        rollNumber: String,
        studentInfoJson: String,
        answersJson: String,
        flagged: Boolean
    ): Long {
        // Avoid duplicate pending submissions for the same responseId
        val existing = dao.getPendingByResponseId(responseId)
        if (existing != null && existing.uploadStatus in listOf("pending", "uploading")) {
            return existing.id
        }
        val pending = PendingSubmission(
            responseId = responseId,
            quizId = quizId,
            rollNumber = rollNumber,
            studentInfoJson = studentInfoJson,
            answersJson = answersJson,
            createdAtElapsedRealtime = android.os.SystemClock.elapsedRealtime(),
            flagged = flagged,
            uploadStatus = "pending"
        )
        return dao.insertPendingSubmission(pending)
    }

    suspend fun listPendingSubmissions(): List<PendingSubmission> = dao.listPendingSubmissions()
}