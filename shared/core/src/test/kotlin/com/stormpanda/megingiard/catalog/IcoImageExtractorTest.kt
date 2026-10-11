package com.stormpanda.megingiard.catalog

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class IcoImageExtractorTest {
    private val pngMagic =
        byteArrayOf(
            0x89.toByte(),
            0x50.toByte(),
            0x4E.toByte(),
            0x47.toByte(),
            0x0D.toByte(),
            0x0A.toByte(),
            0x1A.toByte(),
            0x0A.toByte(),
        )

    @Test
    fun testIsIco_validHeader() {
        val validIcoHeader = byteArrayOf(0, 0, 1, 0, 1, 0)
        assertTrue(IcoImageExtractor.isIco(validIcoHeader))
    }

    @Test
    fun testIsIco_invalidHeader() {
        assertFalse(IcoImageExtractor.isIco(byteArrayOf()))
        assertFalse(IcoImageExtractor.isIco(byteArrayOf(0, 0, 0, 0, 1, 0)))
        assertFalse(IcoImageExtractor.isIco(byteArrayOf(0, 0, 2, 0, 1, 0))) // Cursor type (2)
        assertFalse(IcoImageExtractor.isIco(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()))) // PNG
    }

    @Test
    fun testExtractBestImage_picksPngWithHighestResolution() {
        // Construct synthetic ICO with 2 entries:
        // Entry 1: 16x16 dummy raw image (32 bytes) at offset 38
        // Entry 2: 0x00 (256x256) PNG image (64 bytes) at offset 70
        val out = ByteArrayOutputStream()

        // 1. Header (6 bytes): 2 images
        out.write(byteArrayOf(0, 0, 1, 0, 2, 0))

        // 2. Entry 1 (16 bytes): 16x16, bpp=32, size=32, offset=38
        out.write(byteArrayOf(16, 16, 0, 0, 1, 0, 32, 0))
        out.write(byteArrayOf(32, 0, 0, 0)) // size = 32
        out.write(byteArrayOf(38, 0, 0, 0)) // offset = 38

        // 3. Entry 2 (16 bytes): 0x00 (256x256), bpp=32, size=64, offset=70
        out.write(byteArrayOf(0, 0, 0, 0, 1, 0, 32, 0))
        out.write(byteArrayOf(64, 0, 0, 0)) // size = 64
        out.write(byteArrayOf(70, 0, 0, 0)) // offset = 70

        // 4. Payload 1 (32 bytes dummy)
        val dummyPayload = ByteArray(32) { it.toByte() }
        out.write(dummyPayload)

        // 5. Payload 2 (64 bytes: 8 bytes PNG magic + 56 bytes payload)
        val pngPayload = ByteArray(64)
        System.arraycopy(pngMagic, 0, pngPayload, 0, pngMagic.size)
        for (i in 8 until 64) pngPayload[i] = (i * 2).toByte()
        out.write(pngPayload)

        val icoBytes = out.toByteArray()
        assertTrue(IcoImageExtractor.isIco(icoBytes))

        val extracted = IcoImageExtractor.extractBestImage(icoBytes)
        assertNotNull(extracted)
        assertArrayEquals(pngPayload, extracted)
    }

    @Test
    fun testExtractBestImage_prefersPngOverNonPngEvenIfSmaller() {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 1, 0, 2, 0))

        // Entry 1: 0x00 (256x256) non-PNG (40 bytes at offset 38)
        out.write(byteArrayOf(0, 0, 0, 0, 1, 0, 32, 0))
        out.write(byteArrayOf(40, 0, 0, 0))
        out.write(byteArrayOf(38, 0, 0, 0))

        // Entry 2: 128x128 PNG (32 bytes at offset 78)
        out.write(byteArrayOf(128.toByte(), 128.toByte(), 0, 0, 1, 0, 32, 0))
        out.write(byteArrayOf(32, 0, 0, 0))
        out.write(byteArrayOf(78, 0, 0, 0))

        // Non-PNG payload
        out.write(ByteArray(40) { 0xFF.toByte() })

        // PNG payload
        val pngPayload = ByteArray(32)
        System.arraycopy(pngMagic, 0, pngPayload, 0, pngMagic.size)
        out.write(pngPayload)

        val extracted = IcoImageExtractor.extractBestImage(out.toByteArray())
        assertNotNull(extracted)
        assertArrayEquals(pngPayload, extracted)
    }

    @Test
    fun testExtractBestImage_invalidBoundsOrCountReturnsNull() {
        assertNull(IcoImageExtractor.extractBestImage(byteArrayOf(0, 0, 1, 0, 0, 0))) // count 0
        assertNull(IcoImageExtractor.extractBestImage(byteArrayOf(0, 0, 1, 0, 5, 0))) // count 5 but truncated file

        // File with invalid offset pointing beyond EOF
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0, 0, 1, 0, 1, 0))
        out.write(byteArrayOf(16, 16, 0, 0, 1, 0, 32, 0))
        out.write(byteArrayOf(50, 0, 0, 0)) // size = 50
        out.write(byteArrayOf(100, 0, 0, 0)) // offset = 100 (beyond file length of 22)
        assertNull(IcoImageExtractor.extractBestImage(out.toByteArray()))
    }
}
