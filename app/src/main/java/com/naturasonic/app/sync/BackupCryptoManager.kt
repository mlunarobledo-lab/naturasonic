package com.naturasonic.app.sync

import com.naturasonic.app.security.KeyStoreManager
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupCryptoManager @Inject constructor(
    private val keyStoreManager: KeyStoreManager
) {
    fun encrypt(plaintext: ByteArray): ByteArray {
        val passphrase = keyStoreManager.getOrCreateDatabasePassphrase()
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(passphrase, ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext)

        return iv + ciphertext
    }

    fun decrypt(encrypted: ByteArray): ByteArray {
        val passphrase = keyStoreManager.getOrCreateDatabasePassphrase()
        val iv = encrypted.copyOfRange(0, IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(IV_LENGTH, encrypted.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(passphrase, ALGORITHM)
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        return cipher.doFinal(ciphertext)
    }

    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
    }
}
