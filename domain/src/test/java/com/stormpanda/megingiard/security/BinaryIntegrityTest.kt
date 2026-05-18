package com.stormpanda.megingiard.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [BinaryIntegrity].
 *
 * Covers [BinaryIntegrity.sha256Hex] correctness against NIST FIPS 180-4 known-answer
 * vectors (computed independently via `sha256sum`).  If [sha256Hex] is broken, every
 * integrity check in the app silently accepts arbitrary bytes.
 *
 * [BinaryIntegrity.verify] is intentionally not tested here: it calls [AppLog]
 * which routes through `android.util.Log` \u2014 a stub that throws in the local JVM
 * test runtime.  Testing [verify] would require Robolectric.  The branching logic
 * inside [verify] (hash match / no-pin fail-closed / mismatch) is trivial; [sha256Hex]
 * is the only non-trivial part and is fully covered below.
 */
class BinaryIntegrityTest {

    // -------------------------------------------------------------------------
    // sha256Hex — NIST FIPS 180-4 known-answer test vectors
    // -------------------------------------------------------------------------

    @Test
    fun sha256Hex_emptyInput_returnsNistVector() {
        val hash = BinaryIntegrity.sha256Hex(ByteArray(0))
        assertEquals(
            "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
            hash,
        )
    }

    @Test
    fun sha256Hex_abcInput_returnsNistVector() {
        val hash = BinaryIntegrity.sha256Hex("abc".toByteArray(Charsets.US_ASCII))
        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            hash,
        )
    }

    /** NIST 448-bit message (triggers the SHA-256 padding block boundary). */
    @Test
    fun sha256Hex_448BitInput_returnsNistVector() {
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        val hash = BinaryIntegrity.sha256Hex(input.toByteArray(Charsets.US_ASCII))
        assertEquals(
            "248D6A61D20638B8E5C026930C3E6039A33CE45964FF2167F6ECEDD419DB06C1",
            hash,
        )
    }

    /** Output must always be 64 uppercase hex characters. */
    @Test
    fun sha256Hex_outputIs64UppercaseHexChars() {
        val hash = BinaryIntegrity.sha256Hex("any input".toByteArray())
        assertEquals(64, hash.length)
        assertTrue("Output must be uppercase hex", hash.matches(Regex("[0-9A-F]{64}")))
    }

    /** Same bytes always produce the same hash (determinism). */
    @Test
    fun sha256Hex_sameInputAlwaysSameOutput() {
        val input = "megingiard".toByteArray()
        assertEquals(
            BinaryIntegrity.sha256Hex(input),
            BinaryIntegrity.sha256Hex(input),
        )
    }

    /** A single bit flip must produce a completely different hash. */
    @Test
    fun sha256Hex_singleBitFlip_producesDistinctHash() {
        val original = ByteArray(64) { it.toByte() }
        val flipped  = original.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(
            "Single-bit flip must change the SHA-256 digest",
            BinaryIntegrity.sha256Hex(original) == BinaryIntegrity.sha256Hex(flipped),
        )
    }

    // -------------------------------------------------------------------------
    // verify() is intentionally not tested here.
    //
    // BinaryIntegrity.verify() calls AppLog.w() / AppLog.e(), which in turn call
    // android.util.Log — a class not available in the local JVM test runtime
    // (only mocked as a stub that throws).  Testing verify() would require
    // Robolectric or dependency injection into AppLog.  The sha256Hex tests
    // above cover the only non-trivial logic inside verify(); the branching logic
    // (hash match / no pin / tampered) is a one-liner that can be reviewed
    // directly in BinaryIntegrity.kt.
    // -------------------------------------------------------------------------
}
