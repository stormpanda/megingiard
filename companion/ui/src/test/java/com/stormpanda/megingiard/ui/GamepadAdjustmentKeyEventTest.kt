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
    fun `handle2DAdjustmentKeyEvent tests when not adjusting`() {
        var startCount = 0
        val handled =
            handle2DAdjustmentKeyEvent(
                keyEvent = ComposeKeyEvent(AndroidKeyEvent(AndroidKeyEvent.ACTION_DOWN, AndroidKeyEvent.KEYCODE_DPAD_UP)),
                isAdjusting = false,
                onStartAdjusting = { _, _, _ -> startCount++ },
                onStopAdjusting = {},
                onDismissAdjustment = {},
            )
        assertFalse(handled)
        assertEquals(0, startCount)
    }

    @Test
    fun `handle2DAdjustmentKeyEvent directional and dismiss key handling`() {
        var startKeyCode = 0
        var dirX = 0
        var dirY = 0
        var stopKeyCode = 0
        var dismissCount = 0

        fun test2D(
            keyCode: Int,
            action: Int = AndroidKeyEvent.ACTION_DOWN,
            onModifierKeyDown: ((Int) -> Boolean)? = null,
            onModifierKeyUp: ((Int) -> Boolean)? = null,
        ): Boolean =
            handle2DAdjustmentKeyEvent(
                keyEvent = ComposeKeyEvent(AndroidKeyEvent(action, keyCode)),
                isAdjusting = true,
                onStartAdjusting = { code, dx, dy ->
                    startKeyCode = code
                    dirX = dx
                    dirY = dy
                },
                onStopAdjusting = { code -> stopKeyCode = code },
                onDismissAdjustment = { dismissCount++ },
                onModifierKeyDown = onModifierKeyDown,
                onModifierKeyUp = onModifierKeyUp,
            )

        // D-pad UP
        assertTrue(test2D(AndroidKeyEvent.KEYCODE_DPAD_UP))
        assertEquals(AndroidKeyEvent.KEYCODE_DPAD_UP, startKeyCode)
        assertEquals(0, dirX)
        assertEquals(-1, dirY)

        // D-pad DOWN
        assertTrue(test2D(AndroidKeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(0, dirX)
        assertEquals(1, dirY)

        // D-pad LEFT
        assertTrue(test2D(AndroidKeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(-1, dirX)
        assertEquals(0, dirY)

        // D-pad RIGHT
        assertTrue(test2D(AndroidKeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(1, dirX)
        assertEquals(0, dirY)

        // KeyUp on D-pad RIGHT
        assertTrue(test2D(AndroidKeyEvent.KEYCODE_DPAD_RIGHT, action = AndroidKeyEvent.ACTION_UP))
        assertEquals(AndroidKeyEvent.KEYCODE_DPAD_RIGHT, stopKeyCode)

        // Dismiss via BUTTON_B
        assertTrue(test2D(AndroidKeyEvent.KEYCODE_BUTTON_B))
        assertEquals(1, dismissCount)

        // Modifiers
        var l2Down = false
        var l2Up = false
        assertTrue(
            test2D(
                AndroidKeyEvent.KEYCODE_BUTTON_L2,
                onModifierKeyDown = {
                    l2Down = true
                    true
                },
            ),
        )
        assertTrue(l2Down)

        assertTrue(
            test2D(
                AndroidKeyEvent.KEYCODE_BUTTON_L2,
                action = AndroidKeyEvent.ACTION_UP,
                onModifierKeyUp = {
                    l2Up = true
                    true
                },
            ),
        )
        assertTrue(l2Up)
    }
}
