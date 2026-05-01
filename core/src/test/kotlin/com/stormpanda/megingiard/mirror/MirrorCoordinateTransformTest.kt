package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-3f

/**
 * Tests for [projectCoordinates].
 *
 * Reference scenario: AYN Thor primary display 1080×1920, secondary display 1920×1080
 * Compose surface, letterboxed mirrored content of 1920×1080 (sw=screenW, sh=screenH).
 */
class MirrorCoordinateTransformTest {

    @Test
    fun `center of screen at scale 1 maps to content center`() {
        val r = projectCoordinates(
            touchX = 960f, touchY = 540f,
            screenW = 1920f, screenH = 1080f,
            sw = 1920f, sh = 1080f,
            scale = 1f, offsetX = 0f, offsetY = 0f,
        )
        assertNotNull(r)
        assertEquals(0.5f, r!!.first, EPS)
        assertEquals(0.5f, r.second, EPS)
    }

    @Test
    fun `top-left corner at scale 1 maps to (0,0)`() {
        val r = projectCoordinates(
            touchX = 0f, touchY = 0f,
            screenW = 1920f, screenH = 1080f,
            sw = 1920f, sh = 1080f,
            scale = 1f, offsetX = 0f, offsetY = 0f,
        )
        assertNotNull(r)
        assertEquals(0f, r!!.first, EPS)
        assertEquals(0f, r.second, EPS)
    }

    @Test
    fun `bottom-right corner at scale 1 maps near (1,1)`() {
        // 1f - 1px to stay within the 0..1 range.
        val r = projectCoordinates(
            touchX = 1919f, touchY = 1079f,
            screenW = 1920f, screenH = 1080f,
            sw = 1920f, sh = 1080f,
            scale = 1f, offsetX = 0f, offsetY = 0f,
        )
        assertNotNull(r)
        assertTrue(r!!.first > 0.999f && r.first <= 1f)
        assertTrue(r.second > 0.999f && r.second <= 1f)
    }

    @Test
    fun `2x zoom centered keeps screen center mapped to content center`() {
        val r = projectCoordinates(
            touchX = 960f, touchY = 540f,
            screenW = 1920f, screenH = 1080f,
            sw = 1920f, sh = 1080f,
            scale = 2f, offsetX = 0f, offsetY = 0f,
        )
        assertNotNull(r)
        assertEquals(0.5f, r!!.first, EPS)
        assertEquals(0.5f, r.second, EPS)
    }

    @Test
    fun `2x zoom narrows visible content - screen left edge maps to content quarter point`() {
        // At 2× zoom the visible content X range is 0.25..0.75 of the source.
        val r = projectCoordinates(
            touchX = 0f, touchY = 540f,
            screenW = 1920f, screenH = 1080f,
            sw = 1920f, sh = 1080f,
            scale = 2f, offsetX = 0f, offsetY = 0f,
        )
        assertNotNull(r)
        assertEquals(0.25f, r!!.first, EPS)
        assertEquals(0.5f, r.second, EPS)
    }

    @Test
    fun `pan offset shifts mapped content`() {
        // offsetX = +scale * sw/2 should move the screen center to the content's left edge.
        // svX = (touchX - screenCenter - offsetX)/scale + svCenter
        //     = (960 - 960 - 960) / 1 + 960 = 0  → nx = 0
        val r = projectCoordinates(
            touchX = 960f, touchY = 540f,
            screenW = 1920f, screenH = 1080f,
            sw = 1920f, sh = 1080f,
            scale = 1f, offsetX = 960f, offsetY = 0f,
        )
        assertNotNull(r)
        assertEquals(0f, r!!.first, EPS)
        assertEquals(0.5f, r.second, EPS)
    }

    @Test
    fun `out-of-bounds touch returns null`() {
        // Touch beyond the right edge with no zoom/pan ⇒ nx > 1 ⇒ null.
        val r = projectCoordinates(
            touchX = 5000f, touchY = 540f,
            screenW = 1920f, screenH = 1080f,
            sw = 1920f, sh = 1080f,
            scale = 1f, offsetX = 0f, offsetY = 0f,
        )
        assertNull(r)
    }

    @Test
    fun `letterboxed content - touch in letterbox bar returns null`() {
        // Source is 1920×1080 fitted into a 1920×1200 surface (taller). Letterbox bars at top/bottom.
        // Content area: sw=1920, sh=1080 centered in screenW=1920, screenH=1200.
        // Touch at y=10 (in top letterbox) ⇒ svY < 0 ⇒ ny < 0 ⇒ null.
        val r = projectCoordinates(
            touchX = 960f, touchY = 10f,
            screenW = 1920f, screenH = 1200f,
            sw = 1920f, sh = 1080f,
            scale = 1f, offsetX = 0f, offsetY = 0f,
        )
        assertNull(r)
    }

    @Test
    fun `letterboxed content - touch on visible content maps correctly`() {
        // Same scenario, but touch at the content center (which is also the screen center).
        val r = projectCoordinates(
            touchX = 960f, touchY = 600f,
            screenW = 1920f, screenH = 1200f,
            sw = 1920f, sh = 1080f,
            scale = 1f, offsetX = 0f, offsetY = 0f,
        )
        assertNotNull(r)
        assertEquals(0.5f, r!!.first, EPS)
        assertEquals(0.5f, r.second, EPS)
    }

    @Test
    fun `degenerate inputs return null`() {
        assertNull(
            projectCoordinates(0f, 0f, 0f, 1080f, 1920f, 1080f, 1f, 0f, 0f),
        )
        assertNull(
            projectCoordinates(0f, 0f, 1920f, 1080f, 1920f, 1080f, 0f, 0f, 0f),
        )
        assertNull(
            projectCoordinates(0f, 0f, 1920f, 1080f, 0f, 1080f, 1f, 0f, 0f),
        )
    }
}
