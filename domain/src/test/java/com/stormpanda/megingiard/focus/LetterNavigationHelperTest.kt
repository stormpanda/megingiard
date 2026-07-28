package com.stormpanda.megingiard.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterNavigationHelperTest {
    private fun makeApp(label: String): InstalledAppInfo =
        InstalledAppInfo(
            packageName = "com.test.${label.lowercase().replace(" ", "")}",
            activityName = "MainActivity",
            label = label,
            icon = null,
            coverPath = null,
        )

    @Test
    fun testGetStartingLetter() {
        assertEquals('A', LetterNavigationHelper.getStartingLetter("Apex Legends"))
        assertEquals('A', LetterNavigationHelper.getStartingLetter("  asphalt 9"))
        assertEquals('1', LetterNavigationHelper.getStartingLetter("[1942] Game"))
        assertEquals('#', LetterNavigationHelper.getStartingLetter(""))
        assertEquals('Z', LetterNavigationHelper.getStartingLetter("Zelda"))
    }

    @Test
    fun testGetUniqueStartingLetters() {
        val apps =
            listOf(
                makeApp("Apex"),
                makeApp("Asphalt"),
                makeApp("Brawl"),
                makeApp("Call of Duty"),
                makeApp("Castlevania"),
                makeApp("Doom"),
            )
        val unique = LetterNavigationHelper.getUniqueStartingLetters(apps)
        assertEquals(listOf('A', 'B', 'C', 'D'), unique)
    }

    @Test
    fun testFindFirstIndexOfLetter() {
        val apps =
            listOf(
                makeApp("Apex"), // 0: A
                makeApp("Asphalt"), // 1: A
                makeApp("Brawl"), // 2: B
                makeApp("Call of Duty"), // 3: C
                makeApp("Castlevania"), // 4: C
                makeApp("Doom"), // 5: D
            )
        assertEquals(0, LetterNavigationHelper.findFirstIndexOfLetter(apps, 'A'))
        assertEquals(2, LetterNavigationHelper.findFirstIndexOfLetter(apps, 'B'))
        assertEquals(3, LetterNavigationHelper.findFirstIndexOfLetter(apps, 'C'))
        assertEquals(5, LetterNavigationHelper.findFirstIndexOfLetter(apps, 'D'))
        assertEquals(2, LetterNavigationHelper.findFirstIndexOfLetter(apps, 'b'))
        assertEquals(3, LetterNavigationHelper.findFirstIndexOfLetter(apps, 'c'))
    }

    @Test
    fun testFindNextLetterAppIndex() {
        val apps =
            listOf(
                makeApp("Apex"), // 0: A
                makeApp("Asphalt"), // 1: A
                makeApp("Brawl"), // 2: B
                makeApp("Call of Duty"), // 3: C
                makeApp("Castlevania"), // 4: C
                makeApp("Doom"), // 5: D
            )

        // From 0 (Apex, A) -> 2 (Brawl, B)
        assertEquals(2, LetterNavigationHelper.findNextLetterAppIndex(apps, 0))

        // From 1 (Asphalt, A) -> 2 (Brawl, B)
        assertEquals(2, LetterNavigationHelper.findNextLetterAppIndex(apps, 1))

        // From 2 (Brawl, B) -> 3 (Call of Duty, C)
        assertEquals(3, LetterNavigationHelper.findNextLetterAppIndex(apps, 2))

        // From 4 (Castlevania, C) -> 5 (Doom, D)
        assertEquals(5, LetterNavigationHelper.findNextLetterAppIndex(apps, 4))

        // From 5 (Doom, D) -> wrap around to 0 (Apex, A)
        assertEquals(0, LetterNavigationHelper.findNextLetterAppIndex(apps, 5))
    }

    @Test
    fun testFindPreviousLetterAppIndex() {
        val apps =
            listOf(
                makeApp("Apex"), // 0: A
                makeApp("Asphalt"), // 1: A
                makeApp("Brawl"), // 2: B
                makeApp("Call of Duty"), // 3: C
                makeApp("Castlevania"), // 4: C
                makeApp("Doom"), // 5: D
            )

        // From 4 (Castlevania, C) -> 3 (Call of Duty, start of C)
        assertEquals(3, LetterNavigationHelper.findPreviousLetterAppIndex(apps, 4))

        // From 3 (Call of Duty, start of C) -> 2 (Brawl, B)
        assertEquals(2, LetterNavigationHelper.findPreviousLetterAppIndex(apps, 3))

        // From 2 (Brawl, B) -> 0 (Apex, A)
        assertEquals(0, LetterNavigationHelper.findPreviousLetterAppIndex(apps, 2))

        // From 1 (Asphalt, A) -> 0 (Apex, start of A)
        assertEquals(0, LetterNavigationHelper.findPreviousLetterAppIndex(apps, 1))

        // From 0 (Apex, start of A) -> wrap around to 5 (Doom, D)
        assertEquals(5, LetterNavigationHelper.findPreviousLetterAppIndex(apps, 0))
    }

    @Test
    fun testEdgeCases() {
        assertEquals(0, LetterNavigationHelper.findNextLetterAppIndex(emptyList(), 0))
        assertEquals(0, LetterNavigationHelper.findPreviousLetterAppIndex(emptyList(), 0))
        assertEquals(emptyList<Char>(), LetterNavigationHelper.getUniqueStartingLetters(emptyList()))

        val single = listOf(makeApp("Only Game"))
        assertEquals(0, LetterNavigationHelper.findNextLetterAppIndex(single, 0))
        assertEquals(0, LetterNavigationHelper.findPreviousLetterAppIndex(single, 0))

        val sameLetter = listOf(makeApp("Alpha"), makeApp("Apex"), makeApp("Asphalt"))
        assertEquals(0, LetterNavigationHelper.findNextLetterAppIndex(sameLetter, 0))
        assertEquals(0, LetterNavigationHelper.findPreviousLetterAppIndex(sameLetter, 2))
    }
}
