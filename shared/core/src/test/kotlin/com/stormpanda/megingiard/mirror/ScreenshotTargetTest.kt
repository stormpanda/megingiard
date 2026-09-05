package com.stormpanda.megingiard.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotTargetTest {
    @Test
    fun screenshotTarget_hasExpectedEntries() {
        val entries = ScreenshotTarget.entries
        assertEquals(3, entries.size)
        assertTrue(entries.contains(ScreenshotTarget.TOP))
        assertTrue(entries.contains(ScreenshotTarget.BOTTOM))
        assertTrue(entries.contains(ScreenshotTarget.BOTH))
    }

    @Test
    fun screenshotTarget_valueOfResolvesCorrectly() {
        assertEquals(ScreenshotTarget.TOP, ScreenshotTarget.valueOf("TOP"))
        assertEquals(ScreenshotTarget.BOTTOM, ScreenshotTarget.valueOf("BOTTOM"))
        assertEquals(ScreenshotTarget.BOTH, ScreenshotTarget.valueOf("BOTH"))
    }
}
