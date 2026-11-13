package com.stormpanda.megingiard.math

import com.stormpanda.megingiard.macropad.GridMode
import com.stormpanda.megingiard.macropad.LayoutCanvasSpec
import com.stormpanda.megingiard.macropad.MP_THOR_MIN_BUTTON_SIZE_PX
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadButton
import com.stormpanda.megingiard.macropad.THOR_BOTTOM_CANVAS_SPEC
import com.stormpanda.megingiard.mirror.ScreenCutout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignmentMathTest {
    private val canvasW = 1000f
    private val canvasH = 1000f

    // ─────────────────────────────────────────────────────────────────────────
    // Generic Center Alignment Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `calculateCenterAlignmentSnap snaps center X within threshold`() {
        val otherCenters = listOf(0.5f to 0.5f)
        // 0.505 * 1000 = 505px, delta = 5px <= 10px threshold -> snaps to 0.5f
        val result =
            calculateCenterAlignmentSnap(
                rawCenterX = 0.505f,
                rawCenterY = 0.2f,
                otherCenters = otherCenters,
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )

        assertEquals(0.5f, result.snappedNormX, 0.0001f)
        assertEquals(0.2f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.contains(0.5f))
        assertTrue(result.alignedYNorms.isEmpty())
    }

    @Test
    fun `calculateCenterAlignmentSnap ignores snap when beyond threshold`() {
        val otherCenters = listOf(0.5f to 0.5f)
        // 0.520 * 1000 = 520px, delta = 20px > 10px threshold -> no snap
        val result =
            calculateCenterAlignmentSnap(
                rawCenterX = 0.520f,
                rawCenterY = 0.2f,
                otherCenters = otherCenters,
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )

        assertEquals(0.520f, result.snappedNormX, 0.0001f)
        assertEquals(0.2f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.isEmpty())
    }

    @Test
    fun `calculateCenterAlignmentSnap respects alignmentSnappingEnabled false`() {
        val otherCenters = listOf(0.5f to 0.5f)
        val result =
            calculateCenterAlignmentSnap(
                rawCenterX = 0.505f,
                rawCenterY = 0.505f,
                otherCenters = otherCenters,
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = false,
            )

        assertEquals(0.505f, result.snappedNormX, 0.0001f)
        assertEquals(0.505f, result.snappedNormY, 0.0001f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cutout-Specific Alignment Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `calculateCutoutAlignmentSnap snaps cutout destination bounds when centers align`() {
        // Cutout 1: dest bounds [0.35, 0.35, 0.3, 0.3] -> Center is (0.5, 0.5)
        val cutout1 =
            ScreenCutout(
                id = "cutout-1",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.35f,
                destY = 0.35f,
                destWidth = 0.3f,
                destHeight = 0.3f,
            )
        // Moving Cutout: width = 0.2, height = 0.2.
        // Raw destX = 0.405 -> raw CenterX = 0.405 + 0.1 = 0.505 (delta 5px <= 10px -> snaps to 0.5)
        // Snapped destX = 0.5 - 0.1 = 0.4
        val result =
            calculateCutoutAlignmentSnap(
                rawDestX = 0.405f,
                rawDestY = 0.1f,
                destWidth = 0.2f,
                destHeight = 0.2f,
                movingCutoutId = "cutout-2",
                otherCutouts = listOf(cutout1),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )

        assertEquals(0.4f, result.snappedNormX, 0.0001f)
        assertEquals(0.1f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.contains(0.5f))
    }

    @Test
    fun `calculateCutoutAlignmentSnap dual-axis center snapping with different dimensions`() {
        val cutout1 =
            ScreenCutout(
                id = "cutout-1",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.2f,
                destY = 0.2f,
                destWidth = 0.4f,
                destHeight = 0.4f,
            ) // Center = (0.4, 0.4)

        // Moving Cutout: width = 0.2, height = 0.1
        // Raw destX = 0.305 -> CenterX = 0.405 -> snaps to 0.4 -> snapped destX = 0.3
        // Raw destY = 0.345 -> CenterY = 0.395 -> snaps to 0.4 -> snapped destY = 0.35
        val result =
            calculateCutoutAlignmentSnap(
                rawDestX = 0.305f,
                rawDestY = 0.345f,
                destWidth = 0.2f,
                destHeight = 0.1f,
                movingCutoutId = "cutout-2",
                otherCutouts = listOf(cutout1),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )

        assertEquals(0.3f, result.snappedNormX, 0.0001f)
        assertEquals(0.35f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.contains(0.4f))
        assertTrue(result.alignedYNorms.contains(0.4f))
    }

    @Test
    fun `calculateGamepadCutoutMove normal 10px and fine 1px steps`() {
        val otherCutouts = emptyList<ScreenCutout>()
        // 10px normal step on 1000px canvas = +0.01f
        val (nextXNormal, _) =
            calculateGamepadCutoutMove(
                currentDestX = 0.2f,
                currentDestY = 0.2f,
                destWidth = 0.2f,
                destHeight = 0.2f,
                dirX = 1,
                dirY = 0,
                stepMultiplierPx = ALIGNMENT_NORMAL_STEP_PX,
                movingCutoutId = "cutout-1",
                otherCutouts = otherCutouts,
                canvasW = canvasW,
                canvasH = canvasH,
            )
        assertEquals(0.21f, nextXNormal, 0.0001f)

        // 1px fine step on 1000px canvas = +0.001f
        val (nextXFine, _) =
            calculateGamepadCutoutMove(
                currentDestX = 0.2f,
                currentDestY = 0.2f,
                destWidth = 0.2f,
                destHeight = 0.2f,
                dirX = 1,
                dirY = 0,
                stepMultiplierPx = ALIGNMENT_FINE_STEP_PX,
                movingCutoutId = "cutout-1",
                otherCutouts = otherCutouts,
                canvasW = canvasW,
                canvasH = canvasH,
            )
        assertEquals(0.201f, nextXFine, 0.0001f)
    }

    @Test
    fun `calculateGamepadCutoutMove crossing snap and step-off`() {
        // Cutout 1: dest bounds [0.35, 0.35, 0.3, 0.3] -> Center = 0.5
        val cutout1 =
            ScreenCutout(
                id = "cutout-1",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.35f,
                destY = 0.35f,
                destWidth = 0.3f,
                destHeight = 0.3f,
            )

        // Moving Cutout: width = 0.2. Current destX = 0.395 (CenterX = 0.495).
        // A 10px (+0.01) step would jump to CenterX = 0.505, crossing target CenterX 0.5.
        // It must snap directly to CenterX = 0.5 -> destX = 0.4.
        val (snappedDestX, _) =
            calculateGamepadCutoutMove(
                currentDestX = 0.395f,
                currentDestY = 0.1f,
                destWidth = 0.2f,
                destHeight = 0.2f,
                dirX = 1,
                dirY = 0,
                stepMultiplierPx = ALIGNMENT_NORMAL_STEP_PX,
                movingCutoutId = "cutout-2",
                otherCutouts = listOf(cutout1),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )
        assertEquals(0.4f, snappedDestX, 0.0001f)

        // On the next step from destX = 0.4, it steps off the alignment coordinate to destX = 0.41
        val (steppedOffDestX, _) =
            calculateGamepadCutoutMove(
                currentDestX = 0.4f,
                currentDestY = 0.1f,
                destWidth = 0.2f,
                destHeight = 0.2f,
                dirX = 1,
                dirY = 0,
                stepMultiplierPx = ALIGNMENT_NORMAL_STEP_PX,
                movingCutoutId = "cutout-2",
                otherCutouts = listOf(cutout1),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )
        assertEquals(0.41f, steppedOffDestX, 0.0001f)
    }

    @Test
    fun `findAlignedCutoutCenterGuides discovers matching cutout center guides`() {
        val cutout1 =
            ScreenCutout(
                id = "cutout-1",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.4f,
                destY = 0.1f,
                destWidth = 0.2f,
                destHeight = 0.2f,
            ) // Center = (0.5, 0.2)

        val cutout2 =
            ScreenCutout(
                id = "cutout-2",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 1f,
                srcHeight = 1f,
                destX = 0.35f,
                destY = 0.65f,
                destWidth = 0.3f,
                destHeight = 0.3f,
            ) // Center = (0.5, 0.8)

        val (alignedXs, alignedYs) =
            findAlignedCutoutCenterGuides(
                activeCutoutId = "cutout-1",
                cutouts = listOf(cutout1, cutout2),
                canvasW = canvasW,
                canvasH = canvasH,
            )

        assertEquals(1, alignedXs.size)
        assertEquals(0.5f, alignedXs[0], 0.0001f)
        assertEquals(0, alignedYs.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Button-Specific Alignment Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `calculateButtonAlignmentSnap snaps button center and discovers guides`() {
        val btn1 = PadButton(id = "btn-1", posX = 0.5f, posY = 0.5f, label = "A", action = PadAction.KeyboardKey(30, "A"))
        val btn2 = PadButton(id = "btn-2", posX = 0.2f, posY = 0.8f, label = "B", action = PadAction.KeyboardKey(31, "B"))

        val result =
            calculateButtonAlignmentSnap(
                rawNormX = 0.505f,
                rawNormY = 0.3f,
                movingButtonId = "btn-moving",
                otherButtons = listOf(btn1, btn2),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )

        assertEquals(0.5f, result.snappedNormX, 0.0001f)
        assertEquals(0.3f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.contains(0.5f))
    }

    @Test
    fun `calculateButtonAlignmentSnap falls back to grid snap on non-aligned axes`() {
        val btn1 = PadButton(id = "btn-1", posX = 0.5f, posY = 0.5f, label = "A", action = PadAction.KeyboardKey(30, "A"))
        val result =
            calculateButtonAlignmentSnap(
                rawNormX = 0.505f,
                rawNormY = 0.23f,
                movingButtonId = "btn-moving",
                otherButtons = listOf(btn1),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
                gridMode = GridMode.RECTANGULAR,
                gridStepPx = 100f,
            )

        // Snaps to btn1 on X (0.5f)
        assertEquals(0.5f, result.snappedNormX, 0.0001f)
        // Snaps to nearest 100px grid on Y (centered on 0.5f: 0.23 * 1000 = 230px -> snapped to 200px = 0.2f)
        assertEquals(0.2f, result.snappedNormY, 0.0001f)
    }

    @Test
    fun `calculateButtonAlignmentSnap allows button center to reach canvas boundary edges at 0f and 1f`() {
        val resultLeftTop =
            calculateButtonAlignmentSnap(
                rawNormX = -0.1f,
                rawNormY = -0.2f,
                movingButtonId = "btn-moving",
                otherButtons = emptyList(),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = false,
            )
        assertEquals(0f, resultLeftTop.snappedNormX, 0.0001f)
        assertEquals(0f, resultLeftTop.snappedNormY, 0.0001f)

        val resultRightBottom =
            calculateButtonAlignmentSnap(
                rawNormX = 1.2f,
                rawNormY = 1.3f,
                movingButtonId = "btn-moving",
                otherButtons = emptyList(),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = false,
            )
        assertEquals(1f, resultRightBottom.snappedNormX, 0.0001f)
        assertEquals(1f, resultRightBottom.snappedNormY, 0.0001f)
    }

    @Test
    fun `calculateGamepadButtonMove allows button center to reach canvas boundary edges at 0f and 1f`() {
        val (leftX, topY) =
            calculateGamepadButtonMove(
                currentNormX = 0.01f,
                currentNormY = 0.01f,
                dirX = -1,
                dirY = -1,
                stepMultiplierPx = 100f,
                movingButtonId = "btn-moving",
                otherButtons = emptyList(),
                canvasW = 1000f,
                canvasH = 1000f,
                alignmentSnappingEnabled = false,
            )
        assertEquals(0f, leftX, 0.0001f)
        assertEquals(0f, topY, 0.0001f)

        val (rightX, bottomY) =
            calculateGamepadButtonMove(
                currentNormX = 0.99f,
                currentNormY = 0.99f,
                dirX = 1,
                dirY = 1,
                stepMultiplierPx = 100f,
                movingButtonId = "btn-moving",
                otherButtons = emptyList(),
                canvasW = 1000f,
                canvasH = 1000f,
                alignmentSnappingEnabled = false,
            )
        assertEquals(1f, rightX, 0.0001f)
        assertEquals(1f, bottomY, 0.0001f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Design-Canvas Constants, Grid Snapping & Active Black-Bar Bounds Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `canvas and resize pixel constants match landscape Thor authoring contract`() {
        assertEquals(1240f, ALIGNMENT_CANVAS_WIDTH_PX, 0.001f)
        assertEquals(1080f, ALIGNMENT_CANVAS_HEIGHT_PX, 0.001f)
        assertEquals(1240f, MPE_CANVAS_WIDTH_PX, 0.001f)
        assertEquals(1080f, MPE_CANVAS_HEIGHT_PX, 0.001f)
        assertEquals(10f, MPE_RESIZE_NORMAL_STEP_PX, 0.001f)
        assertEquals(1f, MPE_RESIZE_FINE_STEP_PX, 0.001f)
        assertEquals(9f, BUTTON_ASPECT_SNAP_THRESHOLD_PX, 0.001f)
    }

    @Test
    fun `snapPosition snaps to 68 px grid on Thor and 60 px grid on RP Duo`() {
        val thorSpec = THOR_BOTTOM_CANVAS_SPEC
        val thorW = thorSpec.widthPx.toFloat()
        val thorH = thorSpec.heightPx.toFloat()
        // Center is (620, 540). Offset (+65, -70) snaps to (+68, -68) = (688, 472)
        val (thorSnapX, thorSnapY) =
            snapPosition(
                rawNormX = (620f + 65f) / thorW,
                rawNormY = (540f - 70f) / thorH,
                canvasW = thorW,
                canvasH = thorH,
                gridMode = GridMode.RECTANGULAR,
                gridStepPx = thorSpec.minButtonSizePx,
            )
        assertEquals(688f / thorW, thorSnapX, 0.0001f)
        assertEquals(472f / thorH, thorSnapY, 0.0001f)

        val duoSpec = LayoutCanvasSpec(1280, 960)
        val duoW = duoSpec.widthPx.toFloat()
        val duoH = duoSpec.heightPx.toFloat()
        // Center is (640, 480). Offset (+58, +122) snaps to (+60, +120) = (700, 600)
        val (duoSnapX, duoSnapY) =
            snapPosition(
                rawNormX = (640f + 58f) / duoW,
                rawNormY = (480f + 122f) / duoH,
                canvasW = duoW,
                canvasH = duoH,
                gridMode = GridMode.RECTANGULAR,
                gridStepPx = duoSpec.minButtonSizePx,
            )
        assertEquals(700f / duoW, duoSnapX, 0.0001f)
        assertEquals(600f / duoH, duoSnapY, 0.0001f)

        // Radial snap on RP Duo: point near 2nd ring (radius = 120 px, even ring -> cardinal 0 deg)
        val (duoRadialX, duoRadialY) =
            snapPosition(
                rawNormX = (640f + 118f) / duoW,
                rawNormY = 480f / duoH,
                canvasW = duoW,
                canvasH = duoH,
                gridMode = GridMode.RADIAL,
                gridStepPx = duoSpec.minButtonSizePx,
            )
        assertEquals((640f + 120f) / duoW, duoRadialX, 0.0001f)
        assertEquals(480f / duoH, duoRadialY, 0.0001f)
    }

    @Test
    fun `button move and alignment snap allow active black-bar bounds and preserve bar positions on narrower screen`() {
        // Thor 1240x1080 layout rendered on RP Duo 1280x960 -> X bounds ~ [-0.080645..1.080645]
        val viewport = ViewportMath.calculateDesignViewport(1280f, 960f, 1240f, 1080f)

        val snapIntoLeftBar =
            calculateButtonAlignmentSnap(
                rawNormX = -0.05f,
                rawNormY = 0.5f,
                movingButtonId = "btn-bar",
                otherButtons = emptyList(),
                canvasW = 1240f,
                canvasH = 1080f,
                alignmentSnappingEnabled = false,
                minNormX = viewport.minNormX,
                maxNormX = viewport.maxNormX,
                minNormY = viewport.minNormY,
                maxNormY = viewport.maxNormY,
            )
        assertEquals(-0.05f, snapIntoLeftBar.snappedNormX, 0.0001f)

        val snapClampPastBar =
            calculateButtonAlignmentSnap(
                rawNormX = -0.20f,
                rawNormY = 0.5f,
                movingButtonId = "btn-bar",
                otherButtons = emptyList(),
                canvasW = 1240f,
                canvasH = 1080f,
                alignmentSnappingEnabled = false,
                minNormX = viewport.minNormX,
                maxNormX = viewport.maxNormX,
                minNormY = viewport.minNormY,
                maxNormY = viewport.maxNormY,
            )
        assertEquals(viewport.minNormX, snapClampPastBar.snappedNormX, 0.0001f)

        // When opened back on native Thor (0..1), moving an existing bar button vertically preserves its -0.05f X
        val barBtn = PadButton(id = "btn-bar", posX = -0.05f, posY = 0.5f, label = "Bar", action = PadAction.KeyboardKey(30, "A"))
        val snapVerticalOnThor =
            calculateButtonAlignmentSnap(
                rawNormX = -0.05f,
                rawNormY = 0.6f,
                movingButtonId = "btn-bar",
                otherButtons = listOf(barBtn),
                canvasW = 1240f,
                canvasH = 1080f,
                alignmentSnappingEnabled = false,
            )
        assertEquals(-0.05f, snapVerticalOnThor.snappedNormX, 0.0001f)
        assertEquals(0.6f, snapVerticalOnThor.snappedNormY, 0.0001f)

        val (gpX, gpY) =
            calculateGamepadButtonMove(
                currentNormX = -0.05f,
                currentNormY = 0.5f,
                dirX = 0,
                dirY = 1,
                stepMultiplierPx = 10f,
                movingButtonId = "btn-bar",
                otherButtons = listOf(barBtn),
                canvasW = 1240f,
                canvasH = 1080f,
                alignmentSnappingEnabled = false,
            )
        assertEquals(-0.05f, gpX, 0.0001f)
        assertEquals(0.5f + 10f / 1080f, gpY, 0.0001f)
    }

    @Test
    fun `cutout move and alignment snap allow active black-bar bounds and preserve bar positions on narrower screen`() {
        val viewport = ViewportMath.calculateDesignViewport(1280f, 960f, 1240f, 1080f)

        val snapIntoRightBar =
            calculateCutoutAlignmentSnap(
                rawDestX = 0.85f,
                rawDestY = 0.2f,
                destWidth = 0.2f,
                destHeight = 0.2f,
                movingCutoutId = "cut-bar",
                otherCutouts = emptyList(),
                canvasW = 1240f,
                canvasH = 1080f,
                alignmentSnappingEnabled = false,
                minNormX = viewport.minNormX,
                maxNormX = viewport.maxNormX,
                minNormY = viewport.minNormY,
                maxNormY = viewport.maxNormY,
            )
        // Right edge is 0.85 + 0.20 = 1.05 <= maxNormX (1.080645), so it stays at 0.85f
        assertEquals(0.85f, snapIntoRightBar.snappedNormX, 0.0001f)

        val barCutout =
            ScreenCutout(
                id = "cut-bar",
                srcX = 0f,
                srcY = 0f,
                srcWidth = 0.5f,
                srcHeight = 0.5f,
                destX = -0.06f,
                destY = 0.2f,
                destWidth = 0.2f,
                destHeight = 0.2f,
            )
        // Opened on narrower Thor (0..1): moving vertically preserves destX = -0.06f without jumping to 0f
        val (movedDestX, movedDestY) =
            calculateGamepadCutoutMove(
                currentDestX = -0.06f,
                currentDestY = 0.2f,
                destWidth = 0.2f,
                destHeight = 0.2f,
                dirX = 0,
                dirY = 1,
                stepMultiplierPx = 10f,
                movingCutoutId = "cut-bar",
                otherCutouts = listOf(barCutout),
                canvasW = 1240f,
                canvasH = 1080f,
                alignmentSnappingEnabled = false,
            )
        assertEquals(-0.06f, movedDestX, 0.0001f)
        assertEquals(0.2f + 10f / 1080f, movedDestY, 0.0001f)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stepless Button Resize Tests (Design-Canvas Pixels)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `calculateButtonEdgeResize RIGHT expands width rightward keeping left edge fixed`() {
        // Center: 500px, Width: 120px -> Left: 440px, Right: 560px
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = 40f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )

        // New right: 560 + 40 = 600px. Width: 160px. Center: 440 + 80 = 520px = 0.52f
        assertEquals(160f, result.newWidthPx, 0.01f)
        assertEquals(120f, result.newHeightPx, 0.01f)
        assertEquals(0.52f, result.newNormX, 0.0001f)
        assertEquals(0.5f, result.newNormY, 0.0001f)
    }

    @Test
    fun `calculateButtonEdgeResize LEFT expands width leftward keeping right edge fixed`() {
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.LEFT,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = -40f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )

        // New left: 440 - 40 = 400px. Width: 560 - 400 = 160px. Center: 560 - 80 = 480px = 0.48f
        assertEquals(160f, result.newWidthPx, 0.01f)
        assertEquals(120f, result.newHeightPx, 0.01f)
        assertEquals(0.48f, result.newNormX, 0.0001f)
        assertEquals(0.5f, result.newNormY, 0.0001f)
    }

    @Test
    fun `calculateButtonEdgeResize BOTTOM expands height downward keeping top edge fixed`() {
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.BOTTOM,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = 40f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )

        assertEquals(120f, result.newWidthPx, 0.01f)
        assertEquals(160f, result.newHeightPx, 0.01f)
        assertEquals(0.5f, result.newNormX, 0.0001f)
        assertEquals(0.52f, result.newNormY, 0.0001f)
    }

    @Test
    fun `calculateButtonEdgeResize TOP expands height upward keeping bottom edge fixed`() {
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.TOP,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = -40f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )

        assertEquals(120f, result.newWidthPx, 0.01f)
        assertEquals(160f, result.newHeightPx, 0.01f)
        assertEquals(0.5f, result.newNormX, 0.0001f)
        assertEquals(0.48f, result.newNormY, 0.0001f)
    }

    @Test
    fun `calculateButtonEdgeResize clamps shrinking to designCanvas minimum on Thor and RP Duo`() {
        val thorShrink =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 136f,
                originalHeightPx = 136f,
                dragDeltaDesignPx = -200f,
                designCanvasW = 1240f,
                designCanvasH = 1080f,
            )
        assertEquals(MP_THOR_MIN_BUTTON_SIZE_PX, thorShrink.newWidthPx, 0.01f)

        val duoShrink =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = -200f,
                designCanvasW = 1280f,
                designCanvasH = 960f,
                minWidthPx = 60f,
            )
        assertEquals(60f, duoShrink.newWidthPx, 0.01f)

        val scrollWheelShrink =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.BOTTOM,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 136f,
                originalHeightPx = 300f,
                dragDeltaDesignPx = -200f,
                designCanvasW = 1240f,
                designCanvasH = 1080f,
                minWidthPx = 68f,
                minHeightPx = 272f,
            )
        assertEquals(272f, scrollWheelShrink.newHeightPx, 0.01f)
    }

    @Test
    fun `calculateButtonEdgeResize clamps expanding past canvas boundaries and permits active black bars`() {
        // Left is at 440px on 1000px canvas -> max width is 1000 - 440 = 560px
        val rightResult =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = 2000f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )
        assertEquals(560f, rightResult.newWidthPx, 0.01f)

        // Right is at 560px on 1000px canvas -> max width expanding left is 560px
        val leftResult =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.LEFT,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = -2000f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )
        assertEquals(560f, leftResult.newWidthPx, 0.01f)

        // Top is at 440px on 1000px canvas -> max height expanding down is 560px
        val bottomResult =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.BOTTOM,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = 2000f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )
        assertEquals(560f, bottomResult.newHeightPx, 0.01f)

        // Bottom is at 560px on 1000px canvas -> max height expanding up is 560px
        val topResult =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.TOP,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = -2000f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )
        assertEquals(560f, topResult.newHeightPx, 0.01f)

        // When host bounds extend into side bars [-100 px .. 1340 px] on a 1240 px design canvas,
        // left edge at 1104 px can expand rightward up to 1340 - 1104 = 236 px
        val barExpandResult =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = (1104f + 68f) / 1240f,
                originalNormY = 0.5f,
                originalWidthPx = 136f,
                originalHeightPx = 136f,
                dragDeltaDesignPx = 500f,
                designCanvasW = 1240f,
                designCanvasH = 1080f,
                minNormX = -100f / 1240f,
                maxNormX = 1340f / 1240f,
            )
        assertEquals(236f, barExpandResult.newWidthPx, 0.01f)
    }

    @Test
    fun `calculateGamepadButtonResize updates dimensions in pixel steps and clamps to limits`() {
        // Expand width with dirX = 1, normal step 10 px
        val res1 =
            calculateGamepadButtonResize(
                currentNormX = 0.5f,
                currentNormY = 0.5f,
                currentWidthPx = 136f,
                currentHeightPx = 136f,
                dirX = 1,
                dirY = 0,
                stepPx = MPE_RESIZE_NORMAL_STEP_PX,
                designCanvasW = 500f,
                designCanvasH = 500f,
            )
        assertEquals(146f, res1.newWidthPx, 0.01f)
        assertEquals(136f, res1.newHeightPx, 0.01f)
        assertEquals(0.5f, res1.newNormX, 0.01f)
        assertEquals(0.5f, res1.newNormY, 0.01f)

        // Expand height with D-pad UP (dirY = -1), fine step 1 px
        val res2 =
            calculateGamepadButtonResize(
                currentNormX = 0.5f,
                currentNormY = 0.5f,
                currentWidthPx = 136f,
                currentHeightPx = 136f,
                dirX = 0,
                dirY = -1,
                stepPx = MPE_RESIZE_FINE_STEP_PX,
                designCanvasW = 500f,
                designCanvasH = 500f,
            )
        assertEquals(136f, res2.newWidthPx, 0.01f)
        assertEquals(137f, res2.newHeightPx, 0.01f)

        // Shrink beyond minimum: clamped to 68 px (Thor default) or 60 px (RP Duo)
        val res3 =
            calculateGamepadButtonResize(
                currentNormX = 0.5f,
                currentNormY = 0.5f,
                currentWidthPx = 136f,
                currentHeightPx = 136f,
                dirX = -20,
                dirY = 20,
                stepPx = MPE_RESIZE_NORMAL_STEP_PX,
                designCanvasW = 500f,
                designCanvasH = 500f,
            )
        assertEquals(68f, res3.newWidthPx, 0.01f)
        assertEquals(68f, res3.newHeightPx, 0.01f)

        val resDuo =
            calculateGamepadButtonResize(
                currentNormX = 0.5f,
                currentNormY = 0.5f,
                currentWidthPx = 120f,
                currentHeightPx = 120f,
                dirX = -20,
                dirY = 20,
                stepPx = MPE_RESIZE_NORMAL_STEP_PX,
                designCanvasW = 1280f,
                designCanvasH = 960f,
                minWidthPx = 60f,
            )
        assertEquals(60f, resDuo.newWidthPx, 0.01f)
        assertEquals(60f, resDuo.newHeightPx, 0.01f)
    }

    @Test
    fun `calculateGamepadButtonResize with lockAspect updates both dimensions equally`() {
        val result =
            calculateGamepadButtonResize(
                currentNormX = 0.5f,
                currentNormY = 0.5f,
                currentWidthPx = 272f,
                currentHeightPx = 272f,
                dirX = 1,
                dirY = 0,
                stepPx = MPE_RESIZE_NORMAL_STEP_PX,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
                lockAspect = true,
            )
        assertEquals(282f, result.newWidthPx, 0.01f)
        assertEquals(282f, result.newHeightPx, 0.01f)
    }

    @Test
    fun `calculateGamepadButtonResize preserves center coordinate when button is partially offscreen or in bars`() {
        // Button at normX = 0.03f on 1000px canvas -> centerX = 30px
        val result =
            calculateGamepadButtonResize(
                currentNormX = 0.03f,
                currentNormY = 0.5f,
                currentWidthPx = 120f,
                currentHeightPx = 120f,
                dirX = 1,
                dirY = 0,
                stepPx = 40f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )
        assertEquals(160f, result.newWidthPx, 0.01f)
        assertEquals(0.03f, result.newNormX, 0.001f)

        // Button placed in wider-screen left bar (-0.06f) opened on narrower screen (0..1): center stays -0.06f
        val barResult =
            calculateGamepadButtonResize(
                currentNormX = -0.06f,
                currentNormY = 0.5f,
                currentWidthPx = 120f,
                currentHeightPx = 120f,
                dirX = 1,
                dirY = 0,
                stepPx = 10f,
                designCanvasW = 1240f,
                designCanvasH = 1080f,
            )
        assertEquals(130f, barResult.newWidthPx, 0.01f)
        assertEquals(-0.06f, barResult.newNormX, 0.001f)
    }

    @Test
    fun `calculateGamepadButtonResize preserves center coordinates at screen edges`() {
        // Button at normX = 0.0f (50% offscreen left)
        val resLeft =
            calculateGamepadButtonResize(
                currentNormX = 0.0f,
                currentNormY = 0.5f,
                currentWidthPx = 120f,
                currentHeightPx = 120f,
                dirX = 1,
                dirY = 0,
                stepPx = 20f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )
        assertEquals(140f, resLeft.newWidthPx, 0.01f)
        assertEquals(0.0f, resLeft.newNormX, 0.001f)

        // Button at normX = 1.0f (50% offscreen right)
        val resRight =
            calculateGamepadButtonResize(
                currentNormX = 1.0f,
                currentNormY = 0.5f,
                currentWidthPx = 120f,
                currentHeightPx = 120f,
                dirX = 1,
                dirY = 0,
                stepPx = 20f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )
        assertEquals(140f, resRight.newWidthPx, 0.01f)
        assertEquals(1.0f, resRight.newNormX, 0.001f)
    }

    @Test
    fun `calculateButtonEdgeResize snaps to 1 to 1 aspect ratio when within 9 px threshold`() {
        // Start: width 160px, height 136px. Drag RIGHT by -18px -> candidate width = 142px.
        // abs(142 - 136) = 6px <= 9px threshold -> snaps to exactly 136px!
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 160f,
                originalHeightPx = 136f,
                dragDeltaDesignPx = -18f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )

        assertEquals(136f, result.newWidthPx, 0.01f)
        assertEquals(136f, result.newHeightPx, 0.01f)
        assertTrue(result.isAspectSnapped)
    }

    @Test
    fun `calculateButtonEdgeResize does not snap when outside 9 px threshold`() {
        // Start: width 160px, height 136px. Drag RIGHT by -12px -> candidate width = 148px.
        // 148px - 136px = 12px > 9px threshold -> does not snap.
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 160f,
                originalHeightPx = 136f,
                dragDeltaDesignPx = -12f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
            )

        assertEquals(148f, result.newWidthPx, 0.01f)
        assertEquals(136f, result.newHeightPx, 0.01f)
        assertFalse(result.isAspectSnapped)
    }

    @Test
    fun `calculateButtonEdgeResize does not snap to 1 to 1 if snapped dimension exceeds available canvas boundary`() {
        // Canvas width 500. Button: originalWidthPx = 190f, originalHeightPx = 200f.
        // Anchor (left edge) = 304px. Max allowed width = 500 - 304 = 196px.
        // Drag RIGHT by +4px -> candidate width = 194px.
        // abs(194 - 200) = 6px <= 9px threshold, BUT target 200px exceeds maxAllowed (196px).
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = (304f + 190f / 2f) / 500f,
                originalNormY = 0.5f,
                originalWidthPx = 190f,
                originalHeightPx = 200f,
                dragDeltaDesignPx = 4f,
                designCanvasW = 500f,
                designCanvasH = 500f,
            )

        assertEquals(194f, result.newWidthPx, 0.01f)
        assertFalse(result.isAspectSnapped)
    }

    @Test
    fun `calculateButtonEdgeResize with lockAspect resizes both dimensions symmetrically`() {
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 0.5f,
                originalNormY = 0.5f,
                originalWidthPx = 272f,
                originalHeightPx = 272f,
                dragDeltaDesignPx = 28f,
                designCanvasW = 1000f,
                designCanvasH = 1000f,
                lockAspect = true,
            )

        assertEquals(300f, result.newWidthPx, 0.01f)
        assertEquals(300f, result.newHeightPx, 0.01f)
        assertEquals(0.514f, result.newNormX, 0.001f)
        assertEquals(0.5f, result.newNormY, 0.001f)
    }

    @Test
    fun `formatButtonDimensionPx formats as integer string`() {
        assertEquals("60", formatButtonDimensionPx(60.0f))
        assertEquals("61", formatButtonDimensionPx(60.6f))
        assertEquals("60", formatButtonDimensionPx(60.4f))
        assertEquals("68", formatButtonDimensionPx(68.0f))
        assertEquals("136", formatButtonDimensionPx(136.0f))
        assertEquals("272", formatButtonDimensionPx(272.0f))
    }

    @Test
    fun `calculateButtonEdgeResize with lockAspect floors cleanly when other axis has fractional pixels`() {
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 0.25f,
                originalNormY = 0.5f,
                originalWidthPx = 272f,
                originalHeightPx = 272f,
                dragDeltaDesignPx = 2000f,
                designCanvasW = 1000f,
                designCanvasH = 500.7f,
                lockAspect = true,
            )

        assertEquals(500f, result.newWidthPx, 0.01f)
        assertEquals(500f, result.newHeightPx, 0.01f)
        assertTrue(result.newHeightPx <= 500.7f)
        assertTrue(result.newNormY in 0f..1f)
    }

    @Test
    fun `calculateButtonEdgeResize keeps anchored edge fixed when clamped at fractional canvas boundary`() {
        val originalWidthPx = 136f
        val canvasW = 902.5f
        val leftEdgePx = 100f
        val originalNormX = (leftEdgePx + originalWidthPx / 2f) / canvasW

        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = originalNormX,
                originalNormY = 0.5f,
                originalWidthPx = originalWidthPx,
                originalHeightPx = 136f,
                dragDeltaDesignPx = 5000f,
                designCanvasW = canvasW,
                designCanvasH = 1000f,
            )

        assertEquals(802f, result.newWidthPx, 0.01f)
        val newLeftPx = result.newNormX * canvasW - result.newWidthPx / 2f
        assertEquals(leftEdgePx, newLeftPx, 0.01f)
    }

    @Test
    fun `calculateGamepadButtonResize does not throw when canvas is smaller than minimum button size`() {
        val result =
            calculateGamepadButtonResize(
                currentNormX = 0.5f,
                currentNormY = 0.5f,
                currentWidthPx = 68f,
                currentHeightPx = 68f,
                dirX = 1,
                dirY = -1,
                stepPx = MPE_RESIZE_NORMAL_STEP_PX,
                designCanvasW = 20f,
                designCanvasH = 20f,
            )

        assertEquals(68f, result.newWidthPx, 0.01f)
        assertEquals(0.5f, result.newNormX, 0.001f)
        assertEquals(0.5f, result.newNormY, 0.001f)
    }

    @Test
    fun `calculateButtonEdgeResize does not throw with undersized anchor span near boundary`() {
        val canvasW = 500f
        // Anchor left edge at 490 px on a 500 px canvas (only 10 px remaining, < minWidthPx = 68 px)
        val originalWidthPx = 68f
        val leftEdgePx = 490f
        val originalNormX = (leftEdgePx + originalWidthPx / 2f) / canvasW

        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = originalNormX,
                originalNormY = 0.5f,
                originalWidthPx = originalWidthPx,
                originalHeightPx = 136f,
                dragDeltaDesignPx = 100f,
                designCanvasW = canvasW,
                designCanvasH = 500f,
            )

        assertEquals(68f, result.newWidthPx, 0.01f)
    }

    @Test
    fun `calculateButtonEdgeResize preserves partially offscreen and bar-placed button width without jumping`() {
        // Button at normX = 1.0f (50% offscreen right), width = 120px on 1000px canvas
        val resultRightZeroDrag =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 1.0f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = 0f,
                designCanvasW = 1000f,
                designCanvasH = 500f,
            )
        assertEquals(120f, resultRightZeroDrag.newWidthPx, 0.01f)
        assertEquals(1.0f, resultRightZeroDrag.newNormX, 0.001f)

        // Dragging left handle expands leftward into the canvas
        val resultLeftExpand =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.LEFT,
                originalNormX = 1.0f,
                originalNormY = 0.5f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = -40f,
                designCanvasW = 1000f,
                designCanvasH = 500f,
            )
        assertEquals(160f, resultLeftExpand.newWidthPx, 0.01f)
        // Original right edge anchored at 1000 + 60 = 1060. New left edge at 900. New center = 980 -> normX = 0.98f
        assertEquals(0.98f, resultLeftExpand.newNormX, 0.001f)

        // Button placed in left bar (-0.05f, center = -50px, width = 100px -> [-100..0]) viewed on narrower screen (0..1)
        val resultBarZeroDrag =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.LEFT,
                originalNormX = -0.05f,
                originalNormY = 0.5f,
                originalWidthPx = 100f,
                originalHeightPx = 100f,
                dragDeltaDesignPx = 0f,
                designCanvasW = 1000f,
                designCanvasH = 500f,
            )
        assertEquals(100f, resultBarZeroDrag.newWidthPx, 0.01f)
        assertEquals(-0.05f, resultBarZeroDrag.newNormX, 0.001f)

        // Dragging right handle inward (+40px) keeps left edge at -100px -> new width 140px, new center -30px = -0.03f
        val resultBarRightExpand =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = -0.05f,
                originalNormY = 0.5f,
                originalWidthPx = 100f,
                originalHeightPx = 100f,
                dragDeltaDesignPx = 40f,
                designCanvasW = 1000f,
                designCanvasH = 500f,
            )
        assertEquals(140f, resultBarRightExpand.newWidthPx, 0.01f)
        assertEquals(-0.03f, resultBarRightExpand.newNormX, 0.001f)
    }

    @Test
    fun `calculateButtonEdgeResize with lockAspect preserves partially offscreen other axis`() {
        // Button at normY = 1.0f (50% offscreen bottom), width = 120px, height = 120px
        val result =
            calculateButtonEdgeResize(
                handle = ButtonResizeHandle.RIGHT,
                originalNormX = 0.5f,
                originalNormY = 1.0f,
                originalWidthPx = 120f,
                originalHeightPx = 120f,
                dragDeltaDesignPx = 20f,
                designCanvasW = 1000f,
                designCanvasH = 500f,
                lockAspect = true,
            )
        assertEquals(140f, result.newWidthPx, 0.01f)
        assertEquals(140f, result.newHeightPx, 0.01f)
        // Other axis (Y) must stay at 1.0f rather than jumping inward
        assertEquals(1.0f, result.newNormY, 0.001f)
    }
}
