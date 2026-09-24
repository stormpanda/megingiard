package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.macropad.PadLayout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EPS = 1e-3f

private fun resizeBounds(
    normX: Float,
    normY: Float,
    normW: Float,
    normH: Float,
    dx: Int = 0,
    dy: Int = 0,
    hToggle: Int = 0,
    vToggle: Int = 0,
    screenWidth: Float = 1000f,
    screenHeight: Float = 1000f,
    others: List<ScreenCutout> = emptyList(),
) = calculateResizedBounds(
    normX = normX,
    normY = normY,
    normW = normW,
    normH = normH,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    dx = dx,
    dy = dy,
    hToggle = hToggle,
    vToggle = vToggle,
    others = others,
)

private fun cutout(
    id: String = "1",
    destX: Float = 0f,
    destY: Float = 0f,
    destWidth: Float = 1f,
    destHeight: Float = 1f,
    srcX: Float = 0f,
    srcY: Float = 0f,
    srcWidth: Float = 1f,
    srcHeight: Float = 1f,
    name: String = "Test Cutout",
    shape: CutoutShape = CutoutShape.RECTANGLE,
    aspectRatioMode: AspectRatioMode = AspectRatioMode.TOP,
    motionSmoothing: Boolean = false,
    motionSmoothingStrength: Int = 85,
) = ScreenCutout(
    id = id,
    name = name,
    srcX = srcX,
    srcY = srcY,
    srcWidth = srcWidth,
    srcHeight = srcHeight,
    destX = destX,
    destY = destY,
    destWidth = destWidth,
    destHeight = destHeight,
    shape = shape,
    aspectRatioMode = aspectRatioMode,
    motionSmoothing = motionSmoothing,
    motionSmoothingStrength = motionSmoothingStrength,
)

private fun assertProject(
    touchX: Float,
    touchY: Float,
    expectedX: Float,
    expectedY: Float,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    screenW: Float = 1920f,
    screenH: Float = 1080f,
    sw: Float = 1920f,
    sh: Float = 1080f,
    eps: Float = EPS,
) {
    val r = projectCoordinates(touchX, touchY, screenW, screenH, sw, sh, scale, offsetX, offsetY)
    assertNotNull(r)
    assertEquals(expectedX, r!!.first, eps)
    assertEquals(expectedY, r.second, eps)
}

private fun assertCutoutProject(
    touchX: Float,
    touchY: Float,
    expectedX: Float,
    expectedY: Float,
    destLeft: Float = 100f,
    destTop: Float = 100f,
    destWidth: Float = 100f,
    destHeight: Float = 100f,
    srcX: Float = 0.1f,
    srcY: Float = 0.1f,
    srcWidth: Float = 0.8f,
    srcHeight: Float = 0.8f,
    clampToEdge: Boolean = false,
    eps: Float = EPS,
) {
    val r =
        projectCutoutCoordinates(
            touchX = touchX,
            touchY = touchY,
            destLeft = destLeft,
            destTop = destTop,
            destWidth = destWidth,
            destHeight = destHeight,
            srcX = srcX,
            srcY = srcY,
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            clampToEdge = clampToEdge,
        )
    assertNotNull(r)
    assertEquals(expectedX, r!!.first, eps)
    assertEquals(expectedY, r.second, eps)
}

private fun assertCutoutDrag(
    cutoutId: String,
    originalX: Float,
    originalY: Float,
    targetX: Float,
    targetY: Float,
    width: Float,
    height: Float,
    allCutouts: List<ScreenCutout>,
    expectedX: Float,
    expectedY: Float,
    eps: Float = EPS,
) {
    val (x, y) =
        clampCutoutDrag(
            cutoutId = cutoutId,
            originalX = originalX,
            originalY = originalY,
            targetX = targetX,
            targetY = targetY,
            width = width,
            height = height,
            allCutouts = allCutouts,
        )
    assertEquals(expectedX, x, eps)
    assertEquals(expectedY, y, eps)
}

private fun assertCutoutResize(
    handle: ResizeHandle,
    originalX: Float,
    originalY: Float,
    originalWidth: Float,
    originalHeight: Float,
    targetX: Float,
    targetY: Float,
    targetWidth: Float,
    targetHeight: Float,
    allCutouts: List<ScreenCutout>,
    expectedX: Float? = null,
    expectedY: Float? = null,
    expectedW: Float? = null,
    expectedH: Float? = null,
    cutoutId: String = "1",
    keepAspectRatio: Boolean = false,
    cropRatio: Float = 1f,
    screenW: Float = 1000f,
    screenH: Float = 1000f,
    eps: Float = EPS,
) {
    val geom =
        clampCutoutResize(
            cutoutId = cutoutId,
            handle = handle,
            originalX = originalX,
            originalY = originalY,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            targetX = targetX,
            targetY = targetY,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            allCutouts = allCutouts,
            keepAspectRatio = keepAspectRatio,
            cropRatio = cropRatio,
            screenW = screenW,
            screenH = screenH,
        )
    expectedX?.let { assertEquals(it, geom.x, eps) }
    expectedY?.let { assertEquals(it, geom.y, eps) }
    expectedW?.let { assertEquals(it, geom.w, eps) }
    expectedH?.let { assertEquals(it, geom.h, eps) }
}

/**
 * Tests for [projectCoordinates].
 *
 * Reference scenario: AYN Thor primary display 1080×1920, secondary display 1920×1080
 * Compose surface, letterboxed mirrored content of 1920×1080 (sw=screenW, sh=screenH).
 */
class MirrorCoordinateTransformTest {
    @Test
    fun `center of screen at scale 1 maps to content center`() {
        assertProject(touchX = 960f, touchY = 540f, expectedX = 0.5f, expectedY = 0.5f)
    }

    @Test
    fun `top-left corner at scale 1 maps to (0,0)`() {
        assertProject(touchX = 0f, touchY = 0f, expectedX = 0f, expectedY = 0f)
    }

    @Test
    fun `bottom-right corner at scale 1 maps near (1,1)`() {
        // 1f - 1px to stay within the 0..1 range.
        val r =
            projectCoordinates(
                touchX = 1919f,
                touchY = 1079f,
                screenW = 1920f,
                screenH = 1080f,
                sw = 1920f,
                sh = 1080f,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
            )
        assertNotNull(r)
        assertTrue(r!!.first > 0.999f && r.first <= 1f)
        assertTrue(r.second > 0.999f && r.second <= 1f)
    }

    @Test
    fun `2x zoom centered keeps screen center mapped to content center`() {
        assertProject(touchX = 960f, touchY = 540f, expectedX = 0.5f, expectedY = 0.5f, scale = 2f)
    }

    @Test
    fun `2x zoom narrows visible content - screen left edge maps to content quarter point`() {
        // At 2× zoom the visible content X range is 0.25..0.75 of the source.
        assertProject(touchX = 0f, touchY = 540f, expectedX = 0.25f, expectedY = 0.5f, scale = 2f)
    }

    @Test
    fun `pan offset shifts mapped content`() {
        // offsetX = +scale * sw/2 should move the screen center to the content's left edge.
        assertProject(touchX = 960f, touchY = 540f, expectedX = 0f, expectedY = 0.5f, offsetX = 960f)
    }

    @Test
    fun `out-of-bounds touch returns null`() {
        // Touch beyond the right edge with no zoom/pan ⇒ nx > 1 ⇒ null.
        val r =
            projectCoordinates(
                touchX = 5000f,
                touchY = 540f,
                screenW = 1920f,
                screenH = 1080f,
                sw = 1920f,
                sh = 1080f,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
            )
        assertNull(r)
    }

