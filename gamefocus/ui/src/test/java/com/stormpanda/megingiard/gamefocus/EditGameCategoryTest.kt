package com.stormpanda.megingiard.gamefocus

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
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
        assertEquals(4, entries.size)
        assertEquals(EditGameCategory.GAME_INFO, entries[0])
        assertEquals(EditGameCategory.SCRAPING, entries[1])
        assertEquals(EditGameCategory.LIBRARY_ICON, entries[2])
        assertEquals(EditGameCategory.COLLECTIONS, entries[3])

        assertEquals(R.string.gamefocus_cat_collections, EditGameCategory.COLLECTIONS.titleResId)
        assertEquals(Icons.Rounded.CollectionsBookmark, EditGameCategory.COLLECTIONS.icon)

        entries.forEach { entry ->
            assertNotEquals(0, entry.titleResId)
        }
    }
}
