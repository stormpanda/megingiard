package com.stormpanda.megingiard.ui

import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.session.ActiveGameSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure JVM unit tests for [resolveTargetAppInfo] in [IntegrationHomeScreen.kt].
 */
class IntegrationHomeScreenTargetInfoTest {
    private val testApp =
        InstalledAppInfo(
            packageName = "com.example.game",
            activityName = "com.example.game.MainActivity",
            label = "Example Game",
            coverPath = null,
            isGame = true,
            coverLastModified = 0L,
        )

    private val activeGameSession =
        ActiveGameSession(
            packageName = "com.retroarch.game",
            gameTitle = "Super Mario World",
            romPath = "/sdcard/roms/snes/smw.sfc",
            systemId = "snes",
        )

    private val lastGameSession =
        ActiveGameSession(
            packageName = "com.retroarch.lastgame",
            gameTitle = "Zelda Link to the Past",
            romPath = "/sdcard/roms/snes/zelda.sfc",
            systemId = "snes",
        )

    private fun resolve(
        hoveredPackage: String? = null,
        hoveredAppLabel: String? = null,
        hoveredRomPath: String? = null,
        hoveredRomIdentifier: String? = null,
        hoveredSystemId: String? = null,
        activeSession: ActiveGameSession? = null,
        lastDetectedSession: ActiveGameSession? = null,
        focusedAppPackageName: String? = null,
        focusedRomPath: String? = null,
        focusedRomIdentifier: String? = null,
        installedApps: List<InstalledAppInfo> = listOf(testApp),
        resolveAppLabel: (String) -> String? = { null },
    ) = resolveTargetAppInfo(
        hoveredPackage = hoveredPackage,
        hoveredAppLabel = hoveredAppLabel,
        hoveredRomPath = hoveredRomPath,
        hoveredRomIdentifier = hoveredRomIdentifier,
        hoveredSystemId = hoveredSystemId,
        activeSession = activeSession,
        lastDetectedSession = lastDetectedSession,
        focusedAppPackageName = focusedAppPackageName,
        focusedRomPath = focusedRomPath,
        focusedRomIdentifier = focusedRomIdentifier,
        installedApps = installedApps,
        resolveAppLabel = resolveAppLabel,
    )

