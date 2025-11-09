package com.cheatcrusher.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_joins")
data class LocalJoin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: String,
    val quizCode: String,
    val rollNumber: String,
    val joinedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_history")
data class LocalHistoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val quizId: String,
    val quizTitle: String,
    val rollNumber: String,
    val score: Double?,
    val submittedAt: Long
)

@Entity(tableName = "cached_quizzes")
data class CachedQuiz(
    @PrimaryKey val quizId: String,
    val quizCode: String,
    val title: String,
    val metadataJson: String,
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val downloadedAtElapsedRealtime: Long,
    val requiresNetworkTime: Boolean,
    val invalidated: Boolean = false
)

@Entity(tableName = "pending_submissions")
data class PendingSubmission(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val responseId: String,
    val quizId: String,
    val rollNumber: String,
    val studentInfoJson: String,
    val answersJson: String,
    val createdAtElapsedRealtime: Long,
    val flagged: Boolean = false,
    val uploadStatus: String = "pending", // pending | uploading | uploaded | failed
    val lastError: String? = null
)

@Entity(tableName = "student_profile")
data class StudentProfile(
    @PrimaryKey val rollNumber: String,
    val name: String,
    val email: String,
    val section: String,
    val createdAt: Long = System.currentTimeMillis()
)