package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonAlignmentMathTest {
    private val canvasW = 1000f
    private val canvasH = 1000f

    private fun createButton(
        id: String,
        posX: Float,
        posY: Float,
    ) = PadButton(
        id = id,
        label = id,
        posX = posX,
        posY = posY,
        action = PadAction.KeyboardKey(keycode = 30, label = "A"),
    )

    @Test
    fun calculateButtonAlignmentSnap_snapsXWhenWithinThreshold() {
        val target = createButton("target", 0.5f, 0.5f)
        val other = createButton("other", 0.505f, 0.2f) // 5px away on 1000px canvas (threshold = 10px)

        val result =
            calculateButtonAlignmentSnap(
                rawNormX = target.posX,
                rawNormY = target.posY,
                movingButtonId = target.id,
                otherButtons = listOf(target, other),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
                gridMode = GridMode.OFF,
            )

        assertEquals(0.505f, result.snappedNormX, 0.0001f)
        assertEquals(0.5f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.contains(0.505f))
        assertTrue(result.alignedYNorms.isEmpty())
    }

    @Test
    fun calculateButtonAlignmentSnap_snapsYWhenWithinThreshold() {
        val target = createButton("target", 0.2f, 0.5f)
        val other = createButton("other", 0.8f, 0.508f) // 8px away on 1000px canvas

        val result =
            calculateButtonAlignmentSnap(
                rawNormX = target.posX,
                rawNormY = target.posY,
                movingButtonId = target.id,
                otherButtons = listOf(target, other),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
                gridMode = GridMode.OFF,
            )

        assertEquals(0.2f, result.snappedNormX, 0.0001f)
        assertEquals(0.508f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.isEmpty())
        assertTrue(result.alignedYNorms.contains(0.508f))
    }

    @Test
    fun calculateButtonAlignmentSnap_snapsBothXAndYSimultaneously() {
        val target = createButton("target", 0.306f, 0.704f)
        val other1 = createButton("other1", 0.300f, 0.100f) // 6px away in X
        val other2 = createButton("other2", 0.800f, 0.700f) // 4px away in Y

        val result =
            calculateButtonAlignmentSnap(
                rawNormX = target.posX,
                rawNormY = target.posY,
                movingButtonId = target.id,
                otherButtons = listOf(target, other1, other2),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
                gridMode = GridMode.OFF,
            )

        assertEquals(0.300f, result.snappedNormX, 0.0001f)
        assertEquals(0.700f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.contains(0.300f))
        assertTrue(result.alignedYNorms.contains(0.700f))
    }

    @Test
    fun calculateButtonAlignmentSnap_doesNotSnapWhenOutsideThreshold() {
        val target = createButton("target", 0.5f, 0.5f)
        val other = createButton("other", 0.55f, 0.55f) // 50px away

        val result =
            calculateButtonAlignmentSnap(
                rawNormX = target.posX,
                rawNormY = target.posY,
                movingButtonId = target.id,
                otherButtons = listOf(target, other),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
                gridMode = GridMode.OFF,
            )

        assertEquals(0.5f, result.snappedNormX, 0.0001f)
        assertEquals(0.5f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.isEmpty())
        assertTrue(result.alignedYNorms.isEmpty())
    }

    @Test
    fun calculateButtonAlignmentSnap_doesNotSnapWhenDisabled() {
        val target = createButton("target", 0.5f, 0.5f)
        val other = createButton("other", 0.505f, 0.505f) // 5px away

        val result =
            calculateButtonAlignmentSnap(
                rawNormX = target.posX,
                rawNormY = target.posY,
                movingButtonId = target.id,
                otherButtons = listOf(target, other),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = false,
                gridMode = GridMode.OFF,
            )

        assertEquals(0.5f, result.snappedNormX, 0.0001f)
        assertEquals(0.5f, result.snappedNormY, 0.0001f)
        // Since it's 5px away and tolerance is 2px, no visual guide
        assertTrue(result.alignedXNorms.isEmpty())
        assertTrue(result.alignedYNorms.isEmpty())
    }

    @Test
    fun calculateButtonAlignmentSnap_showsVisualGuideWhenExactlyAlignedEvenIfSnappingDisabled() {
        val target = createButton("target", 0.5f, 0.5f)
        val other = createButton("other", 0.5f, 0.2f) // Exactly aligned in X

        val result =
            calculateButtonAlignmentSnap(
                rawNormX = target.posX,
                rawNormY = target.posY,
                movingButtonId = target.id,
                otherButtons = listOf(target, other),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = false,
                gridMode = GridMode.OFF,
            )

        assertEquals(0.5f, result.snappedNormX, 0.0001f)
        assertEquals(0.5f, result.snappedNormY, 0.0001f)
        assertTrue(result.alignedXNorms.contains(0.5f))
    }

    @Test
    fun calculateGamepadButtonMove_normalStepMovesByDefaultStepSize() {
        val target = createButton("target", 0.5f, 0.5f)
        val (nextX, nextY) =
            calculateGamepadButtonMove(
                currentNormX = target.posX,
                currentNormY = target.posY,
                dirX = 1,
                dirY = 0,
                stepMultiplierPx = MPE_NORMAL_STEP_PX, // 10px
                movingButtonId = target.id,
                otherButtons = listOf(target),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )

        assertEquals(0.5f + 10f / canvasW, nextX, 0.0001f)
        assertEquals(0.5f, nextY, 0.0001f)
    }

    @Test
    fun calculateGamepadButtonMove_fineStepMovesBySinglePixel() {
        val target = createButton("target", 0.5f, 0.5f)
        val (nextX, nextY) =
            calculateGamepadButtonMove(
                currentNormX = target.posX,
                currentNormY = target.posY,
                dirX = 0,
                dirY = -1,
                stepMultiplierPx = MPE_FINE_STEP_PX, // 1px
                movingButtonId = target.id,
                otherButtons = listOf(target),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )

        assertEquals(0.5f, nextX, 0.0001f)
        assertEquals(0.5f - 1f / canvasH, nextY, 0.0001f)
    }

    @Test
    fun calculateGamepadButtonMove_snapsWhenCrossingAlignmentAxis() {
        val target = createButton("target", 0.495f, 0.5f) // 495px on 1000px canvas
        val other = createButton("other", 0.502f, 0.2f) // 502px on 1000px canvas
        // Moving right by 10px would reach 505px, crossing 502px!

        val (nextX, nextY) =
            calculateGamepadButtonMove(
                currentNormX = target.posX,
                currentNormY = target.posY,
                dirX = 1,
                dirY = 0,
                stepMultiplierPx = 10f,
                movingButtonId = target.id,
                otherButtons = listOf(target, other),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )

        // Snaps directly to other.posX
        assertEquals(0.502f, nextX, 0.0001f)
        assertEquals(0.5f, nextY, 0.0001f)
    }

    @Test
    fun calculateGamepadButtonMove_stepsAwayWhenAlreadyAligned() {
        val target = createButton("target", 0.502f, 0.5f)
        val other = createButton("other", 0.502f, 0.2f) // Already aligned at 0.502f

        val (nextX, nextY) =
            calculateGamepadButtonMove(
                currentNormX = target.posX,
                currentNormY = target.posY,
                dirX = 1,
                dirY = 0,
                stepMultiplierPx = 10f,
                movingButtonId = target.id,
                otherButtons = listOf(target, other),
                canvasW = canvasW,
                canvasH = canvasH,
                alignmentSnappingEnabled = true,
            )

        // Steps off 0.502f by 10px = 0.512f
        assertEquals(0.502f + 10f / canvasW, nextX, 0.0001f)
        assertEquals(0.5f, nextY, 0.0001f)
    }

    @Test
    fun findAlignedCenterGuides_discoversMatchingAxes() {
        val btn1 = createButton("btn1", 0.25f, 0.6f)
        val btn2 = createButton("btn2", 0.25f, 0.1f) // Matches X
        val btn3 = createButton("btn3", 0.75f, 0.6f) // Matches Y
        val btn4 = createButton("btn4", 0.90f, 0.9f) // No match

        val (alignedX, alignedY) =
            findAlignedCenterGuides(
                activeButtonId = btn1.id,
                buttons = listOf(btn1, btn2, btn3, btn4),
                canvasW = canvasW,
                canvasH = canvasH,
            )

        assertEquals(listOf(0.25f), alignedX)
        assertEquals(listOf(0.6f), alignedY)
    }

    @Test
    fun snapRectangular_snapsToGridLines() {
        val (snapX, snapY) = snapRectangular(0.505f, 0.505f, 1000f, 1000f, 30f)
        assertEquals(0.5f, snapX, 0.0001f)
        assertEquals(0.5f, snapY, 0.0001f)
    }

    @Test
    fun snapPosition_gridOffReturnsRaw() {
        val (snapX, snapY) = snapPosition(0.123f, 0.456f, 1000f, 1000f, GridMode.OFF, 30f)
        assertEquals(0.123f, snapX, 0.0001f)
        assertEquals(0.456f, snapY, 0.0001f)
    }
}
