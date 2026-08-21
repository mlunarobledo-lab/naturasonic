package com.naturasonic.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.naturasonic.app.data.local.dao.AlertEventDao
import com.naturasonic.app.data.local.dao.AudioProfileDao
import com.naturasonic.app.data.local.dao.AudiogramDao
import com.naturasonic.app.data.local.dao.DosimetrySampleDao
import com.naturasonic.app.data.local.dao.TranscriptionDao
import com.naturasonic.app.data.local.dao.VoiceMetricsDao
import com.naturasonic.app.data.local.entity.AlertEvent
import com.naturasonic.app.data.local.entity.AudioProfile
import com.naturasonic.app.data.local.entity.AudiogramRecord
import com.naturasonic.app.data.local.entity.DosimetrySample
import com.naturasonic.app.data.local.entity.TranscriptionEntry
import com.naturasonic.app.data.local.entity.VoiceMetricsEntry

@Database(
    entities = [
        AudioProfile::class,
        TranscriptionEntry::class,
        AlertEvent::class,
        AudiogramRecord::class,
        VoiceMetricsEntry::class,
        DosimetrySample::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioProfileDao(): AudioProfileDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun alertEventDao(): AlertEventDao
    abstract fun audiogramDao(): AudiogramDao
    abstract fun voiceMetricsDao(): VoiceMetricsDao
    abstract fun dosimetrySampleDao(): DosimetrySampleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE audio_profiles ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE audio_profiles ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS audiogram_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dateMillis INTEGER NOT NULL,
                        leftThresholds TEXT NOT NULL,
                        rightThresholds TEXT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS voice_metrics (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        pitchHz REAL NOT NULL,
                        jitterPercent REAL NOT NULL,
                        shimmerPercent REAL NOT NULL,
                        recordedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS dosimetry_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        instantDba REAL NOT NULL,
                        leq REAL NOT NULL,
                        doseOsha REAL NOT NULL,
                        doseNiosh REAL NOT NULL,
                        recordedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
    }
}
