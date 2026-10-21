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
    fun keyBounds_distanceSquaredTo() {
        val bounds = KeyBounds(left = 10f, top = 20f, right = 50f, bottom = 60f)

        // Inside and on edges -> 0f
        assertEquals(0f, bounds.distanceSquaredTo(10f, 20f), 0.001f)
        assertEquals(0f, bounds.distanceSquaredTo(30f, 40f), 0.001f)
        assertEquals(0f, bounds.distanceSquaredTo(50f, 60f), 0.001f)

        // Left / right outside
        assertEquals(9f, bounds.distanceSquaredTo(7f, 40f), 0.001f)
        assertEquals(16f, bounds.distanceSquaredTo(54f, 40f), 0.001f)

        // Top / bottom outside
        assertEquals(25f, bounds.distanceSquaredTo(30f, 15f), 0.001f)
        assertEquals(36f, bounds.distanceSquaredTo(30f, 66f), 0.001f)

        // Diagonal outside (dx = 3, dy = 4 -> 25)
        assertEquals(25f, bounds.distanceSquaredTo(7f, 16f), 0.001f)
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
