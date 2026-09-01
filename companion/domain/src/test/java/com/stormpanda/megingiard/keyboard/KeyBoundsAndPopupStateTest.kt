package com.stormpanda.megingiard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyBoundsAndPopupStateTest {
    @Test
    fun keyBounds_containsCoordinates() {
        val bounds = KeyBounds(left = 10f, top = 20f, right = 50f, bottom = 60f)

        assertTrue(bounds.contains(10f, 20f))
        assertTrue(bounds.contains(30f, 40f))
        assertTrue(bounds.contains(50f, 60f))

        assertFalse(bounds.contains(9f, 20f))
        assertFalse(bounds.contains(51f, 40f))
        assertFalse(bounds.contains(30f, 19f))
        assertFalse(bounds.contains(30f, 61f))
    }

    @Test
    fun popupState_dataModel() {
        val bounds = KeyBounds(left = 0f, top = 0f, right = 100f, bottom = 50f)
        val keyDef = KeyDef(id = "a", label = "a", linuxKeycode = 30)
        val popup =
            PopupState(
                keyDef = keyDef,
                options = listOf("a", "ä", "á", "à"),
                selectedIndex = 1,
                keyBounds = bounds,
                isLongPress = true,
                pointerId = 123L,
            )

        assertEquals("a", popup.keyDef.label)
        assertEquals(4, popup.options.size)
        assertEquals(1, popup.selectedIndex)
        assertTrue(popup.isLongPress)
        assertEquals(123L, popup.pointerId)
    }
}
