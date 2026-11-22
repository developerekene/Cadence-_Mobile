package com.devekene.kalenda.security

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
 * Hardware-backed AES-256-GCM encryption manager using AndroidKeyStore for stored event fields,
 * PBKDF2WithHmacSHA256 + AES-256-GCM for portable backups, and salted PBKDF2 for 6-digit PIN verification.
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

    fun hashPin(pin: String): Pair<String, String> {
        require(pin.length == 6 && pin.all { it.isDigit() }) { "PIN must be exactly 6 digits" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = derivePassphraseBytes(pin, salt, PIN_PBKDF2_ITERATIONS)
        return Base64.encodeToString(salt, Base64.NO_WRAP) to Base64.encodeToString(derived, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, saltB64: String, expectedHashB64: String): Boolean {
        if (pin.length != 6 || saltB64.isBlank() || expectedHashB64.isBlank()) return false
        return runCatching {
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            val derived = derivePassphraseBytes(pin, salt, PIN_PBKDF2_ITERATIONS)
            val expected = Base64.decode(expectedHashB64, Base64.NO_WRAP)
            MessageDigest.isEqual(derived, expected)
        }.getOrDefault(false)
    }

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
        require(parts.size == 4 && (parts[0] == BACKUP_HEADER || parts[0] == LEGACY_BACKUP_HEADER)) {
            "Invalid encrypted Kalenda vault format"
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
        return SecretKeySpec(derivePassphraseBytes(passphrase, salt, PBKDF2_ITERATIONS), "AES")
    }

    private fun derivePassphraseBytes(input: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(input.toCharArray(), salt, iterations, AES_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun getKeyFingerprint(): String {
        getOrCreateMasterKey()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$keyAlias:${if (isHardwareKeystoreActive) "HW" else "SW"}".toByteArray())
        return digest.take(6).joinToString("-") { "%02X".format(it) }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "kalenda_e2ee_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 120_000
        private const val PIN_PBKDF2_ITERATIONS = 60_000
        private const val PREFIX_V1 = "v1"
        private const val BACKUP_HEADER = "KALENDA_E2EE_V1"
        private const val LEGACY_BACKUP_HEADER = "CADENCE_E2EE_V1"
    }
}
