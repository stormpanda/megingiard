package com.stormpanda.megingiard.gamefocus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

private const val TAG = "GameFocusCategoryTest"

class GameFocusCategoryTest {
    private val categories = GameFocusCategory.builtIns

    @Test
    fun testNext_wrapsAroundFromLastToFirst() {
        var category: GameFocusCategory = GameFocusCategory.GAMES
        category = category.next(categories)
        assertEquals(GameFocusCategory.APPS, category)

        category = category.next(categories)
        assertEquals(GameFocusCategory.ALL_APPS, category)

        category = category.next(categories)
        assertEquals(GameFocusCategory.FAVORITES, category)

        category = category.next(categories)
        assertEquals(GameFocusCategory.LAST_USED, category)

        category = category.next(categories)
        assertEquals(GameFocusCategory.GAMES, category)
    }

    @Test
    fun testPrevious_wrapsAroundFromFirstToLast() {
        var category: GameFocusCategory = GameFocusCategory.GAMES
        category = category.previous(categories)
        assertEquals(GameFocusCategory.LAST_USED, category)

        category = category.previous(categories)
        assertEquals(GameFocusCategory.FAVORITES, category)

        category = category.previous(categories)
        assertEquals(GameFocusCategory.ALL_APPS, category)

        category = category.previous(categories)
        assertEquals(GameFocusCategory.APPS, category)

        category = category.previous(categories)
        assertEquals(GameFocusCategory.GAMES, category)
    }

    @Test
    fun testAllEntriesHaveValidStringResId() {
        categories.forEach { entry ->
            assertNotEquals("Res ID for ${entry.id} should be non-zero", 0, entry.stringResId)
        }
    }
}
