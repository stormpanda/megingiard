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
    private fun handleKey(
        keyCode: Int,
        action: Int = AndroidKeyEvent.ACTION_DOWN,
        isAdjusting: Boolean = true,
        onAdjustLeft: () -> Unit = {},
        onAdjustRight: () -> Unit = {},
        onDismissAdjustment: () -> Unit = {},
    ): Boolean =
        handleAdjustmentKeyEvent(
            keyEvent = ComposeKeyEvent(AndroidKeyEvent(action, keyCode)),
            isAdjusting = isAdjusting,
            onAdjustLeft = onAdjustLeft,
            onAdjustRight = onAdjustRight,
            onDismissAdjustment = onDismissAdjustment,
        )

    @Test
    fun `when not adjusting all keys return false`() {
        var leftCount = 0
        var rightCount = 0
        var dismissCount = 0

        val handled =
            handleKey(
                keyCode = AndroidKeyEvent.KEYCODE_BUTTON_A,
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

        val handledLeft =
            handleKey(
                keyCode = AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                onAdjustLeft = { leftCount++ },
                onAdjustRight = { rightCount++ },
                onDismissAdjustment = { dismissCount++ },
            )
        assertTrue(handledLeft)
        assertEquals(1, leftCount)
        assertEquals(0, rightCount)
        assertEquals(0, dismissCount)

        val handledRight =
            handleKey(
                keyCode = AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
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
        assertTrue(handleKey(AndroidKeyEvent.KEYCODE_BUTTON_A, onDismissAdjustment = { dismissCount++ }))
        assertEquals(1, dismissCount)

        assertTrue(handleKey(AndroidKeyEvent.KEYCODE_BUTTON_A, action = AndroidKeyEvent.ACTION_UP))
    }

    @Test
    fun `when adjusting Dpad Center and Enter dismiss adjustment mode`() {
        var dismissCount = 0
        assertTrue(handleKey(AndroidKeyEvent.KEYCODE_DPAD_CENTER, onDismissAdjustment = { dismissCount++ }))
        assertEquals(1, dismissCount)

        assertTrue(handleKey(AndroidKeyEvent.KEYCODE_ENTER, onDismissAdjustment = { dismissCount++ }))
        assertEquals(2, dismissCount)
    }

    @Test
    fun `when adjusting Button B and Back dismiss adjustment mode`() {
        var dismissCount = 0
        assertTrue(handleKey(AndroidKeyEvent.KEYCODE_BUTTON_B, onDismissAdjustment = { dismissCount++ }))
        assertEquals(1, dismissCount)

        assertTrue(handleKey(AndroidKeyEvent.KEYCODE_BACK, onDismissAdjustment = { dismissCount++ }))
        assertEquals(2, dismissCount)
    }

    @Test
    fun `when adjusting Dpad Up and Down dismiss adjustment mode and return false for focus traversal`() {
        var dismissCount = 0
        assertFalse(handleKey(AndroidKeyEvent.KEYCODE_DPAD_UP, onDismissAdjustment = { dismissCount++ }))
        assertEquals(1, dismissCount)

        assertFalse(handleKey(AndroidKeyEvent.KEYCODE_DPAD_DOWN, onDismissAdjustment = { dismissCount++ }))
        assertEquals(2, dismissCount)
    }
}
