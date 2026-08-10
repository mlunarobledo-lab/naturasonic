package com.naturasonic.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_log")
data class AlertEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val soundClass: String,
    val confidence: Float,
    val detectedAt: Long = System.currentTimeMillis()
)

enum class AlertSoundClass(val key: String, val yamnetIndex: Int) {
    SIREN("SIREN", 400),
    DOORBELL("DOORBELL", 382),
    BABY_CRY("BABY_CRY", 20),
    SMOKE_ALARM("SMOKE_ALARM", 395),
    CAR_HORN("CAR_HORN", 377),
    GLASS_BREAK("GLASS_BREAK", 440),
    DOG_BARK("DOG_BARK", 67);

    companion object {
        private val indexMap = entries.associateBy { it.yamnetIndex }
        fun fromYamnetIndex(index: Int): AlertSoundClass? = indexMap[index]
    }
}
