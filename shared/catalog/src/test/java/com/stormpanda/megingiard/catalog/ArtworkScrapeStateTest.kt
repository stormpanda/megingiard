package com.stormpanda.megingiard.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PKG = "com.test.game"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArtworkScrapeStateTest {
    @Test
    fun testArtworkOptOuts_toggleCoverAndLibraryIconIndependently() {
        val optedOut = ArtworkOptOuts().withOptOut(ArtworkKind.COVER, PKG, true)
        assertTrue(optedOut.isOptedOut(ArtworkKind.COVER, PKG))
        assertFalse(optedOut.isOptedOut(ArtworkKind.LIBRARY_ICON, PKG))

        val both = optedOut.withOptOut(ArtworkKind.LIBRARY_ICON, PKG, true)
        assertTrue(both.isOptedOut(ArtworkKind.LIBRARY_ICON, PKG))

        val cleared = both.withOptOut(ArtworkKind.COVER, PKG, false)
        assertFalse(cleared.isOptedOut(ArtworkKind.COVER, PKG))
        assertTrue(cleared.isOptedOut(ArtworkKind.LIBRARY_ICON, PKG))
    }

    @Test
    fun testArtworkOptOuts_logoCannotBeOptedOut() {
        val original = ArtworkOptOuts()
        val result = original.withOptOut(ArtworkKind.LOGO, PKG, true)
        assertSame(original, result)
        assertFalse(result.isOptedOut(ArtworkKind.LOGO, PKG))
    }

    @Test
    fun testScrapeAttempts_withAttemptIsPerKindAndIdempotent() {
        val attempts = ScrapeAttempts().withAttempt(ArtworkKind.LOGO, PKG)
        assertTrue(attempts.wasAttempted(ArtworkKind.LOGO, PKG))
        assertFalse(attempts.wasAttempted(ArtworkKind.COVER, PKG))
        assertFalse(attempts.wasAttempted(ArtworkKind.LIBRARY_ICON, PKG))

        assertSame(attempts, attempts.withAttempt(ArtworkKind.LOGO, PKG))
    }
}
