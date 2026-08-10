package com.naturasonic.app.transcription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class VoskResult(
    val text: String = "",
    val partial: String = ""
)

@Singleton
class VoskTranscriptionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: Any? = null
    private var model: Any? = null

    private val _currentText = MutableStateFlow("")
    val currentText: StateFlow<String> = _currentText.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    fun getModelDir(): File = File(context.filesDir, "models/vosk")

    fun isModelDownloaded(language: String = "es"): Boolean {
        val modelDir = File(getModelDir(), "vosk-model-small-$language")
        return modelDir.exists() && modelDir.isDirectory
    }

    suspend fun downloadModel(language: String = "es", onProgress: (Float) -> Unit = {}) {
        withContext(Dispatchers.IO) {
            val modelName = "vosk-model-small-$language"
            val url = "https://alphacephei.com/vosk/models/$modelName.zip"
            val destDir = getModelDir()
            destDir.mkdirs()

            val zipFile = File(destDir, "$modelName.zip")

            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connect()
                val totalSize = connection.contentLength.toFloat()
                var downloadedSize = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(zipFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead
                            val progress = if (totalSize > 0) downloadedSize / totalSize else 0f
                            _downloadProgress.value = progress
                            onProgress(progress)
                        }
                    }
                }

                unzip(zipFile, destDir)
                zipFile.delete()
                _isModelReady.value = true
            } catch (e: Exception) {
                zipFile.delete()
                throw e
            }
        }
    }

    fun initializeModel(language: String = "es") {
        if (_isModelReady.value) return
        val modelDir = File(getModelDir(), "vosk-model-small-$language")
        if (!modelDir.exists()) return

        try {
            val voskModelClass = Class.forName("org.vosk.Model")
            model = voskModelClass.getConstructor(String::class.java)
                .newInstance(modelDir.absolutePath)

            val recognizerClass = Class.forName("org.vosk.Recognizer")
            recognizer = recognizerClass.getConstructor(voskModelClass, Float::class.java)
                .newInstance(model, 48000f)

            _isModelReady.value = true
        } catch (e: Exception) {
            model = null
            recognizer = null
            _isModelReady.value = false
        }
    }

    fun processAudioBuffer(buffer: FloatArray) {
        val rec = recognizer ?: return
        if (!_isTranscribing.value) return

        try {
            val shortBuffer = ShortArray(buffer.size) { i ->
                (buffer[i] * Short.MAX_VALUE).toInt().toShort()
            }
            val byteBuffer = ByteArray(shortBuffer.size * 2)
            for (i in shortBuffer.indices) {
                byteBuffer[i * 2] = (shortBuffer[i].toInt() and 0xFF).toByte()
                byteBuffer[i * 2 + 1] = (shortBuffer[i].toInt() shr 8 and 0xFF).toByte()
            }

            val acceptWaveformMethod = rec.javaClass.getMethod(
                "acceptWaveForm", ByteArray::class.java, Int::class.java
            )
            val accepted = acceptWaveformMethod.invoke(rec, byteBuffer, byteBuffer.size) as Boolean

            val resultText = if (accepted) {
                val getResultMethod = rec.javaClass.getMethod("getResult")
                val resultJson = getResultMethod.invoke(rec) as String
                json.decodeFromString<VoskResult>(resultJson).text
            } else {
                val getPartialMethod = rec.javaClass.getMethod("getPartialResult")
                val partialJson = getPartialMethod.invoke(rec) as String
                json.decodeFromString<VoskResult>(partialJson).partial
            }

            if (resultText.isNotBlank()) {
                _currentText.value = resultText
            }
        } catch (_: Exception) { }
    }

    fun startTranscription() {
        _isTranscribing.value = true
        _currentText.value = ""
    }

    fun stopTranscription() {
        _isTranscribing.value = false
    }

    fun release() {
        stopTranscription()
        try {
            recognizer?.javaClass?.getMethod("close")?.invoke(recognizer)
        } catch (_: Exception) { }
        try {
            model?.javaClass?.getMethod("close")?.invoke(model)
        } catch (_: Exception) { }
        recognizer = null
        model = null
        _isModelReady.value = false
    }

    private fun unzip(zipFile: File, destDir: File) {
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
