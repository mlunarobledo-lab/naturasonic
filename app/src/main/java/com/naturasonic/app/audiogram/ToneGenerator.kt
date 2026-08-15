package com.naturasonic.app.audiogram

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

class ToneGenerator {

    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100

    fun play(frequencyHz: Float, amplitudeLinear: Float) {
        stop()

        val durationSamples = sampleRate * 2
        val buffer = ShortArray(durationSamples)
        val clampedAmplitude = amplitudeLinear.coerceIn(0f, 1f)

        val fadeInSamples = (sampleRate * 0.05f).toInt()
        val fadeOutSamples = (sampleRate * 0.05f).toInt()

        for (i in buffer.indices) {
            val phase = 2.0 * Math.PI * frequencyHz * i / sampleRate
            var sample = sin(phase).toFloat() * clampedAmplitude

            if (i < fadeInSamples) {
                sample *= i.toFloat() / fadeInSamples
            } else if (i > durationSamples - fadeOutSamples) {
                sample *= (durationSamples - i).toFloat() / fadeOutSamples
            }

            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }

        val bufferSize = buffer.size * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.apply {
            write(buffer, 0, buffer.size)
            setLoopPoints(0, buffer.size, -1)
            play()
        }
    }

    fun stop() {
        audioTrack?.let {
            try {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            } catch (_: IllegalStateException) { }
        }
        audioTrack = null
    }

    fun release() {
        stop()
    }

    companion object {
        val TEST_FREQUENCIES = floatArrayOf(250f, 500f, 1000f, 2000f, 4000f, 8000f)

        fun dbHlToLinearAmplitude(dbHl: Float): Float {
            return Math.pow(10.0, (dbHl - 80.0) / 20.0).toFloat().coerceIn(0f, 1f)
        }
    }
}
