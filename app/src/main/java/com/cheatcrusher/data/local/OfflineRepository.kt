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

    // New: cache raw JSON quiz by download code (offline-first, no network time requirement)
    suspend fun cacheRawQuizFromDownload(quizId: String, code: String, rawJson: String, title: String? = null): Boolean {
        val cached = CachedQuiz(
            quizId = quizId,
            quizCode = code.uppercase(),
            title = title ?: (try { com.cheatcrusher.util.JoinCodeVerifier.parse(rawJson)?.title ?: "" } catch (_: Exception) { "" }),
            metadataJson = rawJson,
            startsAtMillis = 0L,
            endsAtMillis = 0L,
            downloadedAtElapsedRealtime = android.os.SystemClock.elapsedRealtime(),
            requiresNetworkTime = false,
            invalidated = false
        )
        dao.upsertCachedQuiz(cached)
        // Also persist to local files so it survives app restarts reliably
        try {
            LocalStore.saveQuiz(context, quizId, code.uppercase(), cached.title, rawJson)
        } catch (_: Exception) { }
        return true
    }

    suspend fun getCachedQuizById(id: String): CachedQuiz? = dao.getCachedQuizById(id)

    suspend fun listCachedQuizzes(): List<CachedQuiz> = dao.listCachedQuizzes()

    suspend fun deleteCachedQuiz(quizId: String) {
        dao.deleteCachedQuiz(quizId)
        // Also delete from local files so it doesn't get rehydrated
        try { LocalStore.deleteQuiz(context, quizId) } catch (_: Exception) { }
    }

    fun parseCachedQuiz(cached: CachedQuiz): com.cheatcrusher.domain.Quiz? {
        // Try rawJson first
        try {
            val raw = com.cheatcrusher.util.JoinCodeVerifier.parse(cached.metadataJson)
            if (raw != null) {
                // Build domain Quiz from raw
                val durationSec = (raw.timerMinutes ?: 0) * 60
                val fields = raw.preForm?.fields?.map { f -> com.cheatcrusher.domain.FormField(id = f.key, label = f.label, type = "text", required = f.required) } ?: emptyList()
                val questions = raw.questions.map { rq ->
                    val opts = rq.options.mapIndexed { idx, text -> com.cheatcrusher.domain.QuestionOption(id = "opt_$idx", text = text) }
                    val correctIds = rq.correct.map { idx -> "opt_$idx" }
                    com.cheatcrusher.domain.Question(
                        id = rq.id,
                        type = when (rq.type.uppercase()) { "MSQ" -> com.cheatcrusher.domain.QuestionType.MSQ; "TEXT" -> com.cheatcrusher.domain.QuestionType.TEXT; else -> com.cheatcrusher.domain.QuestionType.MCQ },
                        text = rq.text,
                        options = opts,
                        correct = correctIds,
                        weight = (rq.weight ?: 1).toDouble()
                    )
                }
                return com.cheatcrusher.domain.Quiz(
                    id = raw.quizId ?: cached.quizId,
                    title = raw.title ?: cached.title,
                    code = cached.quizCode,
                    downloadCode = cached.quizCode,
                    creatorId = "",
                    durationSec = durationSec,
                    allowLateUploadSec = (raw.latencyMinutes ?: 0) * 60,
                    allowJoinAfterStart = false,
                    maxLateSec = 0,
                    onAppSwitchString = (raw.onAppSwitch ?: "flag"),
                    showAnswersWithMarks = false,
                    shuffleQuestions = false,
                    shuffleOptions = false,
                    autoDeleteAfterDays = raw.autoDeleteDays ?: 7,
                    preJoinFields = fields,
                    questions = questions,
                    editableUntilStart = true,
                    rawJson = cached.metadataJson
                )
            }
        } catch (_: Exception) { /* fall through */ }
        // Fallback to legacy domain-serialized quiz
        return try { gson.fromJson(cached.metadataJson, com.cheatcrusher.domain.Quiz::class.java) } catch (_: Exception) { null }
    }

    suspend fun rehydrateCachedFromFiles() {
        try {
            val saved = LocalStore.listSaved(context)
            saved.forEach { (quizId, code, title) ->
                val raw = LocalStore.readQuiz(context, quizId) ?: return@forEach
                val existing = dao.getCachedQuizById(quizId)
                if (existing == null) {
                    val cached = CachedQuiz(
                        quizId = quizId,
                        quizCode = code,
                        title = title,
                        metadataJson = raw,
                        startsAtMillis = 0L,
                        endsAtMillis = 0L,
                        downloadedAtElapsedRealtime = android.os.SystemClock.elapsedRealtime(),
                        requiresNetworkTime = false,
                        invalidated = false
                    )
                    dao.upsertCachedQuiz(cached)
                }
            }
        } catch (_: Exception) { }
    }

    suspend fun updatePendingStudentInfo(id: Long, studentInfoJson: String) {
        db.localDao().updatePendingStudentInfo(id, studentInfoJson)
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

    // Include all statuses so UI can reflect uploading/failed items
    suspend fun listPendingSubmissionsAny(): List<PendingSubmission> {
        return dao.listPendingSubmissions("pending") +
                dao.listPendingSubmissions("uploading") +
                dao.listPendingSubmissions("failed")
    }

    suspend fun markPendingUploading(id: Long) {
        dao.updatePendingSubmissionStatus(id, "uploading", null)
    }

    suspend fun listPendingSubmissions(): List<PendingSubmission> = dao.listPendingSubmissions()

    suspend fun getPendingById(id: Long): PendingSubmission? = dao.getPendingById(id)

    suspend fun deletePendingSubmission(id: Long) {
        dao.deletePendingSubmission(id)
    }

    fun saveAnswerCode(quizId: String, code: String) {
        LocalStore.saveAnswerCode(context, quizId, code)
    }

    fun readAnswerCode(quizId: String): String? {
        return LocalStore.readAnswerCode(context, quizId)
    }
}
