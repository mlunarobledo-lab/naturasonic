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
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
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
    private val alertEventDao: AlertEventDao
) {
    private var interpreter: Interpreter? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _latestAlert = MutableStateFlow<DetectedAlert?>(null)
    val latestAlert: StateFlow<DetectedAlert?> = _latestAlert.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun getModelFile(): File = File(context.filesDir, "models/yamnet/yamnet.tflite")

    fun isModelAvailable(): Boolean = getModelFile().exists()

    fun loadModel() {
        try {
            val modelFile = getModelFile()
            if (!modelFile.exists()) return

            val fileInputStream = FileInputStream(modelFile)
            val fileChannel = fileInputStream.channel
            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY, 0, fileChannel.size()
            )
            fileInputStream.close()

            interpreter = Interpreter(modelBuffer, Interpreter.Options().apply {
                setNumThreads(2)
            })
            _isModelLoaded.value = true
        } catch (_: Exception) {
            _isModelLoaded.value = false
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

        val yamnetInputSize = 15600
        if (buffer.size < yamnetInputSize) return

        try {
            val inputBuffer = ByteBuffer.allocateDirect(yamnetInputSize * 4)
                .order(ByteOrder.nativeOrder())
            for (i in 0 until yamnetInputSize) {
                inputBuffer.putFloat(buffer[i])
            }
            inputBuffer.rewind()

            val outputScores = Array(1) { FloatArray(521) }

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
        _isModelLoaded.value = false
    }

    companion object {
        private const val CONFIDENCE_THRESHOLD = 0.3f
    }
}
