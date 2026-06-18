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

    @Test
    fun `projectCutoutCoordinates maps touch to primary display crop`() {
        val r = projectCutoutCoordinates(
            touchX = 150f, touchY = 150f,
            destLeft = 100f, destTop = 100f,
            destWidth = 100f, destHeight = 100f,
            srcX = 0.1f, srcY = 0.1f,
            srcWidth = 0.8f, srcHeight = 0.8f
        )
        assertNotNull(r)
        assertEquals(0.5f, r!!.first, EPS)
        assertEquals(0.5f, r.second, EPS)
    }

    @Test
    fun `projectCutoutCoordinates outside dest bounds returns null when not clamped`() {
        val r = projectCutoutCoordinates(
            touchX = 50f, touchY = 150f,
            destLeft = 100f, destTop = 100f,
            destWidth = 100f, destHeight = 100f,
            srcX = 0.1f, srcY = 0.1f,
            srcWidth = 0.8f, srcHeight = 0.8f,
            clampToEdge = false
        )
        assertNull(r)
    }

    @Test
    fun `projectCutoutCoordinates outside dest bounds clamps to edge when clampToEdge is true`() {
        val r = projectCutoutCoordinates(
            touchX = 50f, touchY = 150f,
            destLeft = 100f, destTop = 100f,
            destWidth = 100f, destHeight = 100f,
            srcX = 0.1f, srcY = 0.1f,
            srcWidth = 0.8f, srcHeight = 0.8f,
            clampToEdge = true
        )
        assertNotNull(r)
        assertEquals(0.1f, r!!.first, EPS)
        assertEquals(0.5f, r.second, EPS)
    }

    @Test
    fun `clampCutoutDrag allows drag without overlap`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.1f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f),
            ScreenCutout("2", destX = 0.5f, destY = 0.5f, destWidth = 0.3f, destHeight = 0.3f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)
        )
        val (x, y) = clampCutoutDrag(
            cutoutId = "1",
            originalX = 0.1f, originalY = 0.1f,
            targetX = 0.15f, targetY = 0.15f,
            width = 0.3f, height = 0.3f,
            allCutouts = allCutouts
        )
        assertEquals(0.15f, x, EPS)
        assertEquals(0.15f, y, EPS)
    }

    @Test
    fun `clampCutoutDrag prevents overlap and allows sliding`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.1f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f),
            ScreenCutout("2", destX = 0.45f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)
        )
        val (x, y) = clampCutoutDrag(
            cutoutId = "1",
            originalX = 0.1f, originalY = 0.1f,
            targetX = 0.25f, targetY = 0.15f,
            width = 0.3f, height = 0.3f,
            allCutouts = allCutouts
        )
        assertEquals(0.15f, x, EPS)
        assertEquals(0.15f, y, EPS)
    }

    @Test
    fun `clampCutoutResize allows clear resize and clamps on collision`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.1f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f),
            ScreenCutout("2", destX = 0.5f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)
        )
        val geomClear = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.BOTTOM_RIGHT,
            originalX = 0.1f, originalY = 0.1f,
            originalWidth = 0.3f, originalHeight = 0.3f,
            targetX = 0.1f, targetY = 0.1f,
            targetWidth = 0.35f, targetHeight = 0.35f,
            allCutouts = allCutouts
        )
        assertEquals(0.35f, geomClear.w, EPS)
        assertEquals(0.35f, geomClear.h, EPS)

        val geomCollision = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.BOTTOM_RIGHT,
            originalX = 0.1f, originalY = 0.1f,
            originalWidth = 0.3f, originalHeight = 0.3f,
            targetX = 0.1f, targetY = 0.1f,
            targetWidth = 0.45f, targetHeight = 0.3f,
            allCutouts = allCutouts
        )
        assertEquals(0.4f, geomCollision.w, EPS)
    }

    @Test
    fun `clampCutoutResize clamps vertical scaling of lower cutout against upper cutout`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.3f, destY = 0.4f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f), // Lower cutout
            ScreenCutout("2", destX = 0.3f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)  // Upper cutout (bottom is 0.3)
        )
        val geom = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.TOP_LEFT,
            originalX = 0.3f, originalY = 0.4f,
            originalWidth = 0.2f, originalHeight = 0.2f,
            targetX = 0.3f, targetY = 0.2f, // Drag top edge up past upper cutout's bottom
            targetWidth = 0.2f, targetHeight = 0.4f,
            allCutouts = allCutouts
        )
        assertEquals(0.3f, geom.y, EPS) // Should clamp to upper cutout's bottom (0.3)
        assertEquals(0.3f, geom.h, EPS) // Height should be 0.6 (originalBottom) - 0.3 = 0.3
    }

    @Test
    fun `clampCutoutResize clamps vertical scaling of lower cutout with slight horizontal drift`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.3f, destY = 0.4f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f), // Lower cutout
            ScreenCutout("2", destX = 0.3f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)  // Upper cutout (bottom is 0.3)
        )
        val geom = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.TOP_LEFT,
            originalX = 0.3f, originalY = 0.4f,
            originalWidth = 0.2f, originalHeight = 0.2f,
            targetX = 0.29f, targetY = 0.2f, // Drag top edge up past bottom (0.3) and left (0.3)
            targetWidth = 0.21f, targetHeight = 0.4f,
            allCutouts = allCutouts
        )
        assertEquals(0.3f, geom.y, EPS)
        assertEquals(0.3f, geom.h, EPS)
    }
}

