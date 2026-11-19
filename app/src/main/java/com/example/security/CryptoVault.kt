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
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Hardware-backed Android Keystore AES-256-GCM encryption vault for securing
 * sensitive contact notes, call recordings, and encrypted cross-device sync packages.
 */
class CryptoVault {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "voxline_aes256_gcm_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_BITS = 128
        private const val PREFIX = "VXL1:"
    }

    data class VaultStatus(
        val cipherSuite: String,
        val keyStoreBacked: Boolean,
        val keyAlias: String,
        val keyFingerprint: String
    )

    private var fallbackKey: SecretKey? = null
    private var isHardwareKeystore: Boolean = true

    private fun getOrCreateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existing != null) {
                isHardwareKeystore = true
                existing
            } else {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGen.init(spec)
                isHardwareKeystore = true
                keyGen.generateKey()
            }
        } catch (_: Exception) {
            // Safe JVM/unit-test fallback if AndroidKeyStore is unavailable
            isHardwareKeystore = false
            fallbackKey ?: run {
                val bytes = ByteArray(32)
                SecureRandom().nextBytes(bytes)
                SecretKeySpec(bytes, "AES").also { fallbackKey = it }
            }
        }
    }

    fun getStatus(): VaultStatus {
        val key = getOrCreateKey()
        val fpInput = "$KEY_ALIAS:${key.algorithm}:${if (isHardwareKeystore) "AKS" else "MEM"}"
        val digest = sha256Hex(fpInput.toByteArray()).take(16).uppercase()
            .chunked(4).joinToString("-")
        return VaultStatus(
            cipherSuite = "AES-256-GCM (128-bit Auth Tag)",
            keyStoreBacked = isHardwareKeystore,
            keyAlias = KEY_ALIAS,
            keyFingerprint = digest
        )
    }

    fun encryptString(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val encrypted = encryptBytes(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptString(ciphertext: String): String {
        if (ciphertext.isEmpty()) return ""
        if (!ciphertext.startsWith(PREFIX)) return ciphertext
        return try {
            val raw = Base64.decode(ciphertext.removePrefix(PREFIX), Base64.NO_WRAP)
            String(decryptBytes(raw), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    fun encryptBytes(plainBytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainBytes)
        val combined = ByteArray(1 + iv.size + cipherBytes.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
        return combined
    }

    fun decryptBytes(payload: ByteArray): ByteArray {
        require(payload.size > 1 + GCM_IV_LENGTH) { "Invalid encrypted payload" }
        val ivLen = payload[0].toInt() and 0xFF
        require(ivLen in 12..16 && payload.size > 1 + ivLen) { "Invalid IV header" }
        val iv = payload.copyOfRange(1, 1 + ivLen)
        val cipherBytes = payload.copyOfRange(1 + ivLen, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherBytes)
    }

    /**
     * Password/passphrase-derived AES-256-GCM encryption for portable cross-device
     * sync bundles so another device running VoxLine can decrypt with the same passphrase.
     */
    fun encryptPortableBundle(plainJson: String, passphrase: String): String {
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(("VOXLINE_SYNC_V1::$passphrase").toByteArray(Charsets.UTF_8))
        val key = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
        return "VOXLINE_SYNC_AES256GCM_V1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptPortableBundle(encoded: String, passphrase: String): String {
        require(encoded.startsWith("VOXLINE_SYNC_AES256GCM_V1:")) {
            "Unrecognized VoxLine sync bundle format."
        }
        val raw = Base64.decode(encoded.removePrefix("VOXLINE_SYNC_AES256GCM_V1:"), Base64.NO_WRAP)
        require(raw.size > GCM_IV_LENGTH) { "Corrupted sync bundle." }
        val iv = raw.copyOfRange(0, GCM_IV_LENGTH)
        val cipherBytes = raw.copyOfRange(GCM_IV_LENGTH, raw.size)
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(("VOXLINE_SYNC_V1::$passphrase").toByteArray(Charsets.UTF_8))
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    fun sha256Hex(data: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(data)
            .joinToString("") { "%02x".format(it) }
    }
}
