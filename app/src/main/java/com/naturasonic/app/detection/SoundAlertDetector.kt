package com.naturasonic.app.detection

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.naturasonic.app.data.local.dao.AlertEventDao
import com.naturasonic.app.data.local.entity.AlertEvent
import com.naturasonic.app.data.local.entity.AlertSoundClass
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.core.BaseOptions
import javax.inject.Inject
import javax.inject.Singleton

data class DetectedAlert(
    val soundClass: AlertSoundClass,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class SoundAlertDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alertEventDao: AlertEventDao,
    private val yamnetModelManager: YamnetModelManager
) {
    private var classifier: AudioClassifier? = null
    private var tensorAudio: TensorAudio? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _latestAlert = MutableStateFlow<DetectedAlert?>(null)
    val latestAlert: StateFlow<DetectedAlert?> = _latestAlert.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    val modelState: StateFlow<YamnetModelState> = yamnetModelManager.state

    suspend fun loadModel() {
        val path = yamnetModelManager.ensureModel() ?: return

        try {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setScoreThreshold(CONFIDENCE_THRESHOLD)
                .setBaseOptions(
                    BaseOptions.builder()
                        .setNumThreads(2)
                        .build()
                )
                .build()

            classifier = AudioClassifier.createFromFileAndOptions(context, path, options)
            tensorAudio = classifier?.createInputTensorAudio()
        } catch (e: Exception) {
            classifier?.close()
            classifier = null
            tensorAudio = null
        }
    }

    fun start() {
        _isRunning.value = true
    }

    fun stop() {
        _isRunning.value = false
    }

    fun processAudio48kHz(buffer48k: FloatArray) {
        if (buffer48k.size < YAMNET_48K_INPUT_SIZE) return
        val resampled = resample48to16(buffer48k)
        processAudioBuffer(resampled)
    }

    private fun resample48to16(input: FloatArray): FloatArray {
        val outputSize = input.size / 3
        val output = FloatArray(outputSize)
        for (i in 0 until outputSize) {
            val srcIdx = i * 3
            output[i] = (input[srcIdx] + input[srcIdx + 1] + input[srcIdx + 2]) / 3f
        }
        return output
    }

    private fun processAudioBuffer(buffer: FloatArray) {
        val audio = tensorAudio ?: return
        val cls = classifier ?: return
        if (!_isRunning.value) return
        if (buffer.size < YAMNET_INPUT_SIZE) return

        try {
            audio.load(buffer.copyOfRange(0, YAMNET_INPUT_SIZE))

            val results = cls.classify(audio)

            for (classifications in results) {
                for (category in classifications.categories) {
                    val alertClass = AlertSoundClass.fromYamnetIndex(category.index) ?: continue
                    val alert = DetectedAlert(alertClass, category.score)
                    _latestAlert.value = alert
                    vibrate()
                    scope.launch {
                        alertEventDao.insert(
                            AlertEvent(
                                soundClass = alertClass.key,
                                confidence = category.score
                            )
                        )
                    }
                    return
                }
            }
        } catch (_: Exception) { }
    }

    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            vibrator.vibrate(
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) { }
    }

    fun release() {
        stop()
        classifier?.close()
        classifier = null
        tensorAudio = null
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val YAMNET_INPUT_SIZE = 15600
        private const val YAMNET_48K_INPUT_SIZE = YAMNET_INPUT_SIZE * 3
    }
}
