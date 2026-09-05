package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutPlacementHelperTest {
    @Test
    fun `findAvailableSlot on empty layout returns base 0_3x0_3 slot at top left`() {
        val slot = CutoutPlacementHelper.findAvailableSlot(emptyList())
        assertNotNull(slot)
        assertEquals(0f, slot!!.destX, 0.001f)
        assertEquals(0f, slot.destY, 0.001f)
        assertEquals(0.3f, slot.destWidth, 0.001f)
        assertEquals(0.3f, slot.destHeight, 0.001f)
    }

    @Test
    fun `findAvailableSlot with cutout at (0, 0) finds adjacent non-overlapping slot at base size`() {
        val cutout =
            ScreenCutout(
                id = "cutout-1",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 0.3f,
                destHeight = 0.3f,
            )
        val slot = CutoutPlacementHelper.findAvailableSlot(listOf(cutout))
        assertNotNull(slot)
        assertEquals(0.3f, slot!!.destX, 0.001f)
        assertEquals(0f, slot.destY, 0.001f)
        assertEquals(0.3f, slot.destWidth, 0.001f)
        assertEquals(0.3f, slot.destHeight, 0.001f)
    }

    @Test
    fun `findAvailableSlot scales down candidate size when standard base size does not fit`() {
        // Create cutouts that fill the canvas except for a small 0.2 x 0.2 pocket
        // Let's create two large cutouts that leave only a 0.20 width strip
        val leftCutout =
            ScreenCutout(
                id = "c1",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 0.8f,
                destHeight = 1.0f,
            )
        val rightCutout =
            ScreenCutout(
                id = "c2",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.8f,
                destY = 0.2f,
                destWidth = 0.2f,
                destHeight = 0.8f,
            )

        // The remaining gap is from x=0.8..1.0, y=0.0..0.2 (0.2 x 0.2)
        // Standard 0.30 x 0.30 will not fit, but scaled down <= 0.20 will fit!
        val slot = CutoutPlacementHelper.findAvailableSlot(listOf(leftCutout, rightCutout))
        assertNotNull(slot)
        assertTrue("Candidate width should be scaled down to <= 0.20", slot!!.destWidth <= 0.201f)
        assertTrue("Candidate height should be scaled down to <= 0.20", slot.destHeight <= 0.201f)
        assertEquals(0.8f, slot.destX, 0.001f)
        assertEquals(0.0f, slot.destY, 0.001f)
    }

    @Test
    fun `findAvailableSlot returns null when canvas is completely occupied`() {
        val fullscreenCutout =
            ScreenCutout(
                id = "full",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
            )
        val slot = CutoutPlacementHelper.findAvailableSlot(listOf(fullscreenCutout))
        assertNull("Should return null when no space is available even at 50% scale", slot)
    }

    @Test
    fun `findAvailableSlot supports placing more than 10 cutouts without hardcoded limits`() {
        var cutouts = emptyList<ScreenCutout>()
        for (i in 0 until 16) {
            val slot = CutoutPlacementHelper.findAvailableSlot(cutouts, baseWidth = 0.2f, baseHeight = 0.2f)
            assertNotNull("Should find slot for cutout index $i", slot)
            val newCutout =
                ScreenCutout(
                    id = "cutout-$i",
                    srcX = 0f,
                    srcY = 0f,
                    srcWidth = 1f,
                    srcHeight = 1f,
                    destX = slot!!.destX,
                    destY = slot.destY,
                    destWidth = slot.destWidth,
                    destHeight = slot.destHeight,
                )
            cutouts = cutouts + newCutout
        }
        assertEquals(16, cutouts.size)
    }
}
