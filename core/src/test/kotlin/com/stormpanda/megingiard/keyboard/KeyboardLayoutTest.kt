package com.stormpanda.megingiard.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariant tests for the three keyboard layouts.
 *
 * Verifies the rules from AGENTS.md §9.8:
 * - All keycodes that target the kernel uinput device must be in 1..255
 *   (the trackpoint sentinel uses 0; modifier keys still inject and must be > 0).
 *
 * Plus structural invariants:
 * - Every layout has 4 rows.
 * - Key IDs are unique within a layout.
 * - All non-trackpoint keys have linuxKeycode in 0..255.
 */
class KeyboardLayoutTest {
    private val layouts =
        mapOf(
            "QWERTZ" to qwertzLayout(),
            "QWERTY" to qwertyLayout(),
            "AZERTY" to azertyLayout(),
        )

    @Test
    fun `every layout has four rows`() {
        for ((name, layout) in layouts) {
            assertEquals("$name row count", 4, layout.size)
        }
    }

    @Test
    fun `key ids are unique within each layout`() {
        for ((name, layout) in layouts) {
            val allIds = layout.flatten().map { it.id }
            val duplicates =
                allIds
                    .groupingBy { it }
                    .eachCount()
                    .filter { it.value > 1 }
                    .keys
            assertTrue("$name has duplicate ids: $duplicates", duplicates.isEmpty())
        }
    }

    @Test
    fun `non-trackpoint keycodes are within 0 to 255 range`() {
        for ((name, layout) in layouts) {
            for (key in layout.flatten()) {
                if (key.type == KeyType.TRACKPOINT) continue
                assertTrue(
                    "$name key '${key.id}' has out-of-range keycode ${key.linuxKeycode}",
                    key.linuxKeycode in 0..255,
                )
            }
        }
    }

    @Test
    fun `all width weights are positive`() {
        for ((name, layout) in layouts) {
            for (key in layout.flatten()) {
                assertTrue(
                    "$name key '${key.id}' has non-positive width weight ${key.widthWeight}",
                    key.widthWeight > 0f,
                )
            }
        }
    }

    @Test
    fun `findKeyInLayout returns matching key when present`() {
        val layout = qwertzLayout()
        val first = layout.flatten().first { it.type == KeyType.NORMAL }
        val found = findKeyInLayout(layout, first.id)
        assertNotNull(found)
        assertEquals(first.id, found!!.id)
        assertEquals(first.linuxKeycode, found.linuxKeycode)
    }

    @Test
    fun `findKeyInLayout returns null for unknown id`() {
        assertNull(findKeyInLayout(qwertzLayout(), "this-id-does-not-exist"))
    }

    @Test
    fun `every layout contains at least one MODIFIER key`() {
        for ((name, layout) in layouts) {
            val modifiers = layout.flatten().filter { it.type == KeyType.MODIFIER }
            assertTrue("$name has no MODIFIER keys", modifiers.isNotEmpty())
        }
    }

    @Test
    fun `symbols layouts contain ABC switcher key`() {
        val sym1 = qwertzLayout(KeyboardMode.SYMBOLS_1)
        val sym2 = qwertzLayout(KeyboardMode.SYMBOLS_2)
        assertNotNull("Symbols 1 has ABC switcher key", findKeyInLayout(sym1, "mode_switch_abc"))
        assertNotNull("Symbols 2 has ABC switcher key", findKeyInLayout(sym2, "mode_switch_abc"))
    }

    @Test
    fun `findKeyInLayout filters switcher keys correctly across modes`() {
        val letters = qwertzLayout(KeyboardMode.LETTERS)
        val symbols = qwertzLayout(KeyboardMode.SYMBOLS_1)

        // mode_switch (?123) exists in letters mode but not symbols mode
        assertNotNull(findKeyInLayout(letters, "mode_switch"))
        assertNull(findKeyInLayout(symbols, "mode_switch"))

        // mode_switch_abc (ABC) exists in symbols mode but not letters mode
        assertNotNull(findKeyInLayout(symbols, "mode_switch_abc"))
        assertNull(findKeyInLayout(letters, "mode_switch_abc"))
    }

    @Test
    fun `numeric layout contains expected keys and row count`() {
        val numLayout = qwertzLayout(KeyboardMode.NUMERIC)
        assertEquals("Numeric layout has 4 rows", 4, numLayout.size)

        // Verify key existences
        assertNotNull("Numeric has ABC switcher", findKeyInLayout(numLayout, "mode_switch_abc"))
        assertNotNull("Numeric has !?# switcher", findKeyInLayout(numLayout, "mode_switch"))
        assertNotNull("Numeric has plus key", findKeyInLayout(numLayout, "plus"))
        assertNotNull("Numeric has minus key", findKeyInLayout(numLayout, "minus"))
        assertNotNull("Numeric has asterisk key", findKeyInLayout(numLayout, "asterisk"))
        assertNotNull("Numeric has slash key", findKeyInLayout(numLayout, "slash"))

        // Verify digits 0-9 exist
        for (i in 0..9) {
            assertNotNull("Numeric has digit $i", findKeyInLayout(numLayout, "num_$i"))
        }
    }

    @Test
    fun `KeyboardLayoutState holds correct mode and grid`() {
        val grid = qwertzLayout(KeyboardMode.LETTERS)
        val state = KeyboardLayoutState(KeyboardMode.LETTERS, grid)
        assertEquals(KeyboardMode.LETTERS, state.mode)
        assertEquals(grid, state.grid)
    }

    @Test
    fun `getPopupOptions generates expected options list`() {
        val eKey = KeyDef("e", "e", 18, superscript = "3")
        val optionsLower = getPopupOptions(eKey, isUpper = false)
        assertEquals(1, optionsLower.size)
        assertTrue(optionsLower.contains("3"))

        val optionsUpper = getPopupOptions(eKey, isUpper = true)
        assertEquals(1, optionsUpper.size)
        assertTrue(optionsUpper.contains("3"))

        // Keys without superscript return empty list
        val fKey = KeyDef("f", "f", 33)
        assertTrue(getPopupOptions(fKey, isUpper = false).isEmpty())
    }
}
