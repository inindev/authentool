package com.github.inindev.authentool

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Generates Time-Based One-Time Passwords (TOTP) per RFC 6238 using HMAC-SHA1.
 */
class TotpGenerator(
    private val secretBytes: ByteArray = generateRandomSecret(),
    private val timeStep: Long = 30L,
    private val digits: Int = 6
) {
    companion object {
        private const val SECRET_LENGTH = 20 // 160 bits, standard for HMAC-SHA1 TOTP
        private val secureRandom = SecureRandom()

        /** Generates a random secret for TOTP. */
        private fun generateRandomSecret(): ByteArray {
            val bytes = ByteArray(SECRET_LENGTH)
            secureRandom.nextBytes(bytes)
            return bytes
        }
    }

    /**
     * Generates a TOTP code based on the current time.
     * @return A [digits]-length string padded with leading zeros.
     */
    fun generateCode(): String {
        val counter = System.currentTimeMillis() / 1000 / timeStep
        val counterBytes = ByteBuffer.allocate(8).putLong(counter).array()

        val hmacSha1 = Mac.getInstance("HmacSHA1").apply {
            init(SecretKeySpec(secretBytes, "HmacSHA1"))
        }
        val hash = hmacSha1.doFinal(counterBytes)

        val offset = hash.last().toInt() and 0x0F
        val binary = (hash[offset].toInt() and 0x7F shl 24) or
                (hash[offset + 1].toInt() and 0xFF shl 16) or
                (hash[offset + 2].toInt() and 0xFF shl 8) or
                (hash[offset + 3].toInt() and 0xFF)
        val code = binary % 10.0.pow(digits).toInt()
        return code.toString().padStart(digits, '0')
    }
}
