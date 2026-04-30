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
 * - Every layout has 6 rows (F-row, number-row, top, home, bottom, bottom-bar).
 * - Key IDs are unique within a layout.
 * - The trackpoint key exists, has KeyType.TRACKPOINT and linuxKeycode == 0.
 * - All non-trackpoint keys have linuxKeycode in 1..255.
 */
class KeyboardLayoutTest {

    private val layouts = mapOf(
        "QWERTZ" to qwertzLayout(),
        "QWERTY" to qwertyLayout(),
        "AZERTY" to azertyLayout(),
    )

    @Test
    fun `every layout has six rows`() {
        for ((name, layout) in layouts) {
            assertEquals("$name row count", 6, layout.size)
        }
    }

    @Test
    fun `key ids are unique within each layout`() {
        for ((name, layout) in layouts) {
            val allIds = layout.flatten().map { it.id }
            val duplicates = allIds.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            assertTrue("$name has duplicate ids: $duplicates", duplicates.isEmpty())
        }
    }

    @Test
    fun `non-trackpoint keycodes are within 1 to 255 range`() {
        for ((name, layout) in layouts) {
            for (key in layout.flatten()) {
                if (key.type == KeyType.TRACKPOINT) continue
                assertTrue(
                    "$name key '${key.id}' has out-of-range keycode ${key.linuxKeycode}",
                    key.linuxKeycode in 1..255,
                )
            }
        }
    }

    @Test
    fun `trackpoint key exists in every layout with sentinel keycode`() {
        for ((name, layout) in layouts) {
            val trackpoint = layout.flatten().firstOrNull { it.type == KeyType.TRACKPOINT }
            assertNotNull("$name has no trackpoint key", trackpoint)
            assertEquals("$name trackpoint keycode must be 0", 0, trackpoint!!.linuxKeycode)
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
}
