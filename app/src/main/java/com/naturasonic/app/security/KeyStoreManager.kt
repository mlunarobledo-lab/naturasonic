package com.naturasonic.app.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        ENCRYPTED_PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getOrCreateDatabasePassphrase(): ByteArray {
        val stored = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
        if (stored != null) {
            return hexToBytes(stored)
        }

        val passphrase = ByteArray(32)
        SecureRandom().nextBytes(passphrase)

        encryptedPrefs.edit()
            .putString(KEY_DB_PASSPHRASE, bytesToHex(passphrase))
            .apply()

        return passphrase
    }

    fun hasPassphrase(): Boolean =
        encryptedPrefs.getString(KEY_DB_PASSPHRASE, null) != null

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    companion object {
        private const val ENCRYPTED_PREFS_FILE = "naturasonic_encrypted_prefs"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"
    }
}
