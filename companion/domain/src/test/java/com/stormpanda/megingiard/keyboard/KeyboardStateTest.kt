package com.stormpanda.megingiard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyboardStateTest {
    private fun assertState(
        mod: String,
        expected: ModifierState,
    ) {
        assertEquals(expected, KeyboardState.stateFor(mod).value)
    }

    @Before
    fun setUp() {
        KeyboardState.reset()
    }

    @Test
    fun testDefaultState() {
        assertState("ctrl", ModifierState.INACTIVE)
        assertState("alt", ModifierState.INACTIVE)
        assertState("altgr", ModifierState.INACTIVE)
    }

    @Test
    fun testQuickTapToSticky() {
        KeyboardState.onModifierTouchDown("ctrl")
        val releasedKeycodes = KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)

        assertState("ctrl", ModifierState.STICKY)
        assertTrue(releasedKeycodes.isEmpty())
        assertTrue(LinuxKeycodes.KEY_LEFTCTRL in KeyboardState.activeModifierKeycodes(emptyList()))
    }

    @Test
    fun testSecondTapClearsSticky() {
        KeyboardState.onModifierTouchDown("ctrl")
        KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)
        assertState("ctrl", ModifierState.STICKY)

        KeyboardState.onModifierTouchDown("ctrl")
        val releasedKeycodes = KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)

        assertState("ctrl", ModifierState.INACTIVE)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTCTRL), releasedKeycodes)
    }

    @Test
    fun testLongPressToHeld() {
        KeyboardState.onModifierTouchDown("ctrl")
        val downCode = KeyboardState.onModifierLongPress("ctrl", LinuxKeycodes.KEY_LEFTCTRL)
        assertEquals(LinuxKeycodes.KEY_LEFTCTRL, downCode)
        assertState("ctrl", ModifierState.HELD)

        val releasedKeycodes = KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)
        assertState("ctrl", ModifierState.INACTIVE)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTCTRL), releasedKeycodes)
    }

    @Test
    fun testReleaseStickyModifiers() {
        KeyboardState.onModifierTouchDown("ctrl")
        KeyboardState.onModifierTouchUp("ctrl", LinuxKeycodes.KEY_LEFTCTRL)
        KeyboardState.onModifierTouchDown("alt")
        KeyboardState.onModifierTouchUp("alt", LinuxKeycodes.KEY_LEFTALT)

        assertState("ctrl", ModifierState.STICKY)
        assertState("alt", ModifierState.STICKY)

        val released = KeyboardState.releaseStickyModifiers(emptyList())

        assertState("ctrl", ModifierState.INACTIVE)
        assertState("alt", ModifierState.INACTIVE)
        assertTrue(LinuxKeycodes.KEY_LEFTCTRL in released)
        assertTrue(LinuxKeycodes.KEY_LEFTALT in released)
    }

    @Test
    fun testCapsLockStickyNotReleased() {
        KeyboardState.onModifierTouchDown("caps")
        KeyboardState.onModifierTouchUp("caps", LinuxKeycodes.KEY_CAPSLOCK)
        assertState("caps", ModifierState.STICKY)

        val layout = listOf(listOf(KeyDef("caps", "Caps", LinuxKeycodes.KEY_CAPSLOCK, type = KeyType.MODIFIER)))

        val released = KeyboardState.releaseStickyModifiers(layout)
        assertState("caps", ModifierState.STICKY)
        assertTrue(LinuxKeycodes.KEY_CAPSLOCK !in released)

        val active = KeyboardState.activeModifierKeycodes(layout)
        assertTrue(LinuxKeycodes.KEY_LEFTSHIFT in active)
        assertTrue(LinuxKeycodes.KEY_CAPSLOCK !in active)

        KeyboardState.onModifierTouchDown("caps")
        val secondRelease = KeyboardState.onModifierTouchUp("caps", LinuxKeycodes.KEY_CAPSLOCK)
        assertState("caps", ModifierState.INACTIVE)
        assertEquals(listOf(LinuxKeycodes.KEY_CAPSLOCK), secondRelease)
    }

    @Test
    fun testFullLayoutTouchDownAndTouchUp() {
        val downCode = KeyboardState.onModifierTouchDown("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertEquals(LinuxKeycodes.KEY_LEFTSHIFT, downCode)
        assertState("lshift", ModifierState.HELD)

        val released = KeyboardState.onModifierTouchUp("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertState("lshift", ModifierState.STICKY)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTSHIFT), released)
    }

    @Test
    fun testFullLayoutTouchDownAndLongRelease() {
        val downCode = KeyboardState.onModifierTouchDown("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertEquals(LinuxKeycodes.KEY_LEFTSHIFT, downCode)

        Thread.sleep(320L)

        val released = KeyboardState.onModifierTouchUp("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertState("lshift", ModifierState.INACTIVE)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTSHIFT), released)
    }

    @Test
    fun testLongPressOnLettersLayoutActivatesCapsLock() {
        KeyboardState.onModifierTouchDown("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = false)
        val downCode = KeyboardState.onModifierLongPress("lshift", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = false)
        assertEquals(LinuxKeycodes.KEY_LEFTSHIFT, downCode)
        assertState("lshift", ModifierState.HELD)
        assertState("caps", ModifierState.HELD)
    }

    @Test
    fun testFullLayoutSecondTouchDownReturnsKeycode() {
        val down1 = KeyboardState.onModifierTouchDown("caps", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertEquals(LinuxKeycodes.KEY_LEFTSHIFT, down1)
        val up1 = KeyboardState.onModifierTouchUp("caps", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertState("caps", ModifierState.STICKY)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTSHIFT), up1)

        val down2 = KeyboardState.onModifierTouchDown("caps", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertEquals(LinuxKeycodes.KEY_LEFTSHIFT, down2)

        val up2 = KeyboardState.onModifierTouchUp("caps", LinuxKeycodes.KEY_LEFTSHIFT, isFullLayout = true)
        assertState("caps", ModifierState.INACTIVE)
        assertEquals(listOf(LinuxKeycodes.KEY_LEFTSHIFT), up2)
    }
}
