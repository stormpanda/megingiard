package com.stormpanda.megingiard.macropad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadProfileMatchesTest {
    @Test
    fun matches_strictMatch_returnsTrue() {
        val profile =
            PadProfile(
                id = "test-1",
                name = "Test Game",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "Baba Is You.steam",
                        systemId = "pc",
                    ),
            )

        assertTrue(profile.matches("app.gamenative", "/storage/Roms/Baba Is You.steam", "pc"))
    }

    @Test
    fun matches_differentExtensionFallback_returnsTrue() {
        val profile =
            PadProfile(
                id = "test-1",
                name = "Test Game",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "Baba Is You.steam",
                        systemId = "pc",
                    ),
            )

        // Matches even with a different extension (.steamappid instead of .steam)
        assertTrue(profile.matches("app.gamenative", "/storage/Roms/Baba Is You.steamappid", "pc"))
    }

    @Test
    fun matches_differentExtensionFallbackNoDirectory_returnsTrue() {
        val profile =
            PadProfile(
                id = "test-1",
                name = "Test Game",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "Baba Is You.steam",
                        systemId = "pc",
                    ),
            )

        // Matches even with a different extension and no full directory path
        assertTrue(profile.matches("app.gamenative", "Baba Is You.steamappid", "pc"))
    }

    @Test
    fun matches_differentRomName_returnsFalse() {
        val profile =
            PadProfile(
                id = "test-1",
                name = "Test Game",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "Baba Is You.steam",
                        systemId = "pc",
                    ),
            )

        assertFalse(profile.matches("app.gamenative", "Other Game.steam", "pc"))
    }

    @Test
    fun matches_differentPackage_returnsFalse() {
        val profile =
            PadProfile(
                id = "test-1",
                name = "Test Game",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "Baba Is You.steam",
                        systemId = "pc",
                    ),
            )

        assertFalse(profile.matches("other.package", "Baba Is You.steam", "pc"))
    }

    @Test
    fun matches_genericAppProfile_returnsTrue() {
        val profile =
            PadProfile(
                id = "test-2",
                name = "Generic GameNative",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = null,
                        systemId = null,
                    ),
            )

        assertTrue(profile.matches("app.gamenative", null, null))
        assertTrue(profile.matches("app.gamenative", "Any ROM.zip", "snes"))
    }
}
