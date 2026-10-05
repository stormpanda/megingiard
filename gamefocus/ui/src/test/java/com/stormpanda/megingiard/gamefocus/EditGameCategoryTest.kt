package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private const val TAG = "EditGameCategoryTest"

class EditGameCategoryTest {
    @Test
    fun testCategoryCountAndProperties() {
        AppLog.d(TAG, "testCategoryCountAndProperties: verifying category enum entries")
        val entries = EditGameCategory.entries
        assertEquals(2, entries.size)
        assertEquals(EditGameCategory.GAME_INFO, entries[0])
        assertEquals(EditGameCategory.SCRAPING, entries[1])

        entries.forEach { entry ->
            assertNotEquals(0, entry.titleResId)
        }
    }
}
