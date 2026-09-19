package com.lelloman.androidoscopy.session

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object PairingCrypto {
    const val EXPORTER = "EXPORTER-Androidoscopy-v2"
    fun random() = ByteArray(32).also { SecureRandom().nextBytes(it) }
    fun hmac(secret: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(secret, "HmacSHA256")); doFinal(data)
    }
    fun commitment(secret: ByteArray, clientNonce: ByteArray) = hmac(secret, clientNonce)
    fun code(secret: ByteArray, clientNonce: ByteArray, serverNonce: ByteArray): String {
        val bytes = hmac(secret, clientNonce + serverNonce)
        val value = bytes.take(4).fold(0L) { n, b -> (n shl 8) or (b.toLong() and 255) }
        return (value % 100_000_000L).toString().padStart(8, '0')
    }
    fun proof(secret: ByteArray, exporter: ByteArray, session: String) =
        hmac(secret, "resume:$session:".toByteArray() + exporter)
    fun equal(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun unhex(value: String): ByteArray {
        require(value.length == 64 && value.matches(Regex("[a-f0-9]+")))
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
