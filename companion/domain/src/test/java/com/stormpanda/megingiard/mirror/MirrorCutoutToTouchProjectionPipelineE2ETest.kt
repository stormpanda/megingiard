package com.stormpanda.megingiard.mirror

import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.privd.PrivdClient
import com.stormpanda.megingiard.privd.PrivdConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-End integration test suite verifying the Screen Mirroring Cutout
 * and Touch Projection pipeline:
 *
 * 1. Multi-cutout configuration and touch-projection filter flags in [ScreenCaptureManager].
 * 2. Cutout drag collision avoidance and aspect-ratio-aware resizing math in [MirrorCoordinateTransform].
 * 3. Multi-slot touch projection from secondary display surface to primary screen coordinates in [TouchProjectionController].
 * 4. Boundary clamping and edge-zone gesture rejection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MirrorCutoutToTouchProjectionPipelineE2ETest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var controller: TouchProjectionController

    private val topScreenCutout =
        ScreenCutout(
            id = "cutout-3ds-top",
            name = "3DS Top Screen",
            srcX = 0.0f,
            srcY = 0.0f,
            srcWidth = 1.0f,
            srcHeight = 0.5f,
            destX = 0.0f,
            destY = 0.0f,
            destWidth = 0.5f,
            destHeight = 1.0f,
            touchProjectionEnabled = false,
        )

    private val bottomScreenCutout =
        ScreenCutout(
            id = "cutout-3ds-bottom",
            name = "3DS Bottom Touch Screen",
            srcX = 0.0f,
            srcY = 0.5f,
            srcWidth = 1.0f,
            srcHeight = 0.5f,
            destX = 0.5f,
            destY = 0.0f,
            destWidth = 0.5f,
            destHeight = 1.0f,
            touchProjectionEnabled = true,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ScreenCaptureManager.scope = CoroutineScope(SupervisorJob() + testDispatcher)
        PrivdClient.setStateForTesting(PrivdConnectionState.CONNECTED)

        val layout =
            PadLayout(
                id = "layout-3ds",
                name = "3DS Layout",
                mirrorCutouts = listOf(topScreenCutout, bottomScreenCutout),
            )
        val profile =
            PadProfile(
                id = "profile-3ds",
                name = "3DS Profile",
                layouts = listOf(layout),
                activeLayoutId = layout.id,
            )
        MacroPadState.loadFrom(listOf(profile), profile.id)

        controller =
            TouchProjectionController(
                edgeZonePx = 48f,
                overlayAtBottom = true,
            )
    }

    @After
    fun tearDown() {
        controller.reset()
        MacroPadState.loadFrom(emptyList(), null)
        ScreenCaptureManager.resetMirrorSessionState()
        ScreenCaptureManager.scope.cancel()
        ScreenCaptureManager.scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        PrivdClient.setStateForTesting(PrivdConnectionState.DISCONNECTED)
        Dispatchers.resetMain()
    }

    @Test
    fun testCutoutTouchProjectionEndToEndPipeline() {
        val boxW = 1000f
        val boxH = 1000f

        // 1. Touch inside non-projectable Cutout (Top Screen: x in 0..500) -> ignored
        val handledTop =
            controller.onPress(
                pointerId = 101L,
                x = 250f,
                y = 500f,
                boxW = boxW,
                boxH = boxH,
                isConsumed = false,
                pointerCount = 1,
            )
        assertFalse("Expected touch on non-projectable cutout to be ignored", handledTop)
        assertNull("Indicator must remain null when touch is unhandled", controller.indicatorPos.value)

        // 2. Touch inside Bottom Touch Screen cutout (x in 500..1000, y in 0..1000)
        // Touch exactly in the center of the bottom screen destination: x = 750 (50% through dest), y = 500 (50% through dest)
        val handledBottom =
            controller.onPress(
                pointerId = 102L,
                x = 750f,
                y = 500f,
                boxW = boxW,
                boxH = boxH,
                isConsumed = false,
                pointerCount = 1,
            )
        assertTrue("Expected touch on projectable cutout to be accepted", handledBottom)
        assertEquals(Pair(750f, 500f), controller.indicatorPos.value)

        // 3. Move touch to (x = 875, y = 750) -> (75% through dest width, 75% through dest height)
        // Source mapping:
        // srcX = 0.0 + 0.75 * 1.0 = 0.75
        // srcY = 0.5 + 0.75 * 0.5 = 0.875
        val moveHandled =
            controller.onMove(
                pointerId = 102L,
                x = 875f,
                y = 750f,
                boxW = boxW,
                boxH = boxH,
                isConsumed = false,
            )
        assertTrue("Expected move inside cutout to be handled", moveHandled)
        assertEquals(Pair(875f, 750f), controller.indicatorPos.value)

        // 4. Move touch OUTSIDE cutout destination (x = 400 -> panned into top screen area)
        // Controller should send clamped UP and release the touch
        val moveOutsideHandled =
            controller.onMove(
                pointerId = 102L,
                x = 400f,
                y = 500f,
                boxW = boxW,
                boxH = boxH,
                isConsumed = false,
            )
        assertTrue("Expected move outside to trigger clamped release", moveOutsideHandled)
        assertNull("Indicator must be cleared when touch leaves cutout", controller.indicatorPos.value)
    }

    @Test
    fun testMultiFingerTouchProjectionSlotManagement() {
        val boxW = 1000f
        val boxH = 1000f

        // 1. Finger A down at (600, 300)
        val pressA =
            controller.onPress(
                pointerId = 1L,
                x = 600f,
                y = 300f,
                boxW = boxW,
                boxH = boxH,
                isConsumed = false,
                pointerCount = 1,
            )
        assertTrue(pressA)
        assertEquals(Pair(600f, 300f), controller.indicatorPos.value)

        // 2. Finger B down at (800, 700)
        val pressB =
            controller.onPress(
                pointerId = 2L,
                x = 800f,
                y = 700f,
                boxW = boxW,
                boxH = boxH,
                isConsumed = false,
                pointerCount = 2,
            )
        assertTrue(pressB)
        // Indicator stays locked to first finger
        assertEquals(Pair(600f, 300f), controller.indicatorPos.value)

        // 3. Move Finger B
        val moveB =
            controller.onMove(
                pointerId = 2L,
                x = 850f,
                y = 750f,
                boxW = boxW,
                boxH = boxH,
                isConsumed = false,
            )
        assertTrue(moveB)

        // 4. Release Finger A -> indicator switches to Finger B
        val releaseA =
            controller.onRelease(
                pointerId = 1L,
                x = 600f,
                y = 300f,
                boxW = boxW,
                boxH = boxH,
            )
        assertTrue(releaseA)

        // 5. Release Finger B -> indicator is cleared
        val releaseB =
            controller.onRelease(
                pointerId = 2L,
                x = 850f,
                y = 750f,
                boxW = boxW,
                boxH = boxH,
            )
        assertTrue(releaseB)
        assertNull(controller.indicatorPos.value)
    }

    @Test
    fun testCutoutCollisionAndAspectRatioMathE2E() {
        val cutouts = listOf(topScreenCutout, bottomScreenCutout)

        // 1. Drag Cutout 1 into Cutout 2 -> clampCutoutDrag prevents overlap
        // Target x = 0.4f, but Cutout 2 starts at 0.5f and width is 0.5f -> maximum x is 0.0f
        val (clampedX, clampedY) =
            clampCutoutDrag(
                cutoutId = topScreenCutout.id,
                originalX = 0.0f,
                originalY = 0.0f,
                targetX = 0.4f,
                targetY = 0.0f,
                width = 0.5f,
                height = 1.0f,
                allCutouts = cutouts,
            )
        assertEquals(0.0f, clampedX, 0.001f)
        assertEquals(0.0f, clampedY, 0.001f)

        // 2. Aspect Ratio size adjustment for 4:3 content inside 16:9 viewport
        val (targetW, targetH) =
            adjustDestSizeToAspectRatio(
                destX = 0.0f,
                destY = 0.0f,
                destWidth = 1.0f,
                destHeight = 1.0f,
                cropRatio = 4f / 3f,
                screenW = 1920f,
                screenH = 1080f,
            )
        assertTrue("Target width must be positive", targetW > 0f)
        assertTrue("Target height must be positive", targetH > 0f)
        assertTrue("Adjusted dimensions must fit inside unit rectangle", targetW <= 1.0f && targetH <= 1.0f)

        // 3. Coordinate projection helper
        val projected =
            projectCutoutCoordinates(
                touchX = 750f,
                touchY = 500f,
                destLeft = 500f,
                destTop = 0f,
                destWidth = 500f,
                destHeight = 1000f,
                srcX = 0.0f,
                srcY = 0.5f,
                srcWidth = 1.0f,
                srcHeight = 0.5f,
                clampToEdge = false,
            )
        assertNotNull(projected)
        assertEquals(0.5f, projected!!.first, 0.001f)
        assertEquals(0.75f, projected.second, 0.001f)
    }
}
