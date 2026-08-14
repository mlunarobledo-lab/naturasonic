package com.naturasonic.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.naturasonic.app.data.local.dao.AlertEventDao
import com.naturasonic.app.data.local.dao.AudioProfileDao
import com.naturasonic.app.data.local.dao.TranscriptionDao
import com.naturasonic.app.data.local.entity.AlertEvent
import com.naturasonic.app.data.local.entity.AudioProfile
import com.naturasonic.app.data.local.entity.TranscriptionEntry

@Database(
    entities = [
        AudioProfile::class,
        TranscriptionEntry::class,
        AlertEvent::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioProfileDao(): AudioProfileDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun alertEventDao(): AlertEventDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_profiles ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio_profiles ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
