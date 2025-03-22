package com.github.inindev.authentool

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Custom exception for Cryptographic operations.
 */
class CryptException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Defines a version of the encryption configuration.
 * Note: Changing SALT_SIZE, IV_SIZE, KEY_SIZE, or TAG_SIZE in CryptUtil requires a new id.
 */
data class Version(
    val id: Byte = 0x08,                     // unique identifier for this version
    val iterations: Int = 250_000,           // PBKDF2 iteration count
    val cipher: String = "AES/GCM/NoPadding" // cipher spec (documented, not dynamically applied)
)

/**
 * Encrypts this string using the provided password.
 */
fun String.encrypt(password: String): String = CryptUtil.encrypt(this, password)

/**
 * Decrypts this Base64-encoded encrypted string using the provided password.
 */
fun String.decrypt(password: String): String = CryptUtil.decrypt(this, password)

/**
 * Utility object for encrypting and decrypting data using AES-GCM.
 */
object CryptUtil {
    private const val SALT_SIZE = 16   // 16-byte salt per modern standards
    private const val IV_SIZE = 12     // 12-byte IV for AES-GCM
    private const val KEY_SIZE = 32    // 256-bit key for AES-256
    private const val TAG_SIZE = 128   // 128-bit authentication tag for GCM
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    // current version configuration
    private val currentVersion = Version()

    /**
     * Encrypts the plaintext using the provided password.
     */
    fun encrypt(plaintext: String, password: String): String {
        require(plaintext.isNotEmpty()) { "Plaintext cannot be empty" }
        require(password.isNotEmpty()) { "Password cannot be empty" }

        val salt = SecureRandom().generateSeed(SALT_SIZE)
        val iv = SecureRandom().generateSeed(IV_SIZE)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(currentVersion.cipher)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val output = byteArrayOf(currentVersion.id) + salt + iv + ciphertext
        return encoder.encodeToString(output)
    }

    /**
     * Decrypts the Base64-encoded encrypted text using the provided password.
     */
    fun decrypt(base64Encrypted: String, password: String): String {
        try {
            // sanitize input by removing leading/trailing whitespace and newlines
            val sanitizedBase64 = base64Encrypted.trim()

            // ensure proper base64 padding
            val base64Normalized = if (sanitizedBase64.length % 4 == 0) {
                sanitizedBase64
            } else {
                val missingPadding = 4 - (sanitizedBase64.length % 4)
                sanitizedBase64 + "=".repeat(missingPadding)
            }

            val encryptedBytes = decoder.decode(base64Normalized)
            if (encryptedBytes.size < (1 + SALT_SIZE + IV_SIZE)) {
                throw CryptException("Encrypted data is too short")
            }

            val versionId = encryptedBytes[0]
            if (versionId != currentVersion.id) {
                throw CryptException("Unsupported version ID: $versionId")
            }

            val salt = encryptedBytes.copyOfRange(1, 1 + SALT_SIZE)
            val iv = encryptedBytes.copyOfRange(1 + SALT_SIZE, 1 + SALT_SIZE + IV_SIZE)
            val ciphertext = encryptedBytes.copyOfRange(1 + SALT_SIZE + IV_SIZE, encryptedBytes.size)
            val key = deriveKey(password, salt)
            val cipher = Cipher.getInstance(currentVersion.cipher)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE, iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            return String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            throw CryptException("Decryption failed: ${e.message}", e)
        }
    }

    /**
     * Derives a key from the password and salt using PBKDF2.
     */
    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            currentVersion.iterations,
            KEY_SIZE * 8
        )
        return factory.generateSecret(spec).encoded
    }
}
