package com.cheatcrusher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJoin(join: LocalJoin): Long

    @Query("SELECT * FROM local_joins ORDER BY joinedAt DESC")
    suspend fun getAllJoins(): List<LocalJoin>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: LocalHistoryItem): Long

    @Query("SELECT * FROM local_history ORDER BY submittedAt DESC")
    suspend fun getAllHistory(): List<LocalHistoryItem>

    @Query("SELECT * FROM local_history WHERE quizId = :quizId AND rollNumber = :roll LIMIT 1")
    suspend fun getHistoryForQuizAndRoll(quizId: String, roll: String): LocalHistoryItem?

    // Cached quizzes
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedQuiz(cached: CachedQuiz)

    @Query("SELECT * FROM cached_quizzes WHERE quizCode = :code LIMIT 1")
    suspend fun getCachedQuizByCode(code: String): CachedQuiz?

    @Query("SELECT * FROM cached_quizzes WHERE quizId = :id LIMIT 1")
    suspend fun getCachedQuizById(id: String): CachedQuiz?

    @Query("SELECT * FROM cached_quizzes WHERE invalidated = 0 ORDER BY startsAtMillis ASC")
    suspend fun listCachedQuizzes(): List<CachedQuiz>

    @Query("UPDATE cached_quizzes SET invalidated = 1 WHERE quizId = :quizId")
    suspend fun invalidateCachedQuiz(quizId: String)

    @Query("DELETE FROM cached_quizzes WHERE invalidated = 1")
    suspend fun deleteInvalidatedQuizzes()

    @Query("DELETE FROM cached_quizzes WHERE quizId = :quizId")
    suspend fun deleteCachedQuiz(quizId: String)

    // Pending submissions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSubmission(pending: PendingSubmission): Long

    @Query("SELECT * FROM pending_submissions WHERE uploadStatus = :status ORDER BY id ASC")
    suspend fun listPendingSubmissions(status: String = "pending"): List<PendingSubmission>

    @Query("UPDATE pending_submissions SET uploadStatus = :status, lastError = :error WHERE id = :id")
    suspend fun updatePendingSubmissionStatus(id: Long, status: String, error: String?)

    @Query("DELETE FROM pending_submissions WHERE id = :id")
    suspend fun deletePendingSubmission(id: Long)

    @Query("SELECT * FROM pending_submissions WHERE responseId = :responseId LIMIT 1")
    suspend fun getPendingByResponseId(responseId: String): PendingSubmission?

    // Student profile
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStudentProfile(profile: StudentProfile)

    @Query("SELECT * FROM student_profile LIMIT 1")
    suspend fun getStudentProfile(): StudentProfile?
}