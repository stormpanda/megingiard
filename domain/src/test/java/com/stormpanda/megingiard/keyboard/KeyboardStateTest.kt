package com.stormpanda.megingiard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyboardStateTest {
    @Before
    fun setUp() {
        KeyboardState.reset()
    }

    @Test
    fun testDefaultState() {
        assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("ctrl").value)
        assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("alt").value)
        assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("altgr").value)
    }

    @Test
    fun testQuickTapToSticky() {
        // Touch down
        KeyboardState.onModifierTouchDown("ctrl")

        // Touch up after 100ms (< 300ms)
        val releasedKeycodes = KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)

        // Should be sticky, uinput key up should not be sent yet
        assertEquals(ModifierState.STICKY, KeyboardState.stateFor("ctrl").value)
        assertTrue(releasedKeycodes.isEmpty())

        // Verify key is active
        val active = KeyboardState.activeModifierKeycodes(emptyList())
        assertTrue(LinuxKeycodes.KEY_LEFTCTRL in active)
    }

    @Test
    fun testSecondTapClearsSticky() {
        // Make sticky
        KeyboardState.onModifierTouchDown("ctrl")
        KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)
        assertEquals(ModifierState.STICKY, KeyboardState.stateFor("ctrl").value)

        // Second touch down
        KeyboardState.onModifierTouchDown("ctrl")

        // Second touch up
        val releasedKeycodes = KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)

        // Should go inactive and return key up code
        assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("ctrl").value)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTCTRL), releasedKeycodes)
    }

    @Test
    fun testLongPressToHeld() {
        // Touch down
        KeyboardState.onModifierTouchDown("ctrl")

        // Long press trigger
        val downCode = KeyboardState.onModifierLongPress("ctrl", LinuxKeycodes.KEY_LEFTCTRL)
        assertEquals(LinuxKeycodes.KEY_LEFTCTRL, downCode)
        assertEquals(ModifierState.HELD, KeyboardState.stateFor("ctrl").value)

        // Touch up should release immediately
        val releasedKeycodes = KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)
        assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("ctrl").value)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTCTRL), releasedKeycodes)
    }

    @Test
    fun testReleaseStickyModifiers() {
        // Set ctrl and alt to sticky
        KeyboardState.onModifierTouchDown("ctrl")
        KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)

        KeyboardState.onModifierTouchDown("alt")
        KeyboardState.onModifierTouchUp("alt", LinuxKeycodes.KEY_LEFTALT)

        assertEquals(ModifierState.STICKY, KeyboardState.stateFor("ctrl").value)
        assertEquals(ModifierState.STICKY, KeyboardState.stateFor("alt").value)

        // Release sticky modifiers
        val released = KeyboardState.releaseStickyModifiers(emptyList())

        // Both should be inactive and keycodes returned
        assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("ctrl").value)
        assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("alt").value)

        assertTrue(LinuxKeycodes.KEY_LEFTCTRL in released)
        assertTrue(LinuxKeycodes.KEY_LEFTALT in released)
    }
}
