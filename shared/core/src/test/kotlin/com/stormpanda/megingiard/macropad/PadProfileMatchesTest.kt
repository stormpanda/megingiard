package com.stormpanda.megingiard.macropad

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadProfileMatchesTest {
    private fun testProfile(
        id: String = "test-1",
        name: String = "Test Game",
        packageName: String = "app.gamenative",
        romFileName: String? = "Baba Is You.steam",
        systemId: String? = "pc",
    ) = PadProfile(
        id = id,
        name = name,
        association =
            ProfileAssociation(
                packageName = packageName,
                romFileName = romFileName,
                systemId = systemId,
            ),
    )

    @Test
    fun matches_strictMatch_returnsTrue() {
        assertTrue(testProfile().matches("app.gamenative", "/storage/Roms/Baba Is You.steam", "pc"))
    }

    @Test
    fun matches_differentExtensionFallback_returnsTrue() {
        // Matches even with a different extension (.steamappid instead of .steam)
        assertTrue(testProfile().matches("app.gamenative", "/storage/Roms/Baba Is You.steamappid", "pc"))
    }

    @Test
    fun matches_differentExtensionFallbackNoDirectory_returnsTrue() {
        // Matches even with a different extension and no full directory path
        assertTrue(testProfile().matches("app.gamenative", "Baba Is You.steamappid", "pc"))
    }

    @Test
    fun matches_differentRomName_returnsFalse() {
        assertFalse(testProfile().matches("app.gamenative", "Other Game.steam", "pc"))
    }

    @Test
    fun matches_differentPackage_returnsFalse() {
        assertFalse(testProfile().matches("other.package", "Baba Is You.steam", "pc"))
    }

    @Test
    fun matches_genericAppProfile_returnsTrue() {
        val profile = testProfile(id = "test-2", name = "Generic GameNative", romFileName = null, systemId = null)
        assertTrue(profile.matches("app.gamenative", null, null))
        assertTrue(profile.matches("app.gamenative", "Any ROM.zip", "snes"))
    }

    @Test
    fun matches_spacingDifferences_returnsTrue() {
        val profile1 = testProfile(id = "test-3", name = "Ball x Pit Profile", romFileName = "BALL x PIT.steam")
        assertTrue(profile1.matches("app.gamenative", "BALLxPIT.steam", "pc"))

        val profile2 = testProfile(id = "test-4", name = "BallxPit Profile", romFileName = "BALLxPIT.steam")
        assertTrue(profile2.matches("app.gamenative", "BALL x PIT.steam", "pc"))
        assertTrue(profile2.matches("app.gamenative", "BALL_x-PIT.steam", "pc"))
    }

    @Test
    fun matches_isActiveProfileTrue_returnsTrueWhenPackageMatchesEvenIfRomPathMissing() {
        val profile = testProfile(name = "GameNative Game", romFileName = "BALL x PIT.steam")
        assertTrue(profile.matches("app.gamenative", null, "pc", isActiveProfile = true))
        assertTrue(profile.matches("app.gamenative", null, null, isActiveProfile = true))
    }

    @Test
    fun matches_isActiveProfileTrue_returnsFalseWhenPackageDoesNotMatch() {
        val profile = testProfile(name = "GameNative Game", romFileName = "BALL x PIT.steam")
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
