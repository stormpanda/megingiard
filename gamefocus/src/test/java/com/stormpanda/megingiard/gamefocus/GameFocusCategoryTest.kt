package com.stormpanda.megingiard.gamefocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private const val TAG = "GameFocusCategoryTest"

class GameFocusCategoryTest {
    @Test
    fun testNext_wrapsAroundFromLastToFirst() {
        var category = GameFocusCategory.GAMES
        category = category.next()
        assertEquals(GameFocusCategory.APPS, category)

        category = category.next()
        assertEquals(GameFocusCategory.ALL_APPS, category)

        category = category.next()
        assertEquals(GameFocusCategory.FAVORITES, category)

        category = category.next()
        assertEquals(GameFocusCategory.LAST_USED, category)

        category = category.next()
        assertEquals(GameFocusCategory.GAMES, category)
    }

    @Test
    fun testPrevious_wrapsAroundFromFirstToLast() {
        var category = GameFocusCategory.GAMES
        category = category.previous()
        assertEquals(GameFocusCategory.LAST_USED, category)

        category = category.previous()
        assertEquals(GameFocusCategory.FAVORITES, category)

        category = category.previous()
        assertEquals(GameFocusCategory.ALL_APPS, category)

        category = category.previous()
        assertEquals(GameFocusCategory.APPS, category)

        category = category.previous()
        assertEquals(GameFocusCategory.GAMES, category)
    }

    @Test
    fun testAllEntriesHaveValidStringResId() {
        GameFocusCategory.entries.forEach { entry ->
            assertNotEquals("Res ID for ${entry.name} should be non-zero", 0, entry.stringResId)
        }
    }
}
