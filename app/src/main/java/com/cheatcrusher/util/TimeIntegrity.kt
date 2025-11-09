package com.cheatcrusher.util

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

data class TimeSnapshot(
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val downloadedAtElapsedRealtime: Long
)

object TimeIntegrity {
    fun isAutoTimeEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    fun createSnapshot(startsAtMillis: Long, endsAtMillis: Long): TimeSnapshot {
        return TimeSnapshot(
            startsAtMillis = startsAtMillis,
            endsAtMillis = endsAtMillis,
            downloadedAtElapsedRealtime = SystemClock.elapsedRealtime()
        )
    }

    fun isActiveNow(snapshot: TimeSnapshot): Boolean {
        val nowServerEstimate = estimateServerNow(snapshot)
        return nowServerEstimate in snapshot.startsAtMillis..snapshot.endsAtMillis
    }

    fun estimateServerNow(snapshot: TimeSnapshot): Long {
        val delta = SystemClock.elapsedRealtime() - snapshot.downloadedAtElapsedRealtime
        // We trust the server-provided starts/ends at download time; use monotonic clock delta
        return snapshot.startsAtMillis + delta
    }
}