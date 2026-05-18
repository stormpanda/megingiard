package com.stormpanda.megingiard.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HmacUtil].
 *
 * Two properties are tested:
 * 1. **Hex decoding** — [HmacUtil.hexToBytes] must decode ASCII hex strings to the
 *    exact byte values the C daemon parses from the `PRIVD_HMAC_KEY_HEX` constant.
 *    A mismatch here silently produces the wrong key on either side.
 *
 * 2. **HMAC-SHA256 correctness** — [HmacUtil.computeHmacHex] must produce the same
 *    digest as the RFC 4231 test vectors.  The C implementation in
 *    `megingiard_privd.c` and the Kotlin `PrivdClient` must agree on every bit;
 *    any divergence causes the Privd socket handshake to fail at boot.
 */
class HmacUtilTest {

    // -------------------------------------------------------------------------
    // hexToBytes — decoding
    // -------------------------------------------------------------------------

    @Test
    fun hexToBytes_zeroByte_decodesCorrectly() {
        assertArrayEquals(byteArrayOf(0x00), HmacUtil.hexToBytes("00"))
    }

    @Test
    fun hexToBytes_maxByte_decodesCorrectly() {
        // 0xFF as signed Kotlin byte is -1
        assertArrayEquals(byteArrayOf(0xFF.toByte()), HmacUtil.hexToBytes("FF"))
    }

