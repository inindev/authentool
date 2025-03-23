//
// Copyright 2025, John Clark <inindev@gmail.com>. All rights reserved.
// Licensed under the Apache License, Version 2.0. See LICENSE file in the project root for full license information.
//
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.text.SimpleDateFormat
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.system.exitProcess

/** Decodes a Base32 string to bytes per RFC 4648. */
fun base32Decode(base32: String): ByteArray {
    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    var bits = 0
    var bitCount = 0
    val bytes = mutableListOf<Byte>()

    base32.uppercase().filter { it != '=' }.forEach { char ->
        val value = alphabet.indexOf(char)
        require(value != -1) { "invalid base32 character: $char" }

        bits = bits.shl(5) or value
        bitCount += 5

        if (bitCount >= 8) {
            bitCount -= 8
            bytes += (bits shr bitCount and 0xFF).toByte()
        }
    }
    return bytes.toByteArray()
}

/** Reads and validates seed.txt, returning decoded secret bytes. */
fun readSecretBytes(): ByteArray {
    val file = File("seed.txt")
    require(file.exists()) { "seed.txt not found in current directory" }

    val ownerReadWrite = setOf(OWNER_READ, OWNER_WRITE)
    val ownerReadOnly = setOf(OWNER_READ)

    val perms = Files.getPosixFilePermissions(file.toPath())
    require(perms == ownerReadWrite || perms == ownerReadOnly) {
        "seed.txt has insecure permissions (must be 600 or 400)"
    }

    val secret = file.readText().trim()
    require(secret.isNotEmpty()) { "seed.txt is empty" }
    return base32Decode(secret)
}

/** Generates a TOTP code per RFC 6238 with HMAC-SHA1. */
fun generateTotp(secretBytes: ByteArray): String {
    val timeStep = 30L
    val digits = 6
    val counter = System.currentTimeMillis() / 1000 / timeStep
    val bytes = ByteBuffer.allocate(8).putLong(counter).array()

    val hash = Mac.getInstance("HmacSHA1").run {
        init(SecretKeySpec(secretBytes, "HmacSHA1"))
        doFinal(bytes)
    }

    val offset = hash.last().toInt() and 0x0F
    val binary = (hash[offset].toInt() and 0x7F shl 24) or
            (hash[offset + 1].toInt() and 0xFF shl 16) or
            (hash[offset + 2].toInt() and 0xFF shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
    return (binary % 10.0.pow(digits).toInt()).toString().padStart(digits, '0')
}

fun main() {
    val secretBytes = try {
        readSecretBytes()
    } catch (e: Exception) {
        println("error: ${e.message}")
        exitProcess(1)
    }

    var running = true
    Runtime.getRuntime().addShutdownHook(thread(start = false) {
        running = false
        println("\nshutting down gracefully...")
    })

    println("\nauthenticator codes (ctrl+c to exit)")
    var lastLength = 0
    while (running) {
        val time = Date()
        val code = generateTotp(secretBytes)
        val timeStr = SimpleDateFormat("HH:mm:ss").format(time)
        val dots = ".".repeat(((30 - (System.currentTimeMillis() / 1000 % 30).toInt() + 2) / 3).coerceAtMost(10))
        val output = "$timeStr:  ${code.take(3)} ${code.drop(3)}  $dots"
        print("\r${output.padEnd(maxOf(output.length, lastLength), ' ')}")
        lastLength = output.length
        System.out.flush()
        Thread.sleep(1000L, 0).takeUnless { running }?.let { running = false }
    }
}
