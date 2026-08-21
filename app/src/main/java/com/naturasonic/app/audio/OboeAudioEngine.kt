package com.naturasonic.app.audio

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OboeAudioEngine @Inject constructor() {

    private var engineHandle: Long = 0L

    val isRunning: Boolean get() = engineHandle != 0L
    val nativeHandle: Long get() = engineHandle

    fun create() {
        if (engineHandle == 0L) {
            engineHandle = nativeCreateEngine()
        }
    }

    fun start(): Boolean {
        if (engineHandle == 0L) create()
        return nativeStart(engineHandle)
    }

    fun stop() {
        if (engineHandle != 0L) {
            nativeStop(engineHandle)
        }
    }

    fun setAmplification(level: Float) {
        if (engineHandle != 0L) {
            nativeSetAmplification(engineHandle, level.coerceIn(0f, 1f))
        }
    }

    fun setEqBands(bands: FloatArray) {
        if (engineHandle != 0L) {
            nativeSetEqBands(engineHandle, bands)
        }
    }

    fun setNoiseSuppressionEnabled(enabled: Boolean) {
        if (engineHandle != 0L) {
            nativeSetNoiseSuppressionEnabled(engineHandle, enabled)
        }
    }

    fun setNoiseGateMode(mode: Int) {
        if (engineHandle != 0L) {
            nativeSetNoiseGateMode(engineHandle, mode.coerceIn(0, 2))
        }
    }

    fun setVolumeLimitDb(limitDb: Float) {
        if (engineHandle != 0L) {
            nativeSetVolumeLimitDb(engineHandle, limitDb)
        }
    }

    fun setOutputMuted(muted: Boolean) {
        if (engineHandle != 0L) {
            nativeSetOutputMuted(engineHandle, muted)
        }
    }

    fun setAttentionAgcEnabled(enabled: Boolean) {
        if (engineHandle != 0L) {
            nativeSetAttentionAgcEnabled(engineHandle, enabled)
        }
    }

    fun setAttentionGainOffsets(offsets: FloatArray) {
        if (engineHandle != 0L) {
            nativeSetAttentionGainOffsets(engineHandle, offsets)
        }
    }

    fun setHeadTrackingEnabled(enabled: Boolean) {
        if (engineHandle != 0L) {
            nativeSetHeadTrackingEnabled(engineHandle, enabled)
        }
    }

    fun setHeadTrackingAngles(azimuthDeg: Float, pitchDeg: Float, sensitivity: Float) {
        if (engineHandle != 0L) {
            nativeSetHeadTrackingAngles(engineHandle, azimuthDeg, pitchDeg, sensitivity.coerceIn(0f, 1f))
        }
    }

    fun applyProfile(bands: FloatArray, amplification: Float, noiseGateMode: Int) {
        if (engineHandle != 0L) {
            nativeApplyProfile(engineHandle, bands, amplification.coerceIn(0f, 1f), noiseGateMode.coerceIn(0, 2))
        }
    }

    fun getAudioBuffer(): FloatArray? {
        if (engineHandle == 0L) return null
        return nativeGetAudioBuffer(engineHandle)
    }

    fun getYamnetAudioBuffer(): FloatArray? {
        if (engineHandle == 0L) return null
        return nativeGetYamnetAudioBuffer(engineHandle)
    }

    fun getLatencyStats(): FloatArray? {
        if (engineHandle == 0L) return null
        return nativeGetLatencyStats(engineHandle)
    }

    fun startVoiceAnalyzer() {
        if (engineHandle != 0L) {
            nativeStartVoiceAnalyzer(engineHandle)
        }
    }

    fun stopVoiceAnalyzer() {
        if (engineHandle != 0L) {
            nativeStopVoiceAnalyzer(engineHandle)
        }
    }

    fun getVoiceMetrics(): FloatArray? {
        if (engineHandle == 0L) return null
        return nativeGetVoiceMetrics(engineHandle)
    }

    fun startDosimetry() {
        if (engineHandle != 0L) {
            nativeStartDosimetry(engineHandle)
        }
    }

    fun stopDosimetry() {
        if (engineHandle != 0L) {
            nativeStopDosimetry(engineHandle)
        }
    }

    fun getDosimetryData(): FloatArray? {
        if (engineHandle == 0L) return null
        return nativeGetDosimetryData(engineHandle)
    }

    fun setCalibrationOffset(offsetDb: Float) {
        if (engineHandle != 0L) {
            nativeSetCalibrationOffset(engineHandle, offsetDb)
        }
    }

    fun setAecMode(mode: Int) {
        if (engineHandle != 0L) {
            nativeSetAecMode(engineHandle, mode.coerceIn(0, 2))
        }
    }

    fun getAudioSessionId(): Int {
        if (engineHandle == 0L) return 0
        return nativeGetAudioSessionId(engineHandle)
    }

    fun destroy() {
        if (engineHandle != 0L) {
            nativeDestroy(engineHandle)
            engineHandle = 0L
        }
    }

    private external fun nativeCreateEngine(): Long
    private external fun nativeStart(engineHandle: Long): Boolean
    private external fun nativeStop(engineHandle: Long)
    private external fun nativeSetAmplification(engineHandle: Long, level: Float)
    private external fun nativeSetEqBands(engineHandle: Long, bands: FloatArray)
    private external fun nativeSetNoiseSuppressionEnabled(engineHandle: Long, enabled: Boolean)
    private external fun nativeSetNoiseGateMode(engineHandle: Long, mode: Int)
    private external fun nativeSetVolumeLimitDb(engineHandle: Long, limitDb: Float)
    private external fun nativeSetOutputMuted(engineHandle: Long, muted: Boolean)
    private external fun nativeSetAttentionAgcEnabled(engineHandle: Long, enabled: Boolean)
    private external fun nativeSetAttentionGainOffsets(engineHandle: Long, offsets: FloatArray)
    private external fun nativeSetHeadTrackingEnabled(engineHandle: Long, enabled: Boolean)
    private external fun nativeSetHeadTrackingAngles(engineHandle: Long, azimuthDeg: Float, pitchDeg: Float, sensitivity: Float)
    private external fun nativeApplyProfile(engineHandle: Long, bands: FloatArray, amplification: Float, noiseGateMode: Int)
    private external fun nativeGetAudioBuffer(engineHandle: Long): FloatArray?
    private external fun nativeGetYamnetAudioBuffer(engineHandle: Long): FloatArray?
    private external fun nativeGetLatencyStats(engineHandle: Long): FloatArray?
    private external fun nativeStartVoiceAnalyzer(engineHandle: Long)
    private external fun nativeStopVoiceAnalyzer(engineHandle: Long)
    private external fun nativeGetVoiceMetrics(engineHandle: Long): FloatArray?
    private external fun nativeStartDosimetry(engineHandle: Long)
    private external fun nativeStopDosimetry(engineHandle: Long)
    private external fun nativeGetDosimetryData(engineHandle: Long): FloatArray?
    private external fun nativeSetCalibrationOffset(engineHandle: Long, offsetDb: Float)
    private external fun nativeSetAecMode(engineHandle: Long, mode: Int)
    private external fun nativeGetAudioSessionId(engineHandle: Long): Int
    private external fun nativeDestroy(engineHandle: Long)

    companion object {
        init {
            System.loadLibrary("naturasonic")
        }
    }
}
