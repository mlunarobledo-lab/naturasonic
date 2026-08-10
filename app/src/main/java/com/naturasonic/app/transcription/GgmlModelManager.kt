package com.naturasonic.app.transcription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ModelState {
    data object Uninitialized : ModelState
    data class Copying(val progress: Float) : ModelState
    data class Ready(val modelPath: String) : ModelState
    data class Error(val message: String) : ModelState
}

@Singleton
class GgmlModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow<ModelState>(ModelState.Uninitialized)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private fun getModelDir(): File = File(context.filesDir, "models/whisper")

    fun isModelInStorage(model: WhisperModel): Boolean {
        val file = File(getModelDir(), model.fileName)
        return file.exists() && file.length() > 0
    }

    suspend fun ensureModel(model: WhisperModel): String? {
        val destFile = File(getModelDir(), model.fileName)

        if (destFile.exists() && destFile.length() > 0) {
            _state.value = ModelState.Ready(destFile.absolutePath)
            return destFile.absolutePath
        }

        return tryExtractFromAssets(model, destFile)
    }

    private suspend fun tryExtractFromAssets(
        model: WhisperModel,
        destFile: File
    ): String? = withContext(Dispatchers.IO) {
        val assetPath = "models/whisper/${model.fileName}"

        val totalBytes = try {
            context.assets.openFd(assetPath).use { it.length }
        } catch (_: FileNotFoundException) {
            _state.value = ModelState.Uninitialized
            return@withContext null
        } catch (_: Exception) {
            val estimated = model.sizeMb.toLong() * 1024L * 1024L
            estimated
        }

        _state.value = ModelState.Copying(0f)

        try {
            destFile.parentFile?.mkdirs()

            context.assets.open(assetPath).buffered(BUFFER_SIZE).use { input ->
                FileOutputStream(destFile).buffered(BUFFER_SIZE).use { output ->
                    var bytesCopied = 0L
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead
                        val progress = if (totalBytes > 0) {
                            (bytesCopied.toFloat() / totalBytes).coerceAtMost(1f)
                        } else 0f
                        _state.value = ModelState.Copying(progress)
                    }
                }
            }

            _state.value = ModelState.Ready(destFile.absolutePath)
            destFile.absolutePath
        } catch (e: Exception) {
            destFile.delete()
            _state.value = ModelState.Error(e.message ?: "Error al preparar modelo")
            null
        }
    }

    fun resetState() {
        _state.value = ModelState.Uninitialized
    }

    companion object {
        private const val BUFFER_SIZE = 16384
    }
}
