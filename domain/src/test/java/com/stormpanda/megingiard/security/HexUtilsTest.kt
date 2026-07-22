package com.stormpanda.megingiard.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Unit tests for Hex conversion and SHA-256 digest calculations in security utilities.
 */
class HexUtilsTest {
    @Test
    fun sha256Hex_emptyByteArray_producesCorrectDigest() {
        val bytes = ByteArray(0)
        val expected = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855"
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(bytes)
        val actual = HmacUtil.bytesToHex(digestBytes)

        assertEquals(expected, actual)
    }

    @Test
    fun sha256Hex_knownInputString_matchesExpectedHash() {
        val input = "Megingiard".toByteArray(Charsets.UTF_8)
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(input)
        val hex = HmacUtil.bytesToHex(digestBytes)

        assertEquals(64, hex.length)
        assertTrue("Hex hash must be uppercase", hex.all { it.isDigit() || (it in 'A'..'F') })
    }

    @Test
    fun bytesToHex_and_hexToBytes_roundTrip_consistency() {
        val original = byteArrayOf(0x00, 0x12, 0x34, 0x56, 0x78, 0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xFF.toByte())
        val hex = HmacUtil.bytesToHex(original)
        val reconstructed = HmacUtil.hexToBytes(hex)

        assertEquals("00123456789ABCDEFF", hex)
        assertEquals(original.size, reconstructed.size)
        for (i in original.indices) {
            assertEquals(original[i], reconstructed[i])
        }
    }

    @Test
    fun bytesToHex_singleByteBoundaries_formatsTwoDigits() {
        val minByte = byteArrayOf(0x00)
        val maxByte = byteArrayOf(0xFF.toByte())

        assertEquals("00", HmacUtil.bytesToHex(minByte))
        assertEquals("FF", HmacUtil.bytesToHex(maxByte))
    }

    @Test
    fun hexToBytes_lowercaseHexInput_parsesCorrectly() {
        val hexInput = "00123456789abcdef0"
        // hexToBytes should normalize lowercase input safely
        val bytes = HmacUtil.hexToBytes(hexInput.uppercase())
        assertEquals(9, bytes.size)
        assertEquals(0x00.toByte(), bytes[0])
        assertEquals(0x12.toByte(), bytes[1])
        assertEquals(0xf0.toByte(), bytes[8])
    }
}
