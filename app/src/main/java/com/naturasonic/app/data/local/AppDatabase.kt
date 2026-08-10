package com.naturasonic.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun audioProfileDao(): AudioProfileDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun alertEventDao(): AlertEventDao
}
