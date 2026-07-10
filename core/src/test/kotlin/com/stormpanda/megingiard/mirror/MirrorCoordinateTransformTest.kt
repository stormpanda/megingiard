package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import com.stormpanda.megingiard.macropad.PadLayout

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
    fun `clampCutoutDrag selects closer candidate when sliding along either axis is valid`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.35f, destY = 0.0f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f),
            ScreenCutout("2", destX = 0.1f, destY = 0.3f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)
        )
        val (x, y) = clampCutoutDrag(
            cutoutId = "1",
            originalX = 0.35f, originalY = 0.0f,
            targetX = 0.29f, targetY = 0.36f,
            width = 0.2f, height = 0.2f,
            allCutouts = allCutouts
        )
        // Candidate 1 (slide X, keep target Y): (0.3f, 0.36f) -> distance to target is 0.01
        // Candidate 2 (keep target X, slide Y): (0.29f, 0.1f) -> distance to target is 0.26
        // Candidate 1 should be selected.
        assertEquals(0.3f, x, EPS)
        assertEquals(0.36f, y, EPS)
    }

    @Test
    fun `clampCutoutDrag handles drag past obstacle without snapping back to starting position`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.51f, destY = 0.3f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f),
            ScreenCutout("2", destX = 0.3f, destY = 0.4f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f),
            ScreenCutout("3", destX = 0.3f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)
        )
        val (x, y) = clampCutoutDrag(
            cutoutId = "1",
            originalX = 0.51f, originalY = 0.3f,
            targetX = 0.49f, targetY = 0.3f,
            width = 0.2f, height = 0.2f,
            allCutouts = allCutouts
        )
        assertEquals(0.5f, x, EPS)
        assertEquals(0.3f, y, EPS)
    }

    @Test
    fun `adjustDestSizeToAspectRatio fits destination size correctly`() {
        val (w, h) = adjustDestSizeToAspectRatio(
            destX = 0f, destY = 0f,
            destWidth = 0.3f, destHeight = 0.3f,
            cropRatio = 16f / 9f,
            screenW = 1280f, screenH = 960f
        )
        assertEquals(0.3f, w, EPS)
        assertEquals(0.225f, h, EPS)
    }

    @Test
    fun `clampCutoutResize maintains aspect ratio during collision`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.1f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f),
            ScreenCutout("2", destX = 0.45f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)
        )
        val geom = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.BOTTOM_RIGHT,
            originalX = 0.1f, originalY = 0.1f,
            originalWidth = 0.2f, originalHeight = 0.2f,
            targetX = 0.1f, targetY = 0.1f,
            targetWidth = 0.4f, targetHeight = 0.4f,
            allCutouts = allCutouts,
            keepAspectRatio = true,
            cropRatio = 1f,
            screenW = 1000f, screenH = 1000f
        )
        assertEquals(0.35f, geom.w, 0.002f)
        assertEquals(0.35f, geom.h, 0.002f)
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

    @Test
    fun `clampCutoutResize clamps horizontal scaling when target is taller and vertically overlaps`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.5f, destY = 0.2f, destWidth = 0.2f, destHeight = 0.1f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f), // Right cutout (b)
            ScreenCutout("2", destX = 0.2f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.3f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)  // Left cutout (a), right edge is 0.4, Y range [0.1, 0.4]
        )
        val geom = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.BOTTOM_LEFT,
            originalX = 0.5f, originalY = 0.2f,
            originalWidth = 0.2f, originalHeight = 0.1f,
            targetX = 0.38f, targetY = 0.2f,
            targetWidth = 0.32f, targetHeight = 0.15f,
            allCutouts = allCutouts
        )
        assertEquals(0.4f, geom.x, EPS)
        assertEquals(0.15f, geom.h, EPS)
    }

    @Test
    fun `clampCutoutResize clamps vertical scaling when target is wider and horizontally overlaps`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.2f, destY = 0.4f, destWidth = 0.1f, destHeight = 0.1f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f), // Lower cutout (b)
            ScreenCutout("2", destX = 0.1f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)  // Upper cutout (a), bottom is 0.3, X range [0.1, 0.4]
        )
        val geom = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.TOP_LEFT,
            originalX = 0.2f, originalY = 0.4f,
            originalWidth = 0.1f, originalHeight = 0.1f,
            targetX = 0.05f, targetY = 0.25f,
            targetWidth = 0.25f, targetHeight = 0.25f,
            allCutouts = allCutouts
        )
        assertEquals(0.3f, geom.y, EPS) // clamped to bottom edge of a
        assertEquals(0.25f, geom.w, EPS) // width remains 0.25f (x is 0.05f, not clamped)
    }

    @Test
    fun `clampCutoutResize clamps top-left drag past corner without snapping back`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.35f, destY = 0.42f, destWidth = 0.35f, destHeight = 0.23f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f), // Right cutout (b) with prev state
            ScreenCutout("2", destX = 0.2f, destY = 0.2f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)  // Left cutout (a)
        )
        val geom = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.TOP_LEFT,
            originalX = 0.5f, originalY = 0.45f,
            originalWidth = 0.2f, originalHeight = 0.2f,
            targetX = 0.35f, targetY = 0.34f,
            targetWidth = 0.35f, targetHeight = 0.31f,
            allCutouts = allCutouts
        )
        assertEquals(0.35f, geom.x, EPS)
        assertEquals(0.4f, geom.y, EPS)
    }

    @Test
    fun `clampCutoutResize clamps top-left drag past corner without snapping back when starting adjacent`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.35f, destY = 0.42f, destWidth = 0.35f, destHeight = 0.23f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f), // prev state
            ScreenCutout("2", destX = 0.2f, destY = 0.2f, destWidth = 0.2f, destHeight = 0.2f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)  // Left cutout
        )
        val geom = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.TOP_LEFT,
            originalX = 0.40f, originalY = 0.45f, // Starts adjacent horizontally
            originalWidth = 0.20f, originalHeight = 0.20f,
            targetX = 0.35f, targetY = 0.34f,
            targetWidth = 0.25f, targetHeight = 0.31f,
            allCutouts = allCutouts
        )
        assertEquals(0.35f, geom.x, EPS)
        assertEquals(0.40f, geom.y, EPS)
    }

    @Test
    fun `clampCutoutResize clamps top-right drag against multiple cutouts correctly`() {
        val allCutouts = listOf(
            ScreenCutout("1", destX = 0.35f, destY = 0.42f, destWidth = 0.30f, destHeight = 0.23f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f), // prev state of C (right is 0.65)
            ScreenCutout("2", destX = 0.30f, destY = 0.20f, destWidth = 0.30f, destHeight = 0.20f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f), // A (above, bottom is 0.40)
            ScreenCutout("3", destX = 0.65f, destY = 0.30f, destWidth = 0.20f, destHeight = 0.20f, srcX=0f, srcY=0f, srcWidth=1f, srcHeight=1f)  // B (right, left is 0.65)
        )
        val geom = clampCutoutResize(
            cutoutId = "1",
            handle = ResizeHandle.TOP_RIGHT,
            originalX = 0.35f, originalY = 0.42f,
            originalWidth = 0.30f, originalHeight = 0.23f,
            targetX = 0.35f, targetY = 0.30f,
            targetWidth = 0.32f, targetHeight = 0.35f, // tr = 0.67, ty = 0.30
            allCutouts = allCutouts
        )
        assertEquals(0.35f, geom.x, EPS)
        assertEquals(0.40f, geom.y, EPS) // clamped to A's bottom (0.40)
        assertEquals(0.30f, geom.w, EPS) // clamped to B's left (0.65), width is 0.65 - 0.35 = 0.30
    }

    @Test
    fun `ScreenCutout serialization round-trip preserves motionSmoothing`() {
        val original = ScreenCutout(
            id = "c-test-smooth",
            name = "Test Smooth",
            srcX = 0f, srcY = 0f, srcWidth = 1f, srcHeight = 1f,
            destX = 0f, destY = 0f, destWidth = 1f, destHeight = 1f,
            motionSmoothing = true
        )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ScreenCutout>(jsonString)
        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertTrue(decoded.motionSmoothing)
    }

    @Test
    fun `ScreenCutout deserialization of legacy JSON defaults motionSmoothing to false`() {
        val legacyJson = """
            {
                "id": "c-legacy",
                "name": "Legacy Cutout",
                "srcX": 0.0,
                "srcY": 0.0,
                "srcWidth": 1.0,
                "srcHeight": 1.0,
                "destX": 0.0,
                "destY": 0.0,
                "destWidth": 1.0,
                "destHeight": 1.0
            }
        """.trimIndent()
        val decoded = Json.decodeFromString<ScreenCutout>(legacyJson)
        assertEquals("c-legacy", decoded.id)
        assertTrue(!decoded.motionSmoothing)
    }

    @Test
    fun `ScreenCutout serialization round-trip preserves shape`() {
        val original = ScreenCutout(
            id = "c-test-shape",
            name = "Test Shape",
            srcX = 0f, srcY = 0f, srcWidth = 1f, srcHeight = 1f,
            destX = 0f, destY = 0f, destWidth = 1f, destHeight = 1f,
            shape = CutoutShape.CIRCLE
        )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ScreenCutout>(jsonString)
        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(CutoutShape.CIRCLE, decoded.shape)
    }

    @Test
    fun `ScreenCutout deserialization of legacy JSON defaults shape to RECTANGLE`() {
        val legacyJson = """
            {
                "id": "c-legacy",
                "name": "Legacy Cutout",
                "srcX": 0.0,
                "srcY": 0.0,
                "srcWidth": 1.0,
                "srcHeight": 1.0,
                "destX": 0.0,
                "destY": 0.0,
                "destWidth": 1.0,
                "destHeight": 1.0
            }
        """.trimIndent()
        val decoded = Json.decodeFromString<ScreenCutout>(legacyJson)
        assertEquals("c-legacy", decoded.id)
        assertEquals(CutoutShape.RECTANGLE, decoded.shape)
    }

    @Test
    fun `ScreenCutout serialization round-trip preserves aspectRatioMode`() {
        val original = ScreenCutout(
            id = "c-test-aspect",
            name = "Test Aspect",
            srcX = 0f, srcY = 0f, srcWidth = 1f, srcHeight = 1f,
            destX = 0f, destY = 0f, destWidth = 1f, destHeight = 1f,
            aspectRatioMode = AspectRatioMode.BOTTOM
        )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ScreenCutout>(jsonString)
        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(AspectRatioMode.BOTTOM, decoded.aspectRatioMode)
    }

    @Test
    fun `ScreenCutout deserialization of legacy JSON migrates keepAspectRatio to aspectRatioMode`() {
        val legacyJsonTrue = """
            {
                "id": "c-legacy-true",
                "name": "Legacy Cutout True",
                "srcX": 0.0,
                "srcY": 0.0,
                "srcWidth": 1.0,
                "srcHeight": 1.0,
                "destX": 0.0,
                "destY": 0.0,
                "destWidth": 1.0,
                "destHeight": 1.0,
                "keepAspectRatio": true
            }
        """.trimIndent()
        val decodedTrue = Json.decodeFromString<ScreenCutout>(legacyJsonTrue)
        assertEquals("c-legacy-true", decodedTrue.id)
        assertEquals(AspectRatioMode.TOP, decodedTrue.aspectRatioMode)

        val legacyJsonFalse = """
            {
                "id": "c-legacy-false",
                "name": "Legacy Cutout False",
                "srcX": 0.0,
                "srcY": 0.0,
                "srcWidth": 1.0,
                "srcHeight": 1.0,
                "destX": 0.0,
                "destY": 0.0,
                "destWidth": 1.0,
                "destHeight": 1.0,
                "keepAspectRatio": false
            }
        """.trimIndent()
        val decodedFalse = Json.decodeFromString<ScreenCutout>(legacyJsonFalse)
        assertEquals("c-legacy-false", decodedFalse.id)
        assertEquals(AspectRatioMode.BOTTOM, decodedFalse.aspectRatioMode)
    }

    @Test
    fun `PadLayout serialization round-trip preserves mirrorMaxFps and mirrorSmoothingStrength`() {
        val original = PadLayout(
            id = "layout-test",
            name = "Test Layout",
            mirrorMaxFps = 45,
            mirrorSmoothingStrength = 80,
            backgroundImageDim = 0.65f
        )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<PadLayout>(jsonString)
        assertEquals(45, decoded.mirrorMaxFps)
        assertEquals(80, decoded.mirrorSmoothingStrength)
        assertEquals(0.65f, decoded.backgroundImageDim)
    }

    @Test
    fun `PadLayout deserialization of legacy JSON defaults mirrorMaxFps and mirrorSmoothingStrength`() {
        val legacyJson = """
            {
                "id": "layout-legacy",
                "name": "Legacy Layout"
            }
        """.trimIndent()
        val decoded = Json.decodeFromString<PadLayout>(legacyJson)
        assertEquals(60, decoded.mirrorMaxFps)
        assertEquals(85, decoded.mirrorSmoothingStrength)
        assertEquals(0f, decoded.backgroundImageDim)
    }

    @Test
    fun `ScreenCutout serialization round-trip preserves motionSmoothingStrength`() {
        val original = ScreenCutout(
            id = "cutout-test",
            name = "Test Cutout",
            srcX = 0f, srcY = 0f, srcWidth = 1f, srcHeight = 1f,
            destX = 0f, destY = 0f, destWidth = 1f, destHeight = 1f,
            motionSmoothingStrength = 80
        )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ScreenCutout>(jsonString)
        assertEquals(80, decoded.motionSmoothingStrength)
    }

    @Test
    fun `ScreenCutout deserialization of legacy JSON defaults motionSmoothingStrength`() {
        val legacyJson = """
            {
                "id": "cutout-legacy",
                "name": "Legacy Cutout",
                "srcX": 0.0, "srcY": 0.0, "srcWidth": 1.0, "srcHeight": 1.0,
                "destX": 0.0, "destY": 0.0, "destWidth": 1.0, "destHeight": 1.0
            }
        """.trimIndent()
        val decoded = Json.decodeFromString<ScreenCutout>(legacyJson)
        assertEquals(85, decoded.motionSmoothingStrength)
    }

    @Test
    fun `ScreenCutout createDefault creates a centered full screen aspect locked cutout`() {
        val cutout = ScreenCutout.createDefault(srcPixelWidth = 1920f, srcPixelHeight = 1080f, bottomPixelWidth = 1920f, bottomPixelHeight = 1080f)
        assertEquals(0f, cutout.srcX)
        assertEquals(0f, cutout.srcY)
        assertEquals(1f, cutout.srcWidth)
        assertEquals(1f, cutout.srcHeight)
        assertEquals(0f, cutout.destX)
        assertEquals(1f, cutout.destWidth)
        assertEquals(1f, cutout.destHeight)
        assertEquals(0f, cutout.destY)
        assertEquals(AspectRatioMode.TOP, cutout.aspectRatioMode)

        // Test with 4:3 bottom screen (default parameters)
        val defaultThorCutout = ScreenCutout.createDefault(srcPixelWidth = 1920f, srcPixelHeight = 1080f)
        assertEquals(0f, defaultThorCutout.srcX)
        assertEquals(0f, defaultThorCutout.srcY)
        assertEquals(1f, defaultThorCutout.srcWidth)
        assertEquals(1f, defaultThorCutout.srcHeight)
        assertEquals(0f, defaultThorCutout.destX)
        assertEquals(1f, defaultThorCutout.destWidth)
        assertEquals(0.75f, defaultThorCutout.destHeight, EPS)
        assertEquals(0.125f, defaultThorCutout.destY, EPS)
        assertEquals(AspectRatioMode.TOP, defaultThorCutout.aspectRatioMode)

        // Test with portrait source (1080x1920) on 4:3 bottom screen
        val portraitCutout = ScreenCutout.createDefault(srcPixelWidth = 1080f, srcPixelHeight = 1920f)
        assertEquals(0.421875f, portraitCutout.destWidth, EPS)
        assertEquals(0.2890625f, portraitCutout.destX, EPS)
        assertEquals(1f, portraitCutout.destHeight, EPS)
        assertEquals(0f, portraitCutout.destY, EPS)
    }
}





