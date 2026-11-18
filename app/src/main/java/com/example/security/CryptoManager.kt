package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed AES-256-GCM encryption manager using AndroidKeyStore for all stored event fields,
 * plus PBKDF2WithHmacSHA256 + AES-256-GCM passphrase encryption for portable E2EE backups.
 */
class CryptoManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) {
    private var fallbackKey: SecretKey? = null
    private val lock = Any()

    val isHardwareKeystoreActive: Boolean
        get() = runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            ks.containsAlias(keyAlias) || canUseAndroidKeystore()
        }.getOrDefault(false)

    private fun canUseAndroidKeystore(): Boolean = runCatching {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        true
    }.getOrDefault(false)

    private fun getOrCreateMasterKey(): SecretKey {
        synchronized(lock) {
            return runCatching {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val existing = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
                if (existing != null) {
                    existing.secretKey
                } else {
                    val keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        ANDROID_KEYSTORE
                    )
                    val spec = KeyGenParameterSpec.Builder(
                        keyAlias,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(AES_KEY_BITS)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                    keyGenerator.init(spec)
                    keyGenerator.generateKey()
                }
            }.getOrElse {
                fallbackKey ?: run {
                    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    SecretKeySpec(bytes, "AES").also { fallbackKey = it }
                }
            }
        }
    }

    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "$PREFIX_V1:$ivB64:$ctB64"
    }

    fun decrypt(payload: String): String {
        if (payload.isEmpty()) return ""
        if (!payload.startsWith("$PREFIX_V1:")) {
            return payload
        }
        val parts = payload.split(":", limit = 3)
        if (parts.size != 3) return ""
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Encrypts an exported vault JSON string with a user-chosen passphrase using
     * PBKDF2WithHmacSHA256 (120k iterations) + AES-256-GCM.
     */
    fun encryptWithPassphrase(plaintext: String, passphrase: String): String {
        require(passphrase.length >= 6) { "Passphrase must be at least 6 characters" }
        val rng = SecureRandom()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val key = derivePassphraseKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return listOf(
            BACKUP_HEADER,
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP),
            Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        ).joinToString(":")
    }

    fun decryptWithPassphrase(payload: String, passphrase: String): String {
        val parts = payload.trim().split(":", limit = 4)
        require(parts.size == 4 && parts[0] == BACKUP_HEADER) {
            "Invalid encrypted Cadence vault format"
        }
        val salt = Base64.decode(parts[1], Base64.NO_WRAP)
        val iv = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[3], Base64.NO_WRAP)
        val key = derivePassphraseKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    private fun derivePassphraseKey(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        return SecretKeySpec(derived, "AES")
    }

    fun getKeyFingerprint(): String {
        // Ensure key exists first, then derive a non-sensitive display fingerprint from alias + status
        getOrCreateMasterKey()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$keyAlias:${if (isHardwareKeystoreActive) "HW" else "SW"}".toByteArray())
        return digest.take(6).joinToString("-") { "%02X".format(it) }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "cadence_e2ee_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PREFIX_V1 = "v1"
        private const val BACKUP_HEADER = "CADENCE_E2EE_V1"
    }
}
