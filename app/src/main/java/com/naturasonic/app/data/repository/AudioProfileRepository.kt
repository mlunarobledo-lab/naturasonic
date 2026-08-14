package com.naturasonic.app.data.repository

import com.naturasonic.app.data.local.dao.AudioProfileDao
import com.naturasonic.app.data.local.entity.AudioMode
import com.naturasonic.app.data.local.entity.AudioProfile
import com.naturasonic.app.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioProfileRepository @Inject constructor(
    private val dao: AudioProfileDao,
    private val syncManager: SyncManager
) {

    fun getAllProfiles(): Flow<List<AudioProfile>> = dao.getAllProfiles()

    fun getProfilesByMode(mode: AudioMode): Flow<List<AudioProfile>> =
        dao.getProfilesByMode(mode.key)

    suspend fun getDefaultProfile(mode: AudioMode): AudioProfile? =
        dao.getDefaultProfile(mode.key)

    suspend fun getById(id: Long): AudioProfile? = dao.getById(id)

    suspend fun save(profile: AudioProfile): Long {
        val id = dao.insert(
            profile.copy(isSynced = false, lastModified = System.currentTimeMillis())
        )
        syncManager.scheduleSync()
        return id
    }

    suspend fun update(profile: AudioProfile) {
        dao.update(
            profile.copy(isSynced = false, lastModified = System.currentTimeMillis())
        )
        syncManager.scheduleSync()
    }

    suspend fun delete(profile: AudioProfile) = dao.delete(profile)

    suspend fun setAsDefault(profile: AudioProfile) {
        dao.clearDefaultForMode(profile.mode)
        dao.update(
            profile.copy(isDefault = true, isSynced = false, lastModified = System.currentTimeMillis())
        )
        syncManager.scheduleSync()
    }

    suspend fun ensureDefaultProfiles() {
        if (dao.count() > 0) return
        seedDefaults()
    }

    private suspend fun seedDefaults() {
        data class SeedConfig(
            val mode: AudioMode,
            val name: String,
            val amplification: Float,
            val ns: Boolean,
            val aec: Boolean,
            val eqBands: FloatArray
        )

        val seeds = listOf(
            SeedConfig(
                AudioMode.CONVERSATION, "Conversación",
                0.7f, true, true,
                floatArrayOf(2f, 3f, 4f, 4f, 3f, 2f, 0f, 0f, 0f, 0f)
            ),
            SeedConfig(
                AudioMode.ENTERTAINMENT, "Entretenimiento",
                0.5f, false, false,
                floatArrayOf(3f, 2f, 0f, 1f, 2f, 3f, 4f, 3f, 2f, 1f)
            ),
            SeedConfig(
                AudioMode.OUTDOOR, "Exterior",
                0.6f, true, false,
                floatArrayOf(1f, 2f, 3f, 3f, 2f, 1f, 0f, 0f, 0f, 0f)
            ),
            SeedConfig(
                AudioMode.REMOTE_MIC, "Micrófono Remoto",
                0.9f, true, true,
                floatArrayOf(4f, 5f, 5f, 5f, 4f, 3f, 2f, 1f, 0f, 0f)
            )
        )

        for (seed in seeds) {
            dao.insert(
                AudioProfile(
                    name = seed.name,
                    mode = seed.mode.key,
                    eqBands = Json.encodeToString(seed.eqBands.toList()),
                    amplificationLevel = seed.amplification,
                    noiseSuppressionEnabled = seed.ns,
                    aecEnabled = seed.aec,
                    isDefault = true
                )
            )
        }
    }
}
