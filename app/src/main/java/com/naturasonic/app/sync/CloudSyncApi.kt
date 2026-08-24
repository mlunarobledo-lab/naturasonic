package com.naturasonic.app.sync

import com.naturasonic.app.data.local.entity.AudioProfile

interface CloudSyncApi {
    suspend fun uploadProfiles(profiles: List<AudioProfile>): Boolean
    suspend fun uploadBackup(encryptedData: ByteArray): Boolean
}
