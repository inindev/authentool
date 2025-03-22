package com.github.inindev.authentool

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * Generates Time-Based One-Time Passwords (TOTP) per RFC 6238 using HMAC-SHA1.
 * Requires a valid seed provided by the caller.
 */
class TotpGenerator(
    private val seedBytes: ByteArray,
    private val timeStep: Long = TIME_STEP,
    private val digits: Int = 6
) {
    companion object {
        const val TIME_STEP = 30L
        // Base32 alphabet per RFC 4648
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        /**
         * Decodes a Base32-encoded string (RFC 4648) into a byte array for TOTP seeds.
         * @param base32 The Base32-encoded string to decode.
         * @return The decoded byte array.
         */
        fun decodeBase32(base32: String): ByteArray {
            val base32Filtered = base32.uppercase().filter { it in ALPHABET }
            if (base32Filtered.isEmpty()) throw IllegalArgumentException("Base32 string is empty after cleaning")
            val validLengths = setOf(2, 4, 5, 7) + (8..base32Filtered.length step 8).toSet()
            if (base32Filtered.length !in validLengths) {
                throw IllegalArgumentException(
                    "Base32 string length (${base32Filtered.length}) is invalid. " +
                            "Expected lengths: 2, 4, 5, 7, or multiples of 8."
                )
            }
            val bits = base32Filtered.map { ALPHABET.indexOf(it).toString(2).padStart(5, '0') }.joinToString("")
            val byteLength = (base32Filtered.length * 5) / 8
            val usableBits = bits.take(byteLength * 8)
            return (0 until byteLength).map {
                usableBits.substring(it * 8, (it + 1) * 8).toInt(2).toByte()
            }.toByteArray()
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
            init(SecretKeySpec(seedBytes, "HmacSHA1"))
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
