package com.naturasonic.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "dosimetry_samples")
data class DosimetrySample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val instantDba: Float,
    val leq: Float,
    val doseOsha: Float,
    val doseNiosh: Float,
    val recordedAt: Long = System.currentTimeMillis()
)
