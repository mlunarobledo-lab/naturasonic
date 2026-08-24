package com.naturasonic.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "audio_profiles")
data class AudioProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: String,
    val eqBands: String,
    val amplificationLevel: Float,
    val noiseSuppressionEnabled: Boolean,
    val aecEnabled: Boolean,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

enum class AudioMode(val key: String) {
    CONVERSATION("CONVERSATION"),
    ENTERTAINMENT("ENTERTAINMENT"),
    OUTDOOR("OUTDOOR"),
    REMOTE_MIC("REMOTE_MIC");

    companion object {
        fun fromKey(key: String): AudioMode =
            entries.firstOrNull { it.key == key } ?: CONVERSATION
    }
}
