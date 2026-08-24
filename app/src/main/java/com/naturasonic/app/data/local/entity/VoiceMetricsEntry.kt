package com.naturasonic.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "voice_metrics")
data class VoiceMetricsEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pitchHz: Float,
    val jitterPercent: Float,
    val shimmerPercent: Float,
    val recordedAt: Long = System.currentTimeMillis()
)