    @Test
    fun `letterboxed content - touch in letterbox bar returns null`() {
        // Source is 1920×1080 fitted into a 1920×1200 surface (taller). Letterbox bars at top/bottom.
        // Content area: sw=1920, sh=1080 centered in screenW=1920, screenH=1200.
        // Touch at y=10 (in top letterbox) ⇒ svY < 0 ⇒ ny < 0 ⇒ null.
        val r =
            projectCoordinates(
                touchX = 960f,
                touchY = 10f,
                screenW = 1920f,
                screenH = 1200f,
                sw = 1920f,
                sh = 1080f,
                scale = 1f,
                offsetX = 0f,
                offsetY = 0f,
            )
        assertNull(r)
    }

    @Test
    fun `letterboxed content - touch on visible content maps correctly`() {
        assertProject(touchX = 960f, touchY = 600f, expectedX = 0.5f, expectedY = 0.5f, screenH = 1200f)
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
        assertCutoutProject(touchX = 150f, touchY = 150f, expectedX = 0.5f, expectedY = 0.5f)
    }

    @Test
    fun `projectCutoutCoordinates outside dest bounds returns null when not clamped`() {
        val r =
            projectCutoutCoordinates(
                touchX = 50f,
                touchY = 150f,
                destLeft = 100f,
                destTop = 100f,
                destWidth = 100f,
                destHeight = 100f,
                srcX = 0.1f,
                srcY = 0.1f,
                srcWidth = 0.8f,
                srcHeight = 0.8f,
                clampToEdge = false,
            )
        assertNull(r)
    }

    @Test
    fun `projectCutoutCoordinates outside dest bounds clamps to edge when clampToEdge is true`() {
        assertCutoutProject(touchX = 50f, touchY = 150f, expectedX = 0.1f, expectedY = 0.5f, clampToEdge = true)
    }

