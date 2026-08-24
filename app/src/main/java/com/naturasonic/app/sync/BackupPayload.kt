package com.naturasonic.app.sync

import com.naturasonic.app.data.local.entity.AlertEvent
import com.naturasonic.app.data.local.entity.AudioProfile
import com.naturasonic.app.data.local.entity.AudiogramRecord
import com.naturasonic.app.data.local.entity.DosimetrySample
import com.naturasonic.app.data.local.entity.TranscriptionEntry
import com.naturasonic.app.data.local.entity.VoiceMetricsEntry
import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val profiles: List<AudioProfile>,
    val transcriptions: List<TranscriptionEntry>,
    val alerts: List<AlertEvent>,
    val audiograms: List<AudiogramRecord>,
    val voiceMetrics: List<VoiceMetricsEntry>,
    val dosimetrySamples: List<DosimetrySample>
)
