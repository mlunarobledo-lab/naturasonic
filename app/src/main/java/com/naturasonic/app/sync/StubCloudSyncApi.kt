package com.naturasonic.app.sync

import android.util.Log
import com.naturasonic.app.data.local.entity.AudioProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StubCloudSyncApi @Inject constructor() : CloudSyncApi {
    override suspend fun uploadProfiles(profiles: List<AudioProfile>): Boolean {
        Log.d("CloudSync", "Stub: ${profiles.size} profiles pending upload (no backend configured)")
        return false
    }

    override suspend fun uploadBackup(encryptedData: ByteArray): Boolean {
        Log.d("CloudSync", "Stub: encrypted backup ${encryptedData.size} bytes pending upload (no backend configured)")
        return true
    }
}
