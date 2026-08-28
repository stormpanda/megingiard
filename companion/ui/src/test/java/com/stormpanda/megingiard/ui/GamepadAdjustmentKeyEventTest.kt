package com.stormpanda.megingiard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GamepadAdjustmentKeyEventTest {
    private fun createKeyEvent(
        action: Int,
        keyCode: Int,
    ): ComposeKeyEvent {
        val nativeEvent = AndroidKeyEvent(action, keyCode)
        return ComposeKeyEvent(nativeEvent)
    }

    @Test
    fun `when not adjusting all keys return false`() {
        var leftCount = 0
        var rightCount = 0
        var dismissCount = 0

        val downA = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BUTTON_A)
        val handled =
            handleAdjustmentKeyEvent(
                keyEvent = downA,
                isAdjusting = false,
                onAdjustLeft = { leftCount++ },
                onAdjustRight = { rightCount++ },
                onDismissAdjustment = { dismissCount++ },
            )

        assertFalse(handled)
        assertEquals(0, leftCount)
        assertEquals(0, rightCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun `when adjusting dpad left and right invoke adjust callbacks and consume event`() {
        var leftCount = 0
        var rightCount = 0
        var dismissCount = 0

        val downLeft = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_LEFT)
        val handledLeft =
            handleAdjustmentKeyEvent(
                keyEvent = downLeft,
                isAdjusting = true,
                onAdjustLeft = { leftCount++ },
                onAdjustRight = { rightCount++ },
                onDismissAdjustment = { dismissCount++ },
            )

        assertTrue(handledLeft)
        assertEquals(1, leftCount)
        assertEquals(0, rightCount)
        assertEquals(0, dismissCount)

        val downRight = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_RIGHT)
        val handledRight =
            handleAdjustmentKeyEvent(
                keyEvent = downRight,
                isAdjusting = true,
                onAdjustLeft = { leftCount++ },
                onAdjustRight = { rightCount++ },
                onDismissAdjustment = { dismissCount++ },
            )

        assertTrue(handledRight)
        assertEquals(1, leftCount)
        assertEquals(1, rightCount)
        assertEquals(0, dismissCount)
    }

    @Test
    fun `when adjusting Button A dismisses adjustment mode and consumes event`() {
        var dismissCount = 0

        val downA = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BUTTON_A)
        val handledDownA =
            handleAdjustmentKeyEvent(
                keyEvent = downA,
                isAdjusting = true,
                onAdjustLeft = {},
                onAdjustRight = {},
                onDismissAdjustment = { dismissCount++ },
            )

        assertTrue(handledDownA)
        assertEquals(1, dismissCount)

        val upA = createKeyEvent(AndroidKeyEvent.ACTION_UP, AndroidKeyEvent.KEYCODE_BUTTON_A)
        val handledUpA =
            handleAdjustmentKeyEvent(
                keyEvent = upA,
                isAdjusting = true,
                onAdjustLeft = {},
                onAdjustRight = {},
                onDismissAdjustment = {},
            )

        assertTrue(handledUpA)
    }

    @Test
    fun `when adjusting Dpad Center and Enter dismiss adjustment mode`() {
        var dismissCount = 0

        val downCenter = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_CENTER)
        val handledCenter =
            handleAdjustmentKeyEvent(
                keyEvent = downCenter,
                isAdjusting = true,
                onAdjustLeft = {},
                onAdjustRight = {},
                onDismissAdjustment = { dismissCount++ },
            )

        assertTrue(handledCenter)
        assertEquals(1, dismissCount)

        val downEnter = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_ENTER)
        val handledEnter =
            handleAdjustmentKeyEvent(
                keyEvent = downEnter,
                isAdjusting = true,
                onAdjustLeft = {},
                onAdjustRight = {},
                onDismissAdjustment = { dismissCount++ },
            )

        assertTrue(handledEnter)
        assertEquals(2, dismissCount)
    }

    @Test
    fun `when adjusting Button B and Back dismiss adjustment mode`() {
        var dismissCount = 0

        val downB = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BUTTON_B)
        val handledB =
            handleAdjustmentKeyEvent(
                keyEvent = downB,
                isAdjusting = true,
                onAdjustLeft = {},
                onAdjustRight = {},
                onDismissAdjustment = { dismissCount++ },
            )

        assertTrue(handledB)
        assertEquals(1, dismissCount)

        val downBack = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_BACK)
        val handledBack =
            handleAdjustmentKeyEvent(
                keyEvent = downBack,
                isAdjusting = true,
                onAdjustLeft = {},
                onAdjustRight = {},
                onDismissAdjustment = { dismissCount++ },
            )

        assertTrue(handledBack)
        assertEquals(2, dismissCount)
    }

    @Test
    fun `when adjusting Dpad Up and Down dismiss adjustment mode and return false for focus traversal`() {
        var dismissCount = 0

        val downUp = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_UP)
        val handledUp =
            handleAdjustmentKeyEvent(
                keyEvent = downUp,
                isAdjusting = true,
                onAdjustLeft = {},
                onAdjustRight = {},
                onDismissAdjustment = { dismissCount++ },
            )

        assertFalse(handledUp)
        assertEquals(1, dismissCount)

        val downDown = createKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_DOWN)
        val handledDown =
            handleAdjustmentKeyEvent(
                keyEvent = downDown,
                isAdjusting = true,
                onAdjustLeft = {},
                onAdjustRight = {},
                onDismissAdjustment = { dismissCount++ },
            )

        assertFalse(handledDown)
        assertEquals(2, dismissCount)
    }
}
