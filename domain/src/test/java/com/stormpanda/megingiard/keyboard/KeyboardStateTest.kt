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

    @Test
    fun testCapsLockStickyNotReleased() {
        // Set caps to sticky
        KeyboardState.onModifierTouchDown("caps")
        KeyboardState.onModifierTouchUp("caps", LinuxKeycodes.KEY_CAPSLOCK)
        assertEquals(ModifierState.STICKY, KeyboardState.stateFor("caps").value)

        // Make a layout that contains caps key
        val capsKey = KeyDef("caps", "Caps", LinuxKeycodes.KEY_CAPSLOCK, type = KeyType.MODIFIER)
        val layout = listOf(listOf(capsKey))

        // Releasing sticky modifiers should NOT release caps lock
        val released = KeyboardState.releaseStickyModifiers(layout)
        assertEquals(ModifierState.STICKY, KeyboardState.stateFor("caps").value)
        assertTrue(LinuxKeycodes.KEY_CAPSLOCK !in released)

        // Verify active keycode contains KEY_LEFTSHIFT but NOT KEY_CAPSLOCK
        val active = KeyboardState.activeModifierKeycodes(layout)
        assertTrue(LinuxKeycodes.KEY_LEFTSHIFT in active)
        assertTrue(LinuxKeycodes.KEY_CAPSLOCK !in active)

        // Second press of caps should turn it off
        KeyboardState.onModifierTouchDown("caps")
        val secondRelease = KeyboardState.onModifierTouchUp("caps", LinuxKeycodes.KEY_CAPSLOCK)
        assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("caps").value)
        assertEquals(listOf(LinuxKeycodes.KEY_CAPSLOCK), secondRelease)
    }

    @Test
    fun testFullLayoutTouchDownAndTouchUp() {
        // Touch down on full layout immediately transitions to HELD and returns code
        val downCode = KeyboardState.onModifierTouchDown("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertEquals(LinuxKeycodes.KEY_LEFTSHIFT, downCode)
        assertEquals(ModifierState.HELD, KeyboardState.stateFor("lshift").value)

        // Quick release (< 300ms) on full layout transitions to STICKY
        val released = KeyboardState.onModifierTouchUp("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertEquals(ModifierState.STICKY, KeyboardState.stateFor("lshift").value)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTSHIFT), released)
    }

    @Test
    fun testFullLayoutTouchDownAndLongRelease() {
        // Touch down on full layout immediately transitions to HELD and returns code
        val downCode = KeyboardState.onModifierTouchDown("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertEquals(LinuxKeycodes.KEY_LEFTSHIFT, downCode)

        // Wait to exceed MODIFIER_HOLD_THRESHOLD_MS (300ms)
        Thread.sleep(320L)

        // Long release (>= 300ms) on full layout transitions to INACTIVE
        val released = KeyboardState.onModifierTouchUp("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertEquals(ModifierState.INACTIVE, KeyboardState.stateFor("lshift").value)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTSHIFT), released)
    }

    @Test
    fun testLongPressOnLettersLayoutActivatesCapsLock() {
        // Touch down
        KeyboardState.onModifierTouchDown("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = false)

        // Long press trigger with isFullLayout = false (default)
        val downCode = KeyboardState.onModifierLongPress("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = false)
        assertEquals(LinuxKeycodes.KEY_LEFTSHIFT, downCode)
        assertEquals(ModifierState.HELD, KeyboardState.stateFor("lshift").value)

        // Verify caps lock is HELD
        assertEquals(ModifierState.HELD, KeyboardState.stateFor("caps").value)
    }
}
