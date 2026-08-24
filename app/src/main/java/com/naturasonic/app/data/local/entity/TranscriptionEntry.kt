package com.naturasonic.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "transcription_history")
data class TranscriptionEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val language: String,
    val durationMs: Long,
    val engine: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TranscriptionEngine(val key: String) {
    VOSK("VOSK"),
    WHISPER("WHISPER")
}
