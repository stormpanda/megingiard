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

    private val HEX_PATTERN = Regex("[0-9A-Fa-f]+")

    fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { b -> "%02X".format(b.toInt() and 0xFF) }

    /**
     * Decodes a hex string (case-insensitive, even number of chars) to a [ByteArray].
     * Each pair of characters maps to one byte; no `0x` prefix expected.
     */
    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must contain an even number of characters" }
        require(hex.isEmpty() || hex.matches(HEX_PATTERN)) { "Hex string contains non-hex characters" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
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
        return bytesToHex(hmac)
    }

    /**
     * Constant-time equality for fixed-length hex MAC strings.
     *
     * The comparison always scans both complete strings when their lengths match.
     * A length mismatch fails immediately because protocol parsers already enforce
     * exact message lengths before calling this helper.
     */
    fun constantTimeEqualsHex(actual: String, expected: String): Boolean {
        if (actual.length != expected.length) return false
        var diff = 0
        for (i in actual.indices) {
            diff = diff or (actual[i].code xor expected[i].code)
        }
        return diff == 0
    }
}
