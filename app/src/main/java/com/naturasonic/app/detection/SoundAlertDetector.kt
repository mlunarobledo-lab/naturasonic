package com.naturasonic.app.detection

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
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
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
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
    private var interpreter: Interpreter? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _latestAlert = MutableStateFlow<DetectedAlert?>(null)
    val latestAlert: StateFlow<DetectedAlert?> = _latestAlert.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    val modelState: StateFlow<YamnetModelState> = yamnetModelManager.state

    suspend fun loadModel() {
        val path = yamnetModelManager.ensureModel() ?: return

        try {
            val fileInputStream = FileInputStream(path)
            val fileChannel = fileInputStream.channel
            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY, 0, fileChannel.size()
            )
            fileInputStream.close()

            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                setNumThreads(2)
            })
        } catch (e: Exception) {
            interpreter = null
        }
    }

    fun start() {
        _isRunning.value = true
    }

    fun stop() {
        _isRunning.value = false
    }

    fun processAudioBuffer(buffer: FloatArray) {
        if (!_isRunning.value || interpreter == null) return

        if (buffer.size < YAMNET_INPUT_SIZE) return

        try {
            val inputBuffer = ByteBuffer.allocateDirect(YAMNET_INPUT_SIZE * 4)
                .order(ByteOrder.nativeOrder())
            for (i in 0 until YAMNET_INPUT_SIZE) {
                inputBuffer.putFloat(buffer[i])
            }
            inputBuffer.rewind()

            val outputScores = Array(1) { FloatArray(YAMNET_OUTPUT_CLASSES) }

            interpreter?.run(inputBuffer, outputScores)

            val scores = outputScores[0]
            for (alertClass in AlertSoundClass.entries) {
                val score = scores.getOrNull(alertClass.yamnetIndex) ?: continue
                if (score > CONFIDENCE_THRESHOLD) {
                    val alert = DetectedAlert(alertClass, score)
                    _latestAlert.value = alert
                    vibrate()
                    scope.launch {
                        alertEventDao.insert(
                            AlertEvent(
                                soundClass = alertClass.key,
                                confidence = score
                            )
                        )
                    }
                    break
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
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.3f
        private const val YAMNET_INPUT_SIZE = 15600
        private const val YAMNET_OUTPUT_CLASSES = 521
    }
}
