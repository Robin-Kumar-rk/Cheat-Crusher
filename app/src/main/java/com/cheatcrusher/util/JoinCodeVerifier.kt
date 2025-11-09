package com.cheatcrusher.util

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import android.util.Log
import java.time.Instant
import java.time.Duration
import java.util.Base64
import java.security.MessageDigest

data class RawQuiz(
    val quizId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val downloadCode: String? = null,
    val latencyMinutes: Int? = null,
    val timerMinutes: Int? = null,
    val preForm: PreForm? = null,
    val questions: List<RawQuestion> = emptyList(),
    val unlockPassword: String? = null,
    val answerViewPassword: String? = null,
    val autoDeleteDays: Int? = null,
    // Optional policy field present in admin-generated JSON
    val onAppSwitch: String? = null
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

object JoinCodeVerifier {
    private val gson = Gson()

    data class Result(val ok: Boolean, val error: String? = null)

    fun parse(rawJson: String?): RawQuiz? {
        return try {
            if (rawJson.isNullOrBlank()) null else gson.fromJson(rawJson, RawQuiz::class.java)
        } catch (_: Exception) { null }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun verify(raw: RawQuiz?, joinCode: String?): Result {
        if (raw == null) return Result(false, "Missing quiz config")
        val code = (joinCode ?: "").trim()
        val configuredPwd = raw.unlockPassword?.trim()
        if (configuredPwd.isNullOrBlank()) return Result(false, "Unlock password missing in quiz")

        // Support unreadable J2 codes and legacy "pwd|ISO" codes
        val startInstant: Instant? = if (code.startsWith("J2")) {
            Log.d("JoinCodeVerifier", "Decoding J2 join code")
            decodeJ2StartInstant(code, configuredPwd)
        } else {
            val parts = code.split("|")
            if (parts.size != 2) return Result(false, "Invalid join code format")
            val pwd = parts[0].trim()
            val startIso = parts[1].trim()
            if (pwd != configuredPwd) return Result(false, "Unlock password not recognized")
            try { Instant.parse(startIso) } catch (e: Exception) { Log.e("JoinCodeVerifier", "Invalid ISO start time", e); null }
        }
        if (startInstant == null) return Result(false, "Invalid join code")

        val now = Instant.now()
        val latencyMin = (raw.latencyMinutes ?: 0).coerceAtLeast(0)
        val windowEnd = startInstant.plus(Duration.ofMinutes(latencyMin.toLong()))
        return if (now.isBefore(startInstant)) {
            Result(false, "Too early to join")
        } else if (now.isAfter(windowEnd)) {
            Result(false, "Join window expired")
        } else {
            Result(true, null)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun decodeJ2StartInstant(code: String, unlockPassword: String): Instant? {
        // Format: J2<base64url(8 bytes maskedEpoch)>-<checksum6>
        val body = code.removePrefix("J2")
        val parts = body.split("-")
        if (parts.size != 2) return null
        val b64 = parts[0]
        val checksum = parts[1]
        val payload = try { base64UrlDecode(b64) } catch (e: Exception) { Log.e("JoinCodeVerifier", "Base64 decode failed", e); return null }
        if (payload.size != 8) return null

        // Verify checksum: sha256(payload + password) first 6 hex chars
        val calc = sha256Hex(payload + unlockPassword.toByteArray())
        if (!calc.startsWith(checksum.lowercase())) return null

        // Unmask: epochSec = masked ^ first8bytes(sha256(password))
        val maskBytes = sha256(unlockPassword.toByteArray()).copyOfRange(0, 8)
        val masked = bytesToLong(payload)
        val mask = bytesToLong(maskBytes)
        val epoch = masked xor mask
        return try { Instant.ofEpochSecond(epoch) } catch (_: Exception) { null }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun base64UrlDecode(s: String): ByteArray {
        var str = s.replace('-', '+').replace('_', '/')
        val pad = (4 - str.length % 4) % 4
        str += "=".repeat(pad)
        return android.util.Base64.decode(str, android.util.Base64.DEFAULT)
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
    private fun sha256Hex(data: ByteArray): String = sha256(data).joinToString("") { b -> "%02x".format(b) }

    private fun bytesToLong(bytes: ByteArray): Long {
        var res = 0L
        for (b in bytes) {
            res = (res shl 8) or (b.toInt() and 0xFF).toLong()
        }
        return res
    }
}
