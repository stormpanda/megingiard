package com.stormpanda.megingiard.gamefocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EditGameCategoryTest {
    @Test
    fun testCategoryCountAndProperties() {
        val entries = EditGameCategory.entries
        assertEquals(2, entries.size)
        assertEquals(EditGameCategory.GAME_INFO, entries[0])
        assertEquals(EditGameCategory.SCRAPING, entries[1])

        entries.forEach { entry ->
            assertNotEquals(0, entry.titleResId)
        }
    }
}
