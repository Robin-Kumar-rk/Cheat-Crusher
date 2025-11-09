package com.cheatcrusher.data.local

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataRepository @Inject constructor(
    private val db: LocalDatabase
) {
    private val dao = db.localDao()

    suspend fun saveHistory(
        quizId: String,
        quizTitle: String,
        rollNumber: String,
        score: Double?,
        submittedAtMillis: Long
    ) {
        dao.insertHistory(
            LocalHistoryItem(
                quizId = quizId,
                quizTitle = quizTitle,
                rollNumber = rollNumber,
                score = score,
                submittedAt = submittedAtMillis
            )
        )
    }

    suspend fun listHistory(): List<LocalHistoryItem> = dao.getAllHistory()

    suspend fun getStudentProfile(): StudentProfile? = dao.getStudentProfile()
}