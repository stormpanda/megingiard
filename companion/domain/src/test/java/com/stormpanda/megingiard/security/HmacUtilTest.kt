package com.stormpanda.megingiard.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HmacUtil].
 */
class HmacUtilTest {
    private fun assertHmac(
        key: ByteArray,
        data: ByteArray,
        expectedHex: String,
    ) {
        assertEquals(expectedHex, HmacUtil.computeHmacHex(key, data))
    }

    @Test
    fun hexToBytes_decodesCorrectly() {
        assertArrayEquals(byteArrayOf(0x00), HmacUtil.hexToBytes("00"))
        assertArrayEquals(byteArrayOf(0xFF.toByte()), HmacUtil.hexToBytes("FF"))
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0xAB.toByte(), 0xFF.toByte()), HmacUtil.hexToBytes("0102ABFF"))
        assertArrayEquals(byteArrayOf(0x0A, 0x0B, 0x0C), HmacUtil.hexToBytes("0a0b0c"))
    }

    @Test
    fun hexToBytes_defaultHmacKey_produces32Bytes() {
        val defaultKey = "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6E7F8A9B0C1D2E3F4A5B6C7D8E9F0A1B2"
        val bytes = HmacUtil.hexToBytes(defaultKey)
        assertEquals(32, bytes.size)
        assertEquals(0xA1.toByte(), bytes[0])
        assertEquals(0xB2.toByte(), bytes[31])
    }

    @Test
    fun hexToBytes_roundTrip_isLossless() {
        val original = ByteArray(32) { (it * 7 + 13).toByte() }
        assertArrayEquals(original, HmacUtil.hexToBytes(HmacUtil.bytesToHex(original)))
    }

    @Test
    fun bytesToHex_highBitBytes_emitTwoCharsPerByte() {
        assertEquals("007F80FF", HmacUtil.bytesToHex(byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())))
    }

    @Test
    fun hexToBytes_invalidInput_throwsException() {
        assertThrows(IllegalArgumentException::class.java) { HmacUtil.hexToBytes("ABC") }
        assertThrows(IllegalArgumentException::class.java) { HmacUtil.hexToBytes("GG") }
    }

    @Test
    fun computeHmacHex_rfc4231TestCases() {
        // Test Case 1
        assertHmac(
            ByteArray(20) { 0x0b.toByte() },
            "Hi There".toByteArray(Charsets.US_ASCII),
            "B0344C61D8DB38535CA8AFCEAF0BF12B881DC200C9833DA726E9376C2E32CFF7",
        )
        // Test Case 2
        assertHmac(
            "Jefe".toByteArray(Charsets.US_ASCII),
            "what do ya want for nothing?".toByteArray(Charsets.US_ASCII),
            "5BDCC146BF60754E6A042426089575C75A003F089D2739839DEC58B964EC3843",
        )
        // Test Case 3
        assertHmac(
            ByteArray(20) { 0xAA.toByte() },
            ByteArray(50) { 0xDD.toByte() },
            "773EA91E36800E46854DB8EBD09181A72959098B3EF8C122D9635514CED565FE",
        )
    }

    @Test
    fun computeHmacHex_outputIs64UppercaseHexChars() {
        val result = HmacUtil.computeHmacHex(ByteArray(32), ByteArray(16))
        assertEquals(64, result.length)
        assertTrue(result.matches(Regex("[0-9A-F]{64}")))
    }

    @Test
    fun computeHmacHex_differentInputs_produceDifferentOutputs() {
        val key = ByteArray(32) { (it + 1).toByte() }
        val nonce1 = ByteArray(16) { it.toByte() }
        val nonce2 = ByteArray(16) { (it + 1).toByte() }
        assertNotEquals(HmacUtil.computeHmacHex(key, nonce1), HmacUtil.computeHmacHex(key, nonce2))

        val key1 = ByteArray(32) { 0x00.toByte() }
        val key2 = ByteArray(32) { 0x01.toByte() }
        assertNotEquals(HmacUtil.computeHmacHex(key1, nonce1), HmacUtil.computeHmacHex(key2, nonce1))
    }

    @Test
    fun computeHmacHex_sameInputs_alwaysSameOutput() {
        val key = ByteArray(32) { (it * 3).toByte() }
        val nonce = ByteArray(16) { (it + 5).toByte() }
        assertEquals(HmacUtil.computeHmacHex(key, nonce), HmacUtil.computeHmacHex(key, nonce))
    }

    @Test
    fun constantTimeEqualsHex_verifications() {
        val mac = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF"
        val diffMac = "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDE0"
        assertTrue(HmacUtil.constantTimeEqualsHex(mac, mac))
        assertFalse(HmacUtil.constantTimeEqualsHex(diffMac, mac))
        assertFalse(HmacUtil.constantTimeEqualsHex("ABCD", "ABCDEF"))
    }
}
