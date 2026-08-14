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

enum class AlertSoundClass(val key: String, val yamnetIndex: Int, val displayName: String) {
    SIREN("SIREN", 400, "Sirena detectada"),
    DOORBELL("DOORBELL", 382, "Timbre detectado"),
    BABY_CRY("BABY_CRY", 20, "Llanto de bebé detectado"),
    SMOKE_ALARM("SMOKE_ALARM", 395, "Alarma de humo detectada"),
    CAR_HORN("CAR_HORN", 377, "Claxon detectado"),
    GLASS_BREAK("GLASS_BREAK", 440, "Cristal roto detectado"),
    DOG_BARK("DOG_BARK", 67, "Ladrido detectado");

    companion object {
        private val indexMap = entries.associateBy { it.yamnetIndex }
        private val keyMap = entries.associateBy { it.key }
        fun fromYamnetIndex(index: Int): AlertSoundClass? = indexMap[index]
        fun fromKey(key: String): AlertSoundClass? = keyMap[key]
    }
}
