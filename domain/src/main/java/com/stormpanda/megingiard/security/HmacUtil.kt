package com.stormpanda.megingiard.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-JVM HMAC-SHA256 helpers shared by [BinaryIntegrity] tests and
 * [com.stormpanda.megingiard.privd.PrivdClient].
 *
 * Kept in the `security` package so it can be tested from `:domain:test`
 * without pulling in any Android classes.
 */
internal object HmacUtil {

    /**
     * Decodes a hex string (case-insensitive, even number of chars) to a [ByteArray].
     * Each pair of characters maps to one byte; no `0x` prefix expected.
     */
    fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

    /**
     * Computes HMAC-SHA256([keyBytes], [nonceBytes]) and returns the result as a
     * 64-character uppercase hex string.
     *
     * This is the exact computation the Privd socket handshake performs:
     * the client receives a 16-byte nonce from the daemon, computes the HMAC
     * with the pre-shared key, and sends `AUTH <64-hex>\n`.
     */
    fun computeHmacHex(keyBytes: ByteArray, nonceBytes: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val hmac = mac.doFinal(nonceBytes)
        return hmac.joinToString("") { b -> "%02X".format(b) }
    }
}
