package com.cheatcrusher.util

import com.google.gson.Gson
import java.time.Instant
import java.time.Duration

data class RawQuiz(
    val quizId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val downloadCode: String? = null,
    val latencyMinutes: Int? = null,
    val timerMinutes: Int? = null,
    val preForm: PreForm? = null,
    val questions: List<RawQuestion> = emptyList(),
    val allowedJoinCodes: List<AllowedJoinCode> = emptyList(),
    val answerViewPassword: String? = null,
    val autoDeleteDays: Int? = null
)

data class PreForm(
    val fields: List<PreFormField> = emptyList()
)

data class PreFormField(
    val key: String,
    val label: String,
    val required: Boolean = false
)

data class RawQuestion(
    val id: String,
    val type: String,
    val text: String,
    val options: List<String> = emptyList(),
    val correct: List<Int> = emptyList(),
    val weight: Int? = null
)

data class AllowedJoinCode(
    val unlockPassword: String,
    val allowedStartTimes: List<String> = emptyList()
)

object JoinCodeVerifier {
    private val gson = Gson()

    data class Result(val ok: Boolean, val error: String? = null)

    fun parse(rawJson: String?): RawQuiz? {
        return try {
            if (rawJson.isNullOrBlank()) null else gson.fromJson(rawJson, RawQuiz::class.java)
        } catch (_: Exception) { null }
    }

    fun verify(raw: RawQuiz?, joinCode: String?): Result {
        if (raw == null) return Result(false, "Missing quiz config")
        val codes = raw.allowedJoinCodes
        if (codes.isEmpty()) return Result(false, "Join codes not configured for this quiz")
        val parts = (joinCode ?: "").split("|")
        if (parts.size != 2) return Result(false, "Invalid join code format")
        val pwd = parts[0].trim()
        val startIso = parts[1].trim()
        val entry = codes.firstOrNull { it.unlockPassword == pwd }
            ?: return Result(false, "Unlock password not recognized")
        if (!entry.allowedStartTimes.contains(startIso)) {
            return Result(false, "Start time not allowed for this password")
        }
        return try {
            val start = Instant.parse(startIso)
            val now = Instant.now()
            val latencyMin = (raw.latencyMinutes ?: 0).coerceAtLeast(0)
            val windowEnd = start.plus(Duration.ofMinutes(latencyMin.toLong()))
            if (now.isBefore(start)) {
                Result(false, "Too early to join")
            } else if (now.isAfter(windowEnd)) {
                Result(false, "Join window expired")
            } else {
                Result(true, null)
            }
        } catch (e: Exception) {
            Result(false, "Invalid start time in code")
        }
    }
}

