package com.stormpanda.megingiard.math

import com.stormpanda.megingiard.macropad.GridMode
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadButton
import com.stormpanda.megingiard.mirror.ScreenCutout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
}