    @Test
    fun resolveTargetAppInfo_hoveredPackagePriority() {
        val target =
            resolve(
                hoveredPackage = "com.example.game",
                hoveredAppLabel = "Hovered Title",
                hoveredRomPath = "/roms/hovered.sfc",
                hoveredRomIdentifier = "hovered.sfc",
                hoveredSystemId = "snes",
                activeSession = activeGameSession,
                lastDetectedSession = lastGameSession,
                focusedAppPackageName = "com.other.app",
            )

        assertEquals("com.example.game", target.pkg)
        assertEquals("Hovered Title", target.label)
        assertEquals("/roms/hovered.sfc", target.romPath)
        assertEquals("hovered.sfc", target.romIdentifier)
        assertEquals("snes", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_hoveredPackageFallbackToInstalledAppsLabel() {
        val target =
            resolve(
                hoveredPackage = "com.example.game",
                resolveAppLabel = { "Fallback Label" },
            )

        assertEquals("com.example.game", target.pkg)
        assertEquals("Example Game", target.label)
    }

    @Test
    fun resolveTargetAppInfo_hoveredPackageFallbackToResolverLambda() {
        val target =
            resolve(
                hoveredPackage = "com.unknown.package",
                installedApps = emptyList(),
                resolveAppLabel = { pkg -> "Resolved ($pkg)" },
            )

        assertEquals("com.unknown.package", target.pkg)
        assertEquals("Resolved (com.unknown.package)", target.label)
    }

    @Test
    fun resolveTargetAppInfo_activeSessionPriorityWhenNotHovering() {
        val target =
            resolve(
                activeSession = activeGameSession,
                lastDetectedSession = lastGameSession,
                focusedAppPackageName = "com.example.game",
            )

        assertEquals("com.retroarch.game", target.pkg)
        assertEquals("Super Mario World", target.label)
        assertEquals("/sdcard/roms/snes/smw.sfc", target.romPath)
        assertEquals("snes", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_focusedAppMatchingLastDetectedSession() {
        val target =
            resolve(
                lastDetectedSession = lastGameSession,
                focusedAppPackageName = "com.retroarch.lastgame",
                focusedRomPath = "/override/path.sfc",
            )

        assertEquals("com.retroarch.lastgame", target.pkg)
        assertEquals("zelda.sfc", target.label)
        assertEquals("/sdcard/roms/snes/zelda.sfc", target.romPath)
        assertEquals("snes", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_focusedAppDifferentFromLastDetectedSession() {
        val target =
            resolve(
                lastDetectedSession = lastGameSession,
                focusedAppPackageName = "com.example.game",
            )

        assertEquals("com.example.game", target.pkg)
        assertEquals("Example Game", target.label)
        assertNull(target.romPath)
        assertNull(target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_lastDetectedSessionFallbackWhenNothingActive() {
        val target =
            resolve(
                lastDetectedSession = lastGameSession,
                installedApps = emptyList(),
            )

        assertEquals("com.retroarch.lastgame", target.pkg)
        assertEquals("zelda.sfc", target.label)
        assertEquals("/sdcard/roms/snes/zelda.sfc", target.romPath)
        assertEquals("snes", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_allNullReturnsEmptyTargetInfo() {
        val target = resolve(installedApps = emptyList())

        assertNull(target.pkg)
        assertNull(target.label)
        assertNull(target.romPath)
        assertNull(target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_gameNativeSessionWithRomIdentifier() {
        val session =
            ActiveGameSession(
                packageName = "app.gamenative",
                gameTitle = "Boltgun",
                romPath = null,
                romIdentifier = "Boltgun.steam",
                systemId = "pc",
            )
        val target = resolve(activeSession = session)

        assertEquals("app.gamenative", target.pkg)
        assertEquals("Boltgun", target.label)
        assertNull(target.romPath)
        assertEquals("Boltgun.steam", target.romIdentifier)
        assertEquals("pc", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_activeSessionWithoutRomPath_fallsBackToFocusedRomPathAndId() {
        val session =
            ActiveGameSession(
                packageName = "app.gamenative",
                gameTitle = "20 Minutes Till Dawn",
                romPath = null,
                romIdentifier = "20MinuteTillDawn.steam",
                systemId = "pc",
            )
        val target =
            resolve(
                activeSession = session,
                focusedAppPackageName = "app.gamenative",
                focusedRomPath = "/storage/emulated/0/ROMs/steam/20 Minutes Till Dawn.steam",
                focusedRomIdentifier = "20 Minutes Till Dawn.steam",
            )

        assertEquals("app.gamenative", target.pkg)
        assertEquals("20 Minutes Till Dawn", target.label)
        assertEquals("/storage/emulated/0/ROMs/steam/20 Minutes Till Dawn.steam", target.romPath)
        assertEquals("20MinuteTillDawn.steam", target.romIdentifier)
        assertEquals("pc", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_activeSessionWithoutRomIdAndNoFocusedRom_fallsBackToTitleId() {
        val session =
            ActiveGameSession(
                packageName = "org.citra.emu",
                gameTitle = "Pokemon X",
                romPath = null,
                romIdentifier = null,
                systemId = "3ds",
                titleId = "0004000000055D00",
            )
        val target =
            resolve(
                activeSession = session,
                focusedAppPackageName = "org.citra.emu",
                focusedRomPath = null,
                focusedRomIdentifier = null,
            )

        assertEquals("org.citra.emu", target.pkg)
        assertEquals("Pokemon X", target.label)
        assertNull(target.romPath)
        assertEquals("0004000000055D00", target.romIdentifier)
        assertEquals("3ds", target.systemId)
    }

    @Test
    fun resolveAssociationTargetLabel_nullAssociationReturnsNull() {
        val label = resolveAssociationTargetLabel(null)
        assertNull(label)
    }

    @Test
    fun resolveAssociationTargetLabel_romFileNameTakesPriority() {
        val assoc =
            ProfileAssociation(
                packageName = "com.retroarch.game",
                systemId = "snes",
                romFileName = "Super Mario World.sfc",
            )
        val label = resolveAssociationTargetLabel(assoc) { "RetroArch" }
        assertEquals("Super Mario World.sfc", label)
    }

    @Test
    fun resolveAssociationTargetLabel_fallsBackToResolvedAppLabel() {
        val assoc =
            ProfileAssociation(
                packageName = "com.retroarch.game",
                systemId = null,
                romFileName = null,
            )
        val label =
            resolveAssociationTargetLabel(assoc) { pkg ->
                if (pkg == "com.retroarch.game") "RetroArch Emulator" else null
            }
        assertEquals("RetroArch Emulator", label)
    }

    @Test
    fun resolveAssociationTargetLabel_fallsBackToPackageNameWhenResolverReturnsNull() {
        val assoc =
            ProfileAssociation(
                packageName = "com.unknown.emulator",
                systemId = null,
                romFileName = null,
            )
        val label = resolveAssociationTargetLabel(assoc) { null }
        assertEquals("com.unknown.emulator", label)
    }
}
