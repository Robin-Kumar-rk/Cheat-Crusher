package com.cheatcrusher.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [LocalJoin::class, LocalHistoryItem::class, CachedQuiz::class, PendingSubmission::class, StudentProfile::class],
    version = 3,
    exportSchema = false
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun localDao(): LocalDao
}