    @Test
    fun hexToBytes_multiByte_decodesCorrectly() {
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0xAB.toByte(), 0xFF.toByte()),
            HmacUtil.hexToBytes("0102ABFF"),
        )
    }

    /** The default HMAC key (64 hex chars) must decode to exactly 32 bytes. */
    @Test
    fun hexToBytes_defaultHmacKey_produces32Bytes() {
        val defaultKey = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3F4A5B6C7D8E9F0A1B2"
        val bytes = HmacUtil.hexToBytes(defaultKey)
        assertEquals(32, bytes.size)
        // First byte: 0xA1
        assertEquals(0xA1.toByte(), bytes[0])
        // Last byte: 0xB2
        assertEquals(0xB2.toByte(), bytes[31])
    }

    /** Round-trip: bytes → hex → bytes must be lossless. */
    @Test
    fun hexToBytes_roundTrip_isLossless() {
        val original = ByteArray(32) { (it * 7 + 13).toByte() }
        val hex = HmacUtil.bytesToHex(original)
        assertArrayEquals(original, HmacUtil.hexToBytes(hex))
    }

    @Test
    fun bytesToHex_highBitBytes_emitTwoCharsPerByte() {
        val bytes = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())

        assertEquals("007F80FF", HmacUtil.bytesToHex(bytes))
    }

    @Test
    fun hexToBytes_lowercase_decodesCorrectly() {
        assertArrayEquals(
            byteArrayOf(0x0A, 0x0B, 0x0C),
            HmacUtil.hexToBytes("0a0b0c"),
        )
    }

    @Test
    fun hexToBytes_oddLength_throwsClearException() {
        assertThrows(IllegalArgumentException::class.java) {
            HmacUtil.hexToBytes("ABC")
        }
    }

    @Test
    fun hexToBytes_nonHexCharacter_throwsClearException() {
        assertThrows(IllegalArgumentException::class.java) {
            HmacUtil.hexToBytes("GG")
        }
    }

    // -------------------------------------------------------------------------
    // computeHmacHex — RFC 4231 known-answer test vectors
    //
    // These are the canonical HMAC-SHA256 test vectors.  The C daemon and the
    // Kotlin client both use standard HMAC-SHA256; if either side's implementation
    // deviates, one of these tests will fail and the handshake will be broken.
    // -------------------------------------------------------------------------

    /**
     * RFC 4231 Test Case 1.
     * Key  = 0x0b repeated 20 times
     * Data = "Hi There"
     * HMAC = b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7
     */
    @Test
    fun computeHmacHex_rfc4231TestCase1() {
        val key   = ByteArray(20) { 0x0b }
        val data  = "Hi There".toByteArray(Charsets.US_ASCII)
        val result = HmacUtil.computeHmacHex(key, data)
        assertEquals(
            "B0344C61D8DB38535CA8AFCEAF0BF12B881DC200C9833DA726E9376C2E32CFF7",
            result,
        )
    }

    /**
     * RFC 4231 Test Case 2.
     * Key  = "Jefe"
     * Data = "what do ya want for nothing?"
     * HMAC = 5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843
     */
    @Test
    fun computeHmacHex_rfc4231TestCase2() {
        val key   = "Jefe".toByteArray(Charsets.US_ASCII)
        val data  = "what do ya want for nothing?".toByteArray(Charsets.US_ASCII)
        val result = HmacUtil.computeHmacHex(key, data)
        assertEquals(
            "5BDCC146BF60754E6A042426089575C75A003F089D2739839DEC58B964EC3843",
            result,
        )
    }

    /**
     * RFC 4231 Test Case 3.
     * Key  = 0xaa repeated 20 times
     * Data = 0xdd repeated 50 times
     * HMAC = 773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe
     */
    @Test
    fun computeHmacHex_rfc4231TestCase3() {
        val key   = ByteArray(20) { 0xAA.toByte() }
        val data  = ByteArray(50) { 0xDD.toByte() }
        val result = HmacUtil.computeHmacHex(key, data)
        assertEquals(
            "773EA91E36800E46854DB8EBD09181A72959098B3EF8C122D9635514CED565FE",
            result,
        )
    }

    // -------------------------------------------------------------------------
    // computeHmacHex — output format
    // -------------------------------------------------------------------------

    /** HMAC-SHA256 is always 32 bytes → 64 uppercase hex chars. */
    @Test
    fun computeHmacHex_outputIs64UppercaseHexChars() {
        val key   = ByteArray(32)  // all zeros (32-byte production key size)
        val nonce = ByteArray(16)  // all zeros (16-byte production nonce size)
        val result = HmacUtil.computeHmacHex(key, nonce)
        assertEquals(64, result.length)
        assertTrue(
            "HMAC output must be uppercase hex",
            result.matches(Regex("[0-9A-F]{64}")),
        )
    }

    /**
     * Different nonces must produce different HMACs (non-trivial binding).
     * If this fails the nonce provides no replay protection.
     */
    @Test
    fun computeHmacHex_differentNonces_produceDifferentOutputs() {
        val key    = ByteArray(32) { (it + 1).toByte() }
        val nonce1 = ByteArray(16) { it.toByte() }
        val nonce2 = ByteArray(16) { (it + 1).toByte() }
        assertTrue(
            "Different nonces must produce different HMACs",
            HmacUtil.computeHmacHex(key, nonce1) != HmacUtil.computeHmacHex(key, nonce2),
        )
    }

    /**
     * Different keys must produce different HMACs.
     * If this fails, key secrecy provides no authentication.
     */
    @Test
    fun computeHmacHex_differentKeys_produceDifferentOutputs() {
        val nonce = ByteArray(16) { 0x42 }
        val key1  = ByteArray(32) { 0x00 }
        val key2  = ByteArray(32) { 0x01 }
        assertTrue(
            "Different keys must produce different HMACs",
            HmacUtil.computeHmacHex(key1, nonce) != HmacUtil.computeHmacHex(key2, nonce),
        )
    }

    /** HMAC must be deterministic for the same key+nonce pair. */
    @Test
    fun computeHmacHex_sameInputs_alwaysSameOutput() {
        val key   = ByteArray(32) { (it * 3).toByte() }
        val nonce = ByteArray(16) { (it + 5).toByte() }
        assertEquals(
            HmacUtil.computeHmacHex(key, nonce),
            HmacUtil.computeHmacHex(key, nonce),
        )
    }

    @Test
    fun constantTimeEqualsHex_sameStrings_returnsTrue() {
        val mac = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
        assertTrue(HmacUtil.constantTimeEqualsHex(mac, mac))
    }

    @Test
    fun constantTimeEqualsHex_differentStringsSameLength_returnsFalse() {
        val expected = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
        val actual = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDE0"
        assertTrue(
            "Different MACs must not compare equal",
            !HmacUtil.constantTimeEqualsHex(actual, expected),
        )
    }

    @Test
    fun constantTimeEqualsHex_differentLengths_returnsFalse() {
        assertTrue(
            "Different lengths must not compare equal",
            !HmacUtil.constantTimeEqualsHex("ABCD", "ABCDEF"),
        )
    }
}
