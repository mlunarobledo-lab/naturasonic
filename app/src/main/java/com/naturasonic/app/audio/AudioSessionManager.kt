package com.naturasonic.app.audio

import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioSessionManager @Inject constructor() {

    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    val isAecAvailable: Boolean get() = AcousticEchoCanceler.isAvailable()
    val isNsAvailable: Boolean get() = NoiseSuppressor.isAvailable()

    fun enableSystemEffects(audioSessionId: Int) {
        enableAec(audioSessionId)
        enableNs(audioSessionId)
    }

    fun disableSystemEffects() {
        releaseAec()
        releaseNs()
    }

    fun setAecEnabled(audioSessionId: Int, enabled: Boolean) {
        if (enabled) enableAec(audioSessionId) else releaseAec()
    }

    fun setNsEnabled(audioSessionId: Int, enabled: Boolean) {
        if (enabled) enableNs(audioSessionId) else releaseNs()
    }

    private fun enableAec(audioSessionId: Int) {
        if (!isAecAvailable) return
        try {
            releaseAec()
            aec = AcousticEchoCanceler.create(audioSessionId)?.apply {
                enabled = true
            }
        } catch (_: Exception) { }
    }

    private fun enableNs(audioSessionId: Int) {
        if (!isNsAvailable) return
        try {
            releaseNs()
            ns = NoiseSuppressor.create(audioSessionId)?.apply {
                enabled = true
            }
        } catch (_: Exception) { }
    }

    private fun releaseAec() {
        aec?.release()
        aec = null
    }

    private fun releaseNs() {
        ns?.release()
        ns = null
    }

    fun release() {
        releaseAec()
        releaseNs()
    }
}
