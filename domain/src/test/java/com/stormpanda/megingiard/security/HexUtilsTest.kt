package com.stormpanda.megingiard.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HexUtilsTest {
    @Test
    fun testBytesToHexAndBackRoundTrip() {
        val original = byteArrayOf(0x00, 0x0F, 0x10, 0xAF.toByte(), 0xFF.toByte())
        val hex = HmacUtil.bytesToHex(original)
        assertEquals("000F10AFFF", hex)

        val decoded = HmacUtil.hexToBytes(hex)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun testSha256HexCalculation() {
        val input = "megingiard".toByteArray(Charsets.UTF_8)
        val hashHex = HmacUtil.sha256Hex(input)
        assertEquals(64, hashHex.length)
        assertEquals(hashHex.uppercase(), hashHex)
    }
}
