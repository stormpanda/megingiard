package com.stormpanda.megingiard.macropad

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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

    @Test
    fun matches_spacingDifferences_returnsTrue() {
        val profile1 =
            PadProfile(
                id = "test-3",
                name = "Ball x Pit Profile",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "BALL x PIT.steam",
                        systemId = "pc",
                    ),
            )

        // Matches when active session has no spaces
        assertTrue(profile1.matches("app.gamenative", "BALLxPIT.steam", "pc"))

        val profile2 =
            PadProfile(
                id = "test-4",
                name = "BallxPit Profile",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "BALLxPIT.steam",
                        systemId = "pc",
                    ),
            )

        // Matches when active session has spaces
        assertTrue(profile2.matches("app.gamenative", "BALL x PIT.steam", "pc"))

        // Matches with underscores/dashes differences
        assertTrue(profile2.matches("app.gamenative", "BALL_x-PIT.steam", "pc"))
    }

    @Test
    fun matches_isActiveProfileTrue_returnsTrueWhenPackageMatchesEvenIfRomPathMissing() {
        val profile =
            PadProfile(
                id = "test-1",
                name = "GameNative Game",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "BALL x PIT.steam",
                        systemId = "pc",
                    ),
            )

        // When isActiveProfile is true and package matches, returns true even if focusedRomPath is null
        assertTrue(profile.matches("app.gamenative", null, "pc", isActiveProfile = true))
        assertTrue(profile.matches("app.gamenative", null, null, isActiveProfile = true))
    }

    @Test
    fun matches_isActiveProfileTrue_returnsFalseWhenPackageDoesNotMatch() {
        val profile =
            PadProfile(
                id = "test-1",
                name = "GameNative Game",
                association =
                    ProfileAssociation(
                        packageName = "app.gamenative",
                        romFileName = "BALL x PIT.steam",
                        systemId = "pc",
                    ),
            )

        // When isActiveProfile is true but package is a launcher or different app, returns false
        assertFalse(profile.matches("com.android.launcher3", null, null, isActiveProfile = true))
        assertFalse(profile.matches("com.miHoYo.GenshinImpact", null, null, isActiveProfile = true))
    }

    @Test
    fun deserialize_migratesAssociatedPackageToAssociation() {
        val oldJson =
            """
            {
                "id": "old-id-1",
                "name": "Old Profile",
                "associatedPackage": "com.retroarch"
            }
            """.trimIndent()

        val parsed = Json.decodeFromString<PadProfile>(oldJson)
        assertEquals("old-id-1", parsed.id)
        assertEquals("Old Profile", parsed.name)
        assertEquals("com.retroarch", parsed.association?.packageName)
        assertEquals(null, parsed.association?.systemId)
        assertEquals(null, parsed.association?.romFileName)
    }
}