    @Test
    fun `clampCutoutDrag allows drag without overlap`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.1f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f),
                cutout("2", destX = 0.5f, destY = 0.5f, destWidth = 0.3f, destHeight = 0.3f),
            )
        assertCutoutDrag("1", 0.1f, 0.1f, 0.15f, 0.15f, 0.3f, 0.3f, allCutouts, 0.15f, 0.15f)
    }

    @Test
    fun `clampCutoutDrag prevents overlap and allows sliding`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.1f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f),
                cutout("2", destX = 0.45f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f),
            )
        assertCutoutDrag("1", 0.1f, 0.1f, 0.25f, 0.15f, 0.3f, 0.3f, allCutouts, 0.15f, 0.15f)
    }

    @Test
    fun `clampCutoutDrag selects closer candidate when sliding along either axis is valid`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.35f, destY = 0.0f, destWidth = 0.2f, destHeight = 0.2f),
                cutout("2", destX = 0.1f, destY = 0.3f, destWidth = 0.2f, destHeight = 0.2f),
            )
        // Candidate 1 (slide X, keep target Y): (0.3f, 0.36f) -> distance to target is 0.01
        assertCutoutDrag("1", 0.35f, 0.0f, 0.29f, 0.36f, 0.2f, 0.2f, allCutouts, 0.3f, 0.36f)
    }

    @Test
    fun `clampCutoutDrag handles drag past obstacle without snapping back to starting position`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.51f, destY = 0.3f, destWidth = 0.2f, destHeight = 0.2f),
                cutout("2", destX = 0.3f, destY = 0.4f, destWidth = 0.2f, destHeight = 0.2f),
                cutout("3", destX = 0.3f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f),
            )
        assertCutoutDrag("1", 0.51f, 0.3f, 0.49f, 0.3f, 0.2f, 0.2f, allCutouts, 0.5f, 0.3f)
    }

    @Test
    fun `clampCutoutDrag handles width or height exceeding 1f without crashing on empty coerce range`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0f, destY = 0f, destWidth = 1.0000178f, destHeight = 0.5f),
            )
        // Must not throw IllegalArgumentException: Cannot coerce value to an empty range
        val (x, y) = clampCutoutDrag("1", 0f, 0f, 0.1f, 0.2f, 1.0000178f, 0.5f, allCutouts)
        assertEquals(0f, x, EPS)
        assertEquals(0.2f, y, EPS)
    }

    @Test
    fun `clampCutoutDrag handles full screen cutout without crashing`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0f, destY = 0f, destWidth = 1f, destHeight = 1f),
            )
        val (x, y) = clampCutoutDrag("1", 0f, 0f, 0.1f, 0.2f, 1f, 1f, allCutouts)
        assertEquals(0f, x, EPS)
        assertEquals(0f, y, EPS)
    }

    @Test
    fun `adjustDestSizeToAspectRatio fits destination size correctly`() {
        val (w, h) =
            adjustDestSizeToAspectRatio(
                destX = 0f,
                destY = 0f,
                destWidth = 0.3f,
                destHeight = 0.3f,
                cropRatio = 16f / 9f,
                screenW = 1240f,
                screenH = 1080f,
            )
        assertEquals(0.3f, w, EPS)
        assertEquals(0.19375f, h, EPS)
    }

    @Test
    fun `clampCutoutResize maintains aspect ratio during collision`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.1f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f),
                cutout("2", destX = 0.45f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f),
            )
        assertCutoutResize(
            ResizeHandle.BOTTOM_RIGHT,
            0.1f,
            0.1f,
            0.2f,
            0.2f,
            0.1f,
            0.1f,
            0.4f,
            0.4f,
            allCutouts,
            expectedW = 0.35f,
            expectedH = 0.35f,
            keepAspectRatio = true,
            cropRatio = 1f,
            eps = 0.002f,
        )
    }

    @Test
    fun `clampCutoutResize allows clear resize and clamps on collision`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.1f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f),
                cutout("2", destX = 0.5f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.3f),
            )
        assertCutoutResize(
            ResizeHandle.BOTTOM_RIGHT,
            0.1f,
            0.1f,
            0.3f,
            0.3f,
            0.1f,
            0.1f,
            0.35f,
            0.35f,
            allCutouts,
            expectedW = 0.35f,
            expectedH = 0.35f,
        )
        assertCutoutResize(ResizeHandle.BOTTOM_RIGHT, 0.1f, 0.1f, 0.3f, 0.3f, 0.1f, 0.1f, 0.45f, 0.3f, allCutouts, expectedW = 0.4f)
    }

    @Test
    fun `clampCutoutResize clamps vertical scaling of lower cutout against upper cutout`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.3f, destY = 0.4f, destWidth = 0.2f, destHeight = 0.2f), // Lower cutout
                cutout("2", destX = 0.3f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f), // Upper cutout (bottom is 0.3)
            )
        val geom =
            clampCutoutResize(
                cutoutId = "1",
                handle = ResizeHandle.TOP_LEFT,
                originalX = 0.3f,
                originalY = 0.4f,
                originalWidth = 0.2f,
                originalHeight = 0.2f,
                targetX = 0.3f,
                targetY = 0.2f, // Drag top edge up past upper cutout's bottom
                targetWidth = 0.2f,
                targetHeight = 0.4f,
                allCutouts = allCutouts,
            )
        assertEquals(0.3f, geom.y, EPS) // Should clamp to upper cutout's bottom (0.3)
        assertEquals(0.3f, geom.h, EPS) // Height should be 0.6 (originalBottom) - 0.3 = 0.3
    }

    @Test
    fun `clampCutoutResize clamps vertical scaling of lower cutout with slight horizontal drift`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.3f, destY = 0.4f, destWidth = 0.2f, destHeight = 0.2f),
                cutout("2", destX = 0.3f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.2f),
            )
        assertCutoutResize(
            ResizeHandle.TOP_LEFT,
            0.3f,
            0.4f,
            0.2f,
            0.2f,
            0.29f,
            0.2f,
            0.21f,
            0.4f,
            allCutouts,
            expectedY = 0.3f,
            expectedH = 0.3f,
        )
    }

    @Test
    fun `clampCutoutResize clamps horizontal scaling when target is taller and vertically overlaps`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.5f, destY = 0.2f, destWidth = 0.2f, destHeight = 0.1f),
                cutout("2", destX = 0.2f, destY = 0.1f, destWidth = 0.2f, destHeight = 0.3f),
            )
        assertCutoutResize(
            ResizeHandle.BOTTOM_LEFT,
            0.5f,
            0.2f,
            0.2f,
            0.1f,
            0.38f,
            0.2f,
            0.32f,
            0.15f,
            allCutouts,
            expectedX = 0.4f,
            expectedW = 0.3f,
        )
    }

    @Test
    fun `clampCutoutResize clamps vertical scaling when target is wider and horizontally overlaps`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.2f, destY = 0.4f, destWidth = 0.1f, destHeight = 0.1f),
                cutout("2", destX = 0.1f, destY = 0.1f, destWidth = 0.3f, destHeight = 0.2f),
            )
        assertCutoutResize(
            ResizeHandle.TOP_LEFT,
            0.2f,
            0.4f,
            0.1f,
            0.1f,
            0.05f,
            0.25f,
            0.25f,
            0.25f,
            allCutouts,
            expectedY = 0.3f,
            expectedW = 0.25f,
        )
    }

    @Test
    fun `clampCutoutResize clamps top-left drag past corner without snapping back`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.35f, destY = 0.42f, destWidth = 0.35f, destHeight = 0.23f),
                cutout("2", destX = 0.2f, destY = 0.2f, destWidth = 0.2f, destHeight = 0.2f),
            )
        assertCutoutResize(
            ResizeHandle.TOP_LEFT,
            0.5f,
            0.45f,
            0.2f,
            0.2f,
            0.35f,
            0.34f,
            0.35f,
            0.31f,
            allCutouts,
            expectedX = 0.35f,
            expectedY = 0.4f,
        )
    }

    @Test
    fun `clampCutoutResize clamps top-left drag past corner without snapping back when starting adjacent`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.35f, destY = 0.42f, destWidth = 0.35f, destHeight = 0.23f),
                cutout("2", destX = 0.2f, destY = 0.2f, destWidth = 0.2f, destHeight = 0.2f),
            )
        assertCutoutResize(
            ResizeHandle.TOP_LEFT,
            0.40f,
            0.45f,
            0.20f,
            0.20f,
            0.35f,
            0.34f,
            0.25f,
            0.31f,
            allCutouts,
            expectedX = 0.35f,
            expectedY = 0.40f,
        )
    }

    @Test
    fun `clampCutoutResize clamps top-right drag against multiple cutouts correctly`() {
        val allCutouts =
            listOf(
                cutout("1", destX = 0.35f, destY = 0.42f, destWidth = 0.30f, destHeight = 0.23f),
                cutout("2", destX = 0.30f, destY = 0.20f, destWidth = 0.30f, destHeight = 0.20f),
                cutout("3", destX = 0.65f, destY = 0.30f, destWidth = 0.20f, destHeight = 0.20f),
            )
        assertCutoutResize(
            ResizeHandle.TOP_RIGHT,
            0.35f,
            0.42f,
            0.30f,
            0.23f,
            0.35f,
            0.30f,
            0.32f,
            0.35f,
            allCutouts,
            expectedX = 0.35f,
            expectedY = 0.40f,
            expectedW = 0.30f,
        )
    }

    @Test
    fun `ScreenCutout serialization round-trip preserves motionSmoothing`() {
        val original =
            ScreenCutout(
                id = "c-test-smooth",
                name = "Test Smooth",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
                motionSmoothing = true,
            )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ScreenCutout>(jsonString)
        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertTrue(decoded.motionSmoothing)
    }

    @Test
    fun `ScreenCutout deserialization of legacy JSON defaults motionSmoothing to false`() {
        val legacyJson =
            """
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
        val original =
            ScreenCutout(
                id = "c-test-shape",
                name = "Test Shape",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
                shape = CutoutShape.CIRCLE,
            )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ScreenCutout>(jsonString)
        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(CutoutShape.CIRCLE, decoded.shape)
    }

    @Test
    fun `ScreenCutout deserialization of legacy JSON defaults shape to RECTANGLE`() {
        val legacyJson =
            """
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
        val original =
            ScreenCutout(
                id = "c-test-aspect",
                name = "Test Aspect",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
                aspectRatioMode = AspectRatioMode.BOTTOM,
            )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ScreenCutout>(jsonString)
        assertEquals(original.id, decoded.id)
        assertEquals(original.name, decoded.name)
        assertEquals(AspectRatioMode.BOTTOM, decoded.aspectRatioMode)
    }

    @Test
    fun `ScreenCutout deserialization of legacy JSON migrates keepAspectRatio to aspectRatioMode`() {
        val legacyJsonTrue =
            """
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

        val legacyJsonFalse =
            """
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
        val original =
            PadLayout(
                id = "layout-test",
                name = "Test Layout",
                mirrorMaxFps = 45,
                mirrorSmoothingStrength = 80,
                backgroundImageDim = 0.65f,
            )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<PadLayout>(jsonString)
        assertEquals(45, decoded.mirrorMaxFps)
        assertEquals(80, decoded.mirrorSmoothingStrength)
        assertEquals(0.65f, decoded.backgroundImageDim)
    }

    @Test
    fun `PadLayout deserialization of legacy JSON defaults mirrorMaxFps and mirrorSmoothingStrength`() {
        val legacyJson =
            """
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
        val original =
            ScreenCutout(
                id = "cutout-test",
                name = "Test Cutout",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0f,
                destY = 0f,
                destWidth = 1f,
                destHeight = 1f,
                motionSmoothingStrength = 80,
            )
        val jsonString = Json.encodeToString(original)
        val decoded = Json.decodeFromString<ScreenCutout>(jsonString)
        assertEquals(80, decoded.motionSmoothingStrength)
    }

    @Test
    fun `ScreenCutout deserialization of legacy JSON defaults motionSmoothingStrength`() {
        val legacyJson =
            """
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
        val cutout =
            ScreenCutout.createDefault(
                srcPixelWidth = 1920f,
                srcPixelHeight = 1080f,
                bottomPixelWidth = 1920f,
                bottomPixelHeight = 1080f,
            )
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

    @Test
    fun `calculateResizedBounds expands width alternating right and left border`() {
        val screenW = 1000f
        val screenH = 1000f
        // 100px to 300px (width 200px, center 200px)
        val initialX = 0.100f
        val initialY = 0.100f
        val initialW = 0.200f
        val initialH = 0.200f

        // Step 1: Expand right border (+1 px)
        val step1 =
            resizeBounds(normX = initialX, normY = initialY, normW = initialW, normH = initialH, dx = 1, dy = 0, hToggle = 0, vToggle = 0)
        assertEquals(0.100f, step1.x, EPS) // Left unchanged
        assertEquals(0.201f, step1.width, EPS) // Width +1
        assertEquals(1, step1.hToggle)

        // Step 2: Expand left border (+1 px)
        val step2 =
            resizeBounds(
                normX = step1.x,
                normY = step1.y,
                normW = step1.width,
                normH = step1.height,
                dx = 1,
                dy = 0,
                hToggle = step1.hToggle,
                vToggle = step1.vToggle,
            )
        assertEquals(0.099f, step2.x, EPS) // Left shifted -1 px
        assertEquals(0.202f, step2.width, EPS) // Width +1 px
        assertEquals(0, step2.hToggle)

        // Center calculation: (99 + 202 / 2) = 200px = initial center (100 + 200 / 2 = 200px)
        val centerStep2 = (step2.x + step2.width / 2f) * screenW
        assertEquals(200f, centerStep2, EPS)
    }

    @Test
    fun `calculateResizedBounds shrinks width alternating left and right border`() {
        val screenW = 1000f
        val screenH = 1000f
        val startX = 0.099f
        val startY = 0.100f
        val startW = 0.202f
        val startH = 0.200f

        // Step 1: Shrink left border (-1 px)
        val step1 =
            resizeBounds(normX = startX, normY = startY, normW = startW, normH = startH, dx = -1, dy = 0, hToggle = 0, vToggle = 0)
        assertEquals(0.100f, step1.x, EPS) // Left shifted +1 px
        assertEquals(0.201f, step1.width, EPS) // Width -1 px
        assertEquals(1, step1.hToggle)

        // Step 2: Shrink right border (-1 px)
        val step2 =
            resizeBounds(
                normX = step1.x,
                normY = step1.y,
                normW = step1.width,
                normH = step1.height,
                dx = -1,
                dy = 0,
                hToggle = step1.hToggle,
                vToggle = step1.vToggle,
            )
        assertEquals(0.100f, step2.x, EPS) // Left unchanged
        assertEquals(0.200f, step2.width, EPS) // Width -1 px
        assertEquals(0, step2.hToggle)
    }

    @Test
    fun `calculateResizedBounds expands height alternating top and bottom border on UP`() {
        val screenW = 1000f
        val screenH = 1000f
        val initialX = 0.100f
        val initialY = 0.100f
        val initialW = 0.200f
        val initialH = 0.200f

        // Step 1: Direction UP (dy = -1) -> Top border expands (-1 px top)
        val step1 =
            resizeBounds(normX = initialX, normY = initialY, normW = initialW, normH = initialH, dx = 0, dy = -1, hToggle = 0, vToggle = 0)
        assertEquals(0.099f, step1.y, EPS) // Top shifted -1 px
        assertEquals(0.201f, step1.height, EPS) // Height +1 px
        assertEquals(1, step1.vToggle)

        // Step 2: Direction UP (dy = -1) -> Bottom border expands (+1 px bottom)
        val step2 =
            resizeBounds(
                normX = step1.x,
                normY = step1.y,
                normW = step1.width,
                normH = step1.height,
                dx = 0,
                dy = -1,
                hToggle = step1.hToggle,
                vToggle = step1.vToggle,
            )
        assertEquals(0.099f, step2.y, EPS) // Top unchanged
        assertEquals(0.202f, step2.height, EPS) // Height +1 px
        assertEquals(0, step2.vToggle)

        // Center calculation: (99 + 202 / 2) = 200px = initial center (100 + 200 / 2 = 200px)
        val centerStep2 = (step2.y + step2.height / 2f) * screenH
        assertEquals(200f, centerStep2, EPS)
    }

    @Test
    fun `calculateResizedBounds shrinks height alternating bottom and top border on DOWN`() {
        val screenW = 1000f
        val screenH = 1000f
        val startX = 0.100f
        val startY = 0.099f
        val startW = 0.200f
        val startH = 0.202f

        // Step 1: Direction DOWN (dy = 1) -> Bottom border shrinks (-1 px bottom)
        val step1 =
            resizeBounds(normX = startX, normY = startY, normW = startW, normH = startH, dx = 0, dy = 1, hToggle = 0, vToggle = 0)
        assertEquals(0.099f, step1.y, EPS) // Top unchanged
        assertEquals(0.201f, step1.height, EPS) // Height -1 px
        assertEquals(1, step1.vToggle)

        // Step 2: Direction DOWN (dy = 1) -> Top border shrinks (-1 px top)
        val step2 =
            resizeBounds(
                normX = step1.x,
                normY = step1.y,
                normW = step1.width,
                normH = step1.height,
                dx = 0,
                dy = 1,
                hToggle = step1.hToggle,
                vToggle = step1.vToggle,
            )
        assertEquals(0.100f, step2.y, EPS) // Top shifted +1 px
        assertEquals(0.200f, step2.height, EPS) // Height -1 px
        assertEquals(0, step2.vToggle)
    }

    @Test
    fun `calculateResizedBounds clamps at screen boundaries and minimum size`() {
        val screenW = 1000f
        val screenH = 1000f

        // Top-left boundary: expanding left when at X=0 expands right instead
        val atLeft =
            calculateResizedBounds(
                normX = 0f,
                normY = 0f,
                normW = 0.100f,
                normH = 0.100f,
                screenWidth = screenW,
                screenHeight = screenH,
                dx = 1,
                dy = 0,
                hToggle = 1, // Wants to expand left
                vToggle = 0,
            )
        assertEquals(0f, atLeft.x, EPS)
        assertEquals(0.101f, atLeft.width, EPS)

        // Minimum size limit
        val atMin =
            calculateResizedBounds(
                normX = 0.100f,
                normY = 0.100f,
                normW = 0.050f,
                normH = 0.050f,
                screenWidth = screenW,
                screenHeight = screenH,
                dx = -1,
                dy = 1,
                hToggle = 0,
                vToggle = 0,
                minSizeRatio = 0.05f,
            )
        assertEquals(0.050f, atMin.width, EPS) // Does not shrink past min
        assertEquals(0.050f, atMin.height, EPS) // Does not shrink past min
    }

    @Test
    fun `isCutoutGeometryValid detects overlaps, boundaries and minimum sizes`() {
        val obstacle =
            cutout(id = "obs", name = "Obs", destX = 0.300f, destY = 0.100f, destWidth = 0.200f, destHeight = 0.200f)
        val others = listOf(obstacle)

        // Non-overlapping rectangle
        assertTrue(isCutoutGeometryValid(x = 0.050f, y = 0.100f, w = 0.200f, h = 0.200f, others = others))

        // Overlapping right edge with obstacle
        assertFalse(isCutoutGeometryValid(x = 0.150f, y = 0.100f, w = 0.200f, h = 0.200f, others = others))

        // Out of screen boundaries
        assertFalse(isCutoutGeometryValid(x = -0.010f, y = 0.100f, w = 0.200f, h = 0.200f, others = others))
        assertFalse(isCutoutGeometryValid(x = 0.900f, y = 0.100f, w = 0.200f, h = 0.200f, others = others))

        // Below minimum size
        assertFalse(isCutoutGeometryValid(x = 0.050f, y = 0.100f, w = 0.020f, h = 0.200f, others = others, minCutoutSize = 0.05f))
    }

    @Test
    fun `calculateResizedBounds avoids overlapping other cutouts`() {
        val screenW = 1000f
        val screenH = 1000f

        // Cutout A: X=0.080, W=0.200 (Right edge at 0.280)
        // Obstacle right: X=0.280, W=0.200
        val rightObstacle =
            cutout(id = "right", name = "Right", destX = 0.280f, destY = 0.100f, destWidth = 0.200f, destHeight = 0.200f)

        // Expanding right border (hToggle=0) hits obstacle -> falls back to expanding left border
        val expandedLeftFallback =
            calculateResizedBounds(
                normX = 0.080f,
                normY = 0.100f,
                normW = 0.200f,
                normH = 0.200f,
                screenWidth = screenW,
                screenHeight = screenH,
                dx = 1,
                dy = 0,
                hToggle = 0, // Wants right, but right (0.281) hits obstacle (0.280)
                vToggle = 0,
                others = listOf(rightObstacle),
            )
        assertEquals(0.079f, expandedLeftFallback.x, EPS) // Left shifted -1 px
        assertEquals(0.201f, expandedLeftFallback.width, EPS) // Width +1 px
        assertEquals(0, expandedLeftFallback.hToggle)

        // Obstacle on both left (X=0) and right (X=0.200)
        val leftObstacle =
            cutout(id = "left", name = "Left", destX = 0f, destY = 0.100f, destWidth = 0.100f, destHeight = 0.200f)
        val rightObs2 =
            cutout(id = "right", name = "Right", destX = 0.300f, destY = 0.100f, destWidth = 0.200f, destHeight = 0.200f)

        // Cutout bounded tightly: X=0.100, W=0.200 (from 0.100 to 0.300)
        val trapped =
            calculateResizedBounds(
                normX = 0.100f,
                normY = 0.100f,
                normW = 0.200f,
                normH = 0.200f,
                screenWidth = screenW,
                screenHeight = screenH,
                dx = 1,
                dy = 0,
                hToggle = 0,
                vToggle = 0,
                others = listOf(leftObstacle, rightObs2),
            )
        // Expansion is blocked on both sides, size stays invariant
        assertEquals(0.100f, trapped.x, EPS)
        assertEquals(0.200f, trapped.width, EPS)

        // Vertical obstacle above (Y=0, H=0.100) -> Cutout at Y=0.100, H=0.200 (Y range 0.100 - 0.300)
        val topObstacle =
            cutout(id = "top", name = "Top", destX = 0.100f, destY = 0f, destWidth = 0.200f, destHeight = 0.100f)
        val expandBottomFallback =
            calculateResizedBounds(
                normX = 0.100f,
                normY = 0.100f,
                normW = 0.200f,
                normH = 0.200f,
                screenWidth = screenW,
                screenHeight = screenH,
                dx = 0,
                dy = -1, // D-Pad UP wants to expand top border first (vToggle=0)
                hToggle = 0,
                vToggle = 0,
                others = listOf(topObstacle),
            )
        assertEquals(0.100f, expandBottomFallback.y, EPS) // Top stayed at 0.100
        assertEquals(0.201f, expandBottomFallback.height, EPS) // Bottom expanded +1 px to 0.301
        assertEquals(0, expandBottomFallback.vToggle)
    }

    @Test
    fun `clampCutoutResize edge handles move only respective edge and clamp against collisions`() {
        val obstacleTop = cutout(id = "obs_top", name = "Obs Top", destX = 0.2f, destY = 0f, destWidth = 0.4f, destHeight = 0.2f)
        val obstacleRight = cutout(id = "obs_right", name = "Obs Right", destX = 0.7f, destY = 0.2f, destWidth = 0.2f, destHeight = 0.4f)
        val allCutouts = listOf(obstacleTop, obstacleRight)

        // 1. TOP Handle
        assertCutoutResize(
            ResizeHandle.TOP,
            0.2f,
            0.3f,
            0.3f,
            0.3f,
            0.2f,
            0.1f,
            0.3f,
            0.5f,
            allCutouts,
            expectedX = 0.2f,
            expectedY = 0.2f,
            expectedW = 0.3f,
            expectedH = 0.4f,
            cutoutId = "test",
        )
        // 2. BOTTOM Handle
        assertCutoutResize(
            ResizeHandle.BOTTOM,
            0.2f,
            0.3f,
            0.3f,
            0.3f,
            0.2f,
            0.3f,
            0.3f,
            0.5f,
            allCutouts,
            expectedX = 0.2f,
            expectedY = 0.3f,
            expectedW = 0.3f,
            expectedH = 0.5f,
            cutoutId = "test",
        )
        // 3. LEFT Handle
        assertCutoutResize(
            ResizeHandle.LEFT,
            0.2f,
            0.3f,
            0.3f,
            0.3f,
            0.05f,
            0.3f,
            0.45f,
            0.3f,
            allCutouts,
            expectedX = 0.05f,
            expectedY = 0.3f,
            expectedW = 0.45f,
            expectedH = 0.3f,
            cutoutId = "test",
        )
        // 4. RIGHT Handle
        assertCutoutResize(
            ResizeHandle.RIGHT,
            0.2f,
            0.3f,
            0.3f,
            0.3f,
            0.2f,
            0.3f,
            0.65f,
            0.3f,
            allCutouts,
            expectedX = 0.2f,
            expectedY = 0.3f,
            expectedW = 0.5f,
            expectedH = 0.3f,
            cutoutId = "test",
        )
    }

    @Test
    fun `clampCropResizeProportional maintains exact aspect ratio for all corners`() {
        val topW = 1920f
        val topH = 1080f
        val cutoutRatio = 16f / 9f // Physical ratio = 16:9
        val expectedNormRatio = cutoutRatio * (topH / topW) // (16/9) * (1080/1920) = 1.0

        // Test BOTTOM_RIGHT handle drag outwards (+dx, +dy)
        val br =
            clampCropResizeProportional(
                handle = ResizeHandle.BOTTOM_RIGHT,
                originalX = 0.2f,
                originalY = 0.2f,
                originalWidth = 0.4f,
                originalHeight = 0.4f,
                totalDx = 192f, // +0.1
                totalDy = 108f, // +0.1
                topScreenW = topW,
                topScreenH = topH,
                cutoutRatio = cutoutRatio,
            )
        assertEquals(0.2f, br.x, EPS)
        assertEquals(0.2f, br.y, EPS)
        assertEquals(0.5f, br.w, EPS)
        assertEquals(0.5f, br.h, EPS)
        assertEquals(expectedNormRatio, br.w / br.h, EPS)

        // Test TOP_LEFT handle drag outwards (-dx, -dy)
        val tl =
            clampCropResizeProportional(
                handle = ResizeHandle.TOP_LEFT,
                originalX = 0.2f,
                originalY = 0.2f,
                originalWidth = 0.4f,
                originalHeight = 0.4f,
                totalDx = -192f, // -0.1 (moves left)
                totalDy = -108f, // -0.1 (moves top)
                topScreenW = topW,
                topScreenH = topH,
                cutoutRatio = cutoutRatio,
            )
        assertEquals(0.1f, tl.x, EPS)
        assertEquals(0.1f, tl.y, EPS)
        assertEquals(0.5f, tl.w, EPS)
        assertEquals(0.5f, tl.h, EPS)
        assertEquals(expectedNormRatio, tl.w / tl.h, EPS)
    }

    @Test
    fun `clampCropResizeProportional clamps at display boundaries without deforming ratio`() {
        val topW = 1920f
        val topH = 1080f
        val cutoutRatio = 16f / 9f // Norm ratio = 1.0

        // Drag BOTTOM_RIGHT way beyond screen (e.g. +2000px)
        val brClamped =
            clampCropResizeProportional(
                handle = ResizeHandle.BOTTOM_RIGHT,
                originalX = 0.3f,
                originalY = 0.5f,
                originalWidth = 0.3f,
                originalHeight = 0.3f,
                totalDx = 2000f,
                totalDy = 2000f,
                topScreenW = topW,
                topScreenH = topH,
                cutoutRatio = cutoutRatio,
            )
        // Max height from 0.5 is 0.5, so max width is 0.5 (X: 0.3 + 0.5 = 0.8 <= 1.0)
        assertEquals(0.3f, brClamped.x, EPS)
        assertEquals(0.5f, brClamped.y, EPS)
        assertEquals(0.5f, brClamped.w, EPS)
        assertEquals(0.5f, brClamped.h, EPS)
        assertEquals(1.0f, brClamped.w / brClamped.h, EPS)
    }

    @Test
    fun `calculateProportionalResizedBounds expands and shrinks preserving ratio`() {
        val screenW = 1000f
        val screenH = 1000f
        val normRatio = 1.5f // w / h = 1.5

        val expanded =
            calculateProportionalResizedBounds(
                normX = 0.2f,
                normY = 0.2f,
                normW = 0.3f,
                normH = 0.2f,
                screenWidth = screenW,
                screenHeight = screenH,
                stepDelta = 1,
                targetNormRatio = normRatio,
            )
        assertTrue(expanded.w > 0.3f)
        assertTrue(expanded.h > 0.2f)
        assertEquals(normRatio, expanded.w / expanded.h, EPS)

        val shrunk =
            calculateProportionalResizedBounds(
                normX = 0.2f,
                normY = 0.2f,
                normW = 0.3f,
                normH = 0.2f,
                screenWidth = screenW,
                screenHeight = screenH,
                stepDelta = -1,
                targetNormRatio = normRatio,
            )
        assertTrue(shrunk.w < 0.3f)
        assertTrue(shrunk.h < 0.2f)
        assertEquals(normRatio, shrunk.w / shrunk.h, EPS)
    }

    @Test
    fun `calculateResizedBounds supports multi-pixel step increments`() {
        val screenW = 1000f
        val screenH = 1000f

        // Initial cutout: X=0.200 (200px), Y=0.200 (200px), W=0.400 (400px), H=0.400 (400px)
        val resized10px =
            resizeBounds(normX = 0.200f, normY = 0.200f, normW = 0.400f, normH = 0.400f, dx = 10, dy = 0, hToggle = 0, vToggle = 0)
        // Expanded by exactly 10 pixels on width: 400px + 10px = 410px -> 0.410f
        assertEquals(0.410f, resized10px.width, EPS)

        val shrunk10px =
            resizeBounds(
                normX = resized10px.x,
                normY = resized10px.y,
                normW = resized10px.width,
                normH = resized10px.height,
                dx = -10,
                dy = 0,
                hToggle = resized10px.hToggle,
                vToggle = resized10px.vToggle,
            )
        // Shrunk back by 10 pixels: 410px - 10px = 400px -> 0.400f
        assertEquals(0.400f, shrunk10px.width, EPS)
    }

    @Test
    fun `calculateProportionalResizedBounds supports multi-step delta increments`() {
        val screenW = 1000f
        val screenH = 1000f
        val normRatio = 1.5f

        val expanded10Steps =
            calculateProportionalResizedBounds(
                normX = 0.2f,
                normY = 0.2f,
                normW = 0.3f,
                normH = 0.2f,
                screenWidth = screenW,
                screenHeight = screenH,
                stepDelta = 10,
                targetNormRatio = normRatio,
            )
        assertTrue(expanded10Steps.w > 0.3f)
        assertTrue(expanded10Steps.h > 0.2f)
        assertEquals(normRatio, expanded10Steps.w / expanded10Steps.h, EPS)

        val singleStep =
            calculateProportionalResizedBounds(
                normX = 0.2f,
                normY = 0.2f,
                normW = 0.3f,
                normH = 0.2f,
                screenWidth = screenW,
                screenHeight = screenH,
                stepDelta = 1,
                targetNormRatio = normRatio,
            )
        assertTrue(expanded10Steps.w > singleStep.w)
    }

    @Test
    fun `calculateResizedBounds supports custom minSizeRatio for visual anchor scaling`() {
        val screenW = 1000f
        val screenH = 1000f
        val minAnchorRatio = 0.04f

        // Starting at exactly minAnchorRatio: 40px out of 1000px
        val atMin =
            calculateResizedBounds(
                normX = 0.500f,
                normY = 0.500f,
                normW = 0.040f,
                normH = 0.040f,
                screenWidth = screenW,
                screenHeight = screenH,
                dx = -10,
                dy = 10,
                minSizeRatio = minAnchorRatio,
            )
        // Must clamp to 0.040f (40px)
        assertEquals(0.040f, atMin.width, EPS)
        assertEquals(0.040f, atMin.height, EPS)

        // D-Pad UP (dy < 0) expands height symmetrically from center
        val expandedUp =
            calculateResizedBounds(
                normX = 0.500f,
                normY = 0.500f,
                normW = 0.040f,
                normH = 0.040f,
                screenWidth = screenW,
                screenHeight = screenH,
                dx = 0,
                dy = -2,
                minSizeRatio = minAnchorRatio,
            )
        // Expanded by 2px: top border -1px (0.499f), height 0.042f
        assertEquals(0.499f, expandedUp.y, EPS)
        assertEquals(0.042f, expandedUp.height, EPS)
    }

    @Test
    fun `calculateResizedBounds shrinks down to 1 percent default minimum`() {
        val screenW = 1000f
        val screenH = 1000f

        // Shrinking past 0.05f succeeds and clamps at MIN_GAMEPAD_CUTOUT_SIZE (0.010f / 10px)
        val shrunk =
            calculateResizedBounds(
                normX = 0.500f,
                normY = 0.500f,
                normW = 0.030f,
                normH = 0.030f,
                screenWidth = screenW,
                screenHeight = screenH,
                dx = -100,
                dy = 100,
            )
        assertEquals(MIN_GAMEPAD_CUTOUT_SIZE, shrunk.width, EPS)
        assertEquals(MIN_GAMEPAD_CUTOUT_SIZE, shrunk.height, EPS)
    }

    @Test
    fun `calculateProportionalResizedBounds shrinks down to 1 percent default minimum`() {
        val screenW = 1000f
        val screenH = 1000f

        val shrunk =
            calculateProportionalResizedBounds(
                normX = 0.500f,
                normY = 0.500f,
                normW = 0.030f,
                normH = 0.030f,
                screenWidth = screenW,
                screenHeight = screenH,
                stepDelta = -100,
                targetNormRatio = 1f,
            )
        assertEquals(MIN_GAMEPAD_CUTOUT_SIZE, shrunk.w, EPS)
        assertEquals(MIN_GAMEPAD_CUTOUT_SIZE, shrunk.h, EPS)
    }

    @Test
    fun `isCutoutGeometryValid allows 1 percent size by default and rejects below 1 percent`() {
        val others = emptyList<ScreenCutout>()
        // 1% (0.010f) is valid
        assertTrue(isCutoutGeometryValid(x = 0.100f, y = 0.100f, w = 0.010f, h = 0.010f, others = others))
        // 0.5% (0.005f) is below default minimum
        assertFalse(isCutoutGeometryValid(x = 0.100f, y = 0.100f, w = 0.005f, h = 0.010f, others = others))
        assertFalse(isCutoutGeometryValid(x = 0.100f, y = 0.100f, w = 0.010f, h = 0.005f, others = others))
    }

    @Test
    fun `clampCutoutResize enforces 5 percent touch minimum by default`() {
        val others = emptyList<ScreenCutout>()
        val shrunk =
            clampCutoutResize(
                cutoutId = "test",
                handle = ResizeHandle.BOTTOM,
                originalX = 0.100f,
                originalY = 0.100f,
                originalWidth = 0.200f,
                originalHeight = 0.200f,
                targetX = 0.100f,
                targetY = 0.100f,
                targetWidth = 0.200f,
                targetHeight = 0.020f,
                allCutouts = others,
            )
        assertEquals(MIN_TOUCH_CUTOUT_SIZE, shrunk.h, EPS)
    }

    @Test
    fun `adjustSourceCropToAspectRatio clamps to 1 percent minimum for extremely flat target cutout`() {
        // Flat destination cutout: destWidth = 0.8f, destHeight = 0.015f
        val cutout =
            ScreenCutout(
                id = "test",
                srcX = 0.2f,
                srcY = 0.2f,
                srcWidth = 0.2f,
                srcHeight = 0.2f,
                destX = 0.1f,
                destY = 0.1f,
                destWidth = 0.8f,
                destHeight = 0.015f,
                aspectRatioMode = AspectRatioMode.BOTTOM,
            )
        val adjusted =
            adjustSourceCropToAspectRatio(
                cutout = cutout,
                screenW = 1080f,
                screenH = 1240f,
                srcW = 1920f,
                srcH = 1080f,
            )
        // Height must not collapse below MIN_GAMEPAD_CUTOUT_SIZE (0.01f)
        assertTrue(adjusted.srcHeight >= MIN_GAMEPAD_CUTOUT_SIZE)
        assertTrue(adjusted.srcWidth >= MIN_GAMEPAD_CUTOUT_SIZE)
        assertTrue(adjusted.srcWidth <= 1f)
        assertTrue(adjusted.srcHeight <= 1f)
        assertTrue(adjusted.srcX >= 0f && adjusted.srcX + adjusted.srcWidth <= 1f)
        assertTrue(adjusted.srcY >= 0f && adjusted.srcY + adjusted.srcHeight <= 1f)

        // Verify ratio matches expected factor
        val targetRatio = (0.8f * 1080f) / (0.015f * 1240f)
        val factor = targetRatio * (1080f / 1920f)
        assertEquals(factor, adjusted.srcWidth / adjusted.srcHeight, 0.01f)
    }

    @Test
    fun `adjustSourceCropToAspectRatio clamps to 1 percent minimum for extremely tall target cutout`() {
        // Tall destination cutout: destWidth = 0.015f, destHeight = 0.8f
        val cutout =
            ScreenCutout(
                id = "test",
                srcX = 0.2f,
                srcY = 0.2f,
                srcWidth = 0.2f,
                srcHeight = 0.2f,
                destX = 0.1f,
                destY = 0.1f,
                destWidth = 0.015f,
                destHeight = 0.8f,
                aspectRatioMode = AspectRatioMode.BOTTOM,
            )
        val adjusted =
            adjustSourceCropToAspectRatio(
                cutout = cutout,
                screenW = 1080f,
                screenH = 1240f,
                srcW = 1920f,
                srcH = 1080f,
            )
        // Width must not collapse below MIN_GAMEPAD_CUTOUT_SIZE (0.01f)
        assertTrue(adjusted.srcWidth >= MIN_GAMEPAD_CUTOUT_SIZE)
        assertTrue(adjusted.srcHeight >= MIN_GAMEPAD_CUTOUT_SIZE)
        assertTrue(adjusted.srcWidth <= 1f)
        assertTrue(adjusted.srcHeight <= 1f)
        assertTrue(adjusted.srcX >= 0f && adjusted.srcX + adjusted.srcWidth <= 1f)
        assertTrue(adjusted.srcY >= 0f && adjusted.srcY + adjusted.srcHeight <= 1f)

        // Verify ratio matches expected factor
        val targetRatio = (0.015f * 1080f) / (0.8f * 1240f)
        val factor = targetRatio * (1080f / 1920f)
        assertEquals(factor, adjusted.srcWidth / adjusted.srcHeight, 0.01f)
    }

    @Test
    fun `calculateProportionalResizedBounds heals and expands from sub-minimum size`() {
        val screenW = 1920f
        val screenH = 1080f
        val targetNormRatio = 20f // wide ratio: w / h = 20
        // minW = max(0.01f, 0.01f * 20f) = 0.20f, minH = 0.01f
        // Start from sub-minimum dimensions (e.g. w = 0.05f, h = 0.0025f)
        val healed =
            calculateProportionalResizedBounds(
                normX = 0.4f,
                normY = 0.4f,
                normW = 0.05f,
                normH = 0.0025f,
                screenWidth = screenW,
                screenHeight = screenH,
                stepDelta = 1,
                targetNormRatio = targetNormRatio,
            )
        // Expanding must immediately heal up to minW (0.20f) instead of locking up
        assertEquals(0.20f, healed.w, EPS)
        assertEquals(0.01f, healed.h, EPS)
        assertEquals(targetNormRatio, healed.w / healed.h, EPS)
    }

    @Test
    fun `calculateProportionalResizedBounds resizes wide aspect ratio cutouts smoothly down to 1 percent height`() {
        val screenW = 1920f
        val screenH = 1080f
        val targetNormRatio = 25f // w / h = 25
        // Start at w = 0.50f, h = 0.02f
        val expanded =
            calculateProportionalResizedBounds(
                normX = 0.2f,
                normY = 0.2f,
                normW = 0.50f,
                normH = 0.02f,
                screenWidth = screenW,
                screenHeight = screenH,
                stepDelta = 5,
                targetNormRatio = targetNormRatio,
            )
        assertTrue(expanded.w > 0.50f)
        assertTrue(expanded.h > 0.02f)
        assertEquals(targetNormRatio, expanded.w / expanded.h, 0.01f)

        // Shrink all the way down: minH is 0.01f, minW is 0.25f
        val clampedShrunk =
            calculateProportionalResizedBounds(
                normX = 0.2f,
                normY = 0.2f,
                normW = 0.50f,
                normH = 0.02f,
                screenWidth = screenW,
                screenHeight = screenH,
                stepDelta = -1000,
                targetNormRatio = targetNormRatio,
            )
        assertEquals(0.25f, clampedShrunk.w, EPS)
        assertEquals(0.01f, clampedShrunk.h, EPS)
    }

    @Test
    fun `adjustDestSizeToAspectRatio clamps to 1 percent minimum`() {
        // High cropRatio (very flat) with small destWidth
        val (targetW, targetH) =
            adjustDestSizeToAspectRatio(
                destX = 0.1f,
                destY = 0.1f,
                destWidth = 0.05f,
                destHeight = 0.05f,
                cropRatio = 30f,
                screenW = 1080f,
                screenH = 1240f,
            )
        assertTrue(targetH >= MIN_GAMEPAD_CUTOUT_SIZE)
        assertTrue(targetW >= MIN_GAMEPAD_CUTOUT_SIZE)
    }

    @Test
    fun `calculateResizedBounds allows expanding from below minW and minH`() {
        val screenW = 1000f
        val screenH = 1000f
        // Start with width below minW (e.g. 5px / 0.005f where minW is 10px / 0.01f)
        val expandedW =
            calculateResizedBounds(
                normX = 0.5f,
                normY = 0.5f,
                normW = 0.005f,
                normH = 0.005f,
                screenWidth = screenW,
                screenHeight = screenH,
                dx = 5,
                dy = 0,
            )
        // Must expand 5px to 10px (0.010f) rather than being blocked
        assertEquals(0.010f, expandedW.width, EPS)
    }

    @Test
    fun `projectCutoutCoordinates transforms coordinates correctly across all 90 degree rotation steps`() {
        val destLeft = 0.2f
        val destTop = 0.3f
        val destW = 0.4f
        val destH = 0.2f
        val srcX = 0.1f
        val srcY = 0.1f
        val srcW = 0.8f
        val srcH = 0.6f

        // Center touch: (0.2 + 0.2, 0.3 + 0.1) -> rx=0.5, ry=0.5 -> center of source for any rotation
        val center =
            projectCutoutCoordinates(
                touchX = 0.4f,
                touchY = 0.4f,
                destLeft = destLeft,
                destTop = destTop,
                destWidth = destW,
                destHeight = destH,
                srcX = srcX,
                srcY = srcY,
                srcWidth = srcW,
                srcHeight = srcH,
                rotation = 90,
            )
        assertNotNull(center)
        assertEquals(srcX + 0.5f * srcW, center!!.first, EPS)
        assertEquals(srcY + 0.5f * srcH, center.second, EPS)

        // Top-left touch on screen: touchX = destLeft, touchY = destTop (rx=0, ry=0)
        // 0 deg: (srcX, srcY)
        val deg0 =
            projectCutoutCoordinates(
                touchX = destLeft,
                touchY = destTop,
                destLeft = destLeft,
                destTop = destTop,
                destWidth = destW,
                destHeight = destH,
                srcX = srcX,
                srcY = srcY,
                srcWidth = srcW,
                srcHeight = srcH,
                rotation = 0,
            )
        assertEquals(srcX, deg0!!.first, EPS)
        assertEquals(srcY, deg0.second, EPS)

        // 90 deg clockwise: rx=0, ry=0 -> normU=0, normV=1 -> (srcX, srcY + srcH) [bottom-left of source]
        val deg90 =
            projectCutoutCoordinates(
                touchX = destLeft,
                touchY = destTop,
                destLeft = destLeft,
                destTop = destTop,
                destWidth = destW,
                destHeight = destH,
                srcX = srcX,
                srcY = srcY,
                srcWidth = srcW,
                srcHeight = srcH,
                rotation = 90,
            )
        assertEquals(srcX, deg90!!.first, EPS)
        assertEquals(srcY + srcH, deg90.second, EPS)

        // 180 deg: rx=0, ry=0 -> normU=1, normV=1 -> (srcX + srcW, srcY + srcH) [bottom-right of source]
        val deg180 =
            projectCutoutCoordinates(
                touchX = destLeft,
                touchY = destTop,
                destLeft = destLeft,
                destTop = destTop,
                destWidth = destW,
                destHeight = destH,
                srcX = srcX,
                srcY = srcY,
                srcWidth = srcW,
                srcHeight = srcH,
                rotation = 180,
            )
        assertEquals(srcX + srcW, deg180!!.first, EPS)
        assertEquals(srcY + srcH, deg180.second, EPS)

        // 270 deg: rx=0, ry=0 -> normU=1, normV=0 -> (srcX + srcW, srcY) [top-right of source]
        val deg270 =
            projectCutoutCoordinates(
                touchX = destLeft,
                touchY = destTop,
                destLeft = destLeft,
                destTop = destTop,
                destWidth = destW,
                destHeight = destH,
                srcX = srcX,
                srcY = srcY,
                srcWidth = srcW,
                srcHeight = srcH,
                rotation = 270,
            )
        assertEquals(srcX + srcW, deg270!!.first, EPS)
        assertEquals(srcY, deg270.second, EPS)
    }

    @Test
    fun `projectCutoutCoordinates applies horizontal and vertical flipping`() {
        val destLeft = 0f
        val destTop = 0f
        val destW = 1f
        val destH = 1f
        val srcX = 0f
        val srcY = 0f
        val srcW = 1f
        val srcH = 1f

        // Touch at (0.2, 0.3)
        // Normal: (0.2, 0.3)
        val normal =
            projectCutoutCoordinates(
                0.2f,
                0.3f,
                destLeft,
                destTop,
                destW,
                destH,
                srcX,
                srcY,
                srcW,
                srcH,
                rotation = 0,
                flipHorizontal = false,
                flipVertical = false,
            )
        assertEquals(0.2f, normal!!.first, EPS)
        assertEquals(0.3f, normal.second, EPS)

        // Horizontal flip: normU = 1 - 0.2 = 0.8
        val flipH =
            projectCutoutCoordinates(
                0.2f,
                0.3f,
                destLeft,
                destTop,
                destW,
                destH,
                srcX,
                srcY,
                srcW,
                srcH,
                rotation = 0,
                flipHorizontal = true,
                flipVertical = false,
            )
        assertEquals(0.8f, flipH!!.first, EPS)
        assertEquals(0.3f, flipH.second, EPS)

        // Vertical flip: normV = 1 - 0.3 = 0.7
        val flipV =
            projectCutoutCoordinates(
                0.2f,
                0.3f,
                destLeft,
                destTop,
                destW,
                destH,
                srcX,
                srcY,
                srcW,
                srcH,
                rotation = 0,
                flipHorizontal = false,
                flipVertical = true,
            )
        assertEquals(0.2f, flipV!!.first, EPS)
        assertEquals(0.7f, flipV.second, EPS)

        // Both: (0.8, 0.7)
        val flipBoth =
            projectCutoutCoordinates(
                0.2f,
                0.3f,
                destLeft,
                destTop,
                destW,
                destH,
                srcX,
                srcY,
                srcW,
                srcH,
                rotation = 0,
                flipHorizontal = true,
                flipVertical = true,
            )
        assertEquals(0.8f, flipBoth!!.first, EPS)
        assertEquals(0.7f, flipBoth.second, EPS)
    }

    @Test
    fun `calculateRotatedCutoutBounds swaps dimensions and centers without overlap`() {
        val cutout =
            ScreenCutout(
                id = "cutout_1",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.2f,
                destY = 0.2f,
                destWidth = 0.4f,
                destHeight = 0.2f,
                rotation = 0,
            )
        // Center is (0.2 + 0.2 = 0.4, 0.2 + 0.1 = 0.3)
        // On 90 deg rotation: newW = 0.2, newH = 0.4
        // targetX = 0.4 - 0.1 = 0.3, targetY = 0.3 - 0.2 = 0.1
        val rotated = calculateRotatedCutoutBounds(cutout, targetRotation = 90, allCutouts = listOf(cutout))
        assertNotNull(rotated)
        assertEquals(90, rotated!!.rotation)
        assertEquals(0.2f, rotated.destWidth, EPS)
        assertEquals(0.4f, rotated.destHeight, EPS)
        assertEquals(0.3f, rotated.destX, EPS)
        assertEquals(0.1f, rotated.destY, EPS)
    }

    @Test
    fun `calculateRotatedCutoutBounds blocks rotation on collision with neighbor`() {
        val cutout1 =
            ScreenCutout(
                id = "cutout_1",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.2f,
                destY = 0.2f,
                destWidth = 0.4f,
                destHeight = 0.2f,
                rotation = 0,
            )
        // Neighbor right above cutout1's target bounds (Y=0.1 to 0.5)
        val neighbor =
            ScreenCutout(
                id = "neighbor",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.25f,
                destY = 0.05f,
                destWidth = 0.2f,
                destHeight = 0.15f,
            )
        val blocked = calculateRotatedCutoutBounds(cutout1, targetRotation = 90, allCutouts = listOf(cutout1, neighbor))
        assertNull(blocked)
    }
}
