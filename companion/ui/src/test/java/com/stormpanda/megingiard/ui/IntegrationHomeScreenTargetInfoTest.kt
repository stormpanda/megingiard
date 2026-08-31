package com.stormpanda.megingiard.ui

import com.stormpanda.megingiard.catalog.InstalledAppInfo
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
}
