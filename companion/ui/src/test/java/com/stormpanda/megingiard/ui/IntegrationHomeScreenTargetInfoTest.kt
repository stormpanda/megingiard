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

    @Test
    fun resolveTargetAppInfo_hoveredPackagePriority() {
        val target =
            resolveTargetAppInfo(
                hoveredPackage = "com.example.game",
                hoveredAppLabel = "Hovered Title",
                hoveredRomPath = "/roms/hovered.sfc",
                hoveredSystemId = "snes",
                activeSession = activeGameSession,
                lastDetectedSession = lastGameSession,
                focusedAppPackageName = "com.other.app",
                focusedRomPath = null,
                installedApps = listOf(testApp),
            )

        assertEquals("com.example.game", target.pkg)
        assertEquals("Hovered Title", target.label)
        assertEquals("/roms/hovered.sfc", target.romPath)
        assertEquals("snes", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_hoveredPackageFallbackToInstalledAppsLabel() {
        val target =
            resolveTargetAppInfo(
                hoveredPackage = "com.example.game",
                hoveredAppLabel = null,
                hoveredRomPath = null,
                hoveredSystemId = null,
                activeSession = null,
                lastDetectedSession = null,
                focusedAppPackageName = null,
                focusedRomPath = null,
                installedApps = listOf(testApp),
                resolveAppLabel = { "Fallback Label" },
            )

        assertEquals("com.example.game", target.pkg)
        assertEquals("Example Game", target.label)
    }

    @Test
    fun resolveTargetAppInfo_hoveredPackageFallbackToResolverLambda() {
        val target =
            resolveTargetAppInfo(
                hoveredPackage = "com.unknown.package",
                hoveredAppLabel = null,
                hoveredRomPath = null,
                hoveredSystemId = null,
                activeSession = null,
                lastDetectedSession = null,
                focusedAppPackageName = null,
                focusedRomPath = null,
                installedApps = emptyList(),
                resolveAppLabel = { pkg -> "Resolved ($pkg)" },
            )

        assertEquals("com.unknown.package", target.pkg)
        assertEquals("Resolved (com.unknown.package)", target.label)
    }

    @Test
    fun resolveTargetAppInfo_activeSessionPriorityWhenNotHovering() {
        val target =
            resolveTargetAppInfo(
                hoveredPackage = null,
                hoveredAppLabel = null,
                hoveredRomPath = null,
                hoveredSystemId = null,
                activeSession = activeGameSession,
                lastDetectedSession = lastGameSession,
                focusedAppPackageName = "com.example.game",
                focusedRomPath = null,
                installedApps = listOf(testApp),
            )

        assertEquals("com.retroarch.game", target.pkg)
        assertEquals("Super Mario World", target.label)
        assertEquals("/sdcard/roms/snes/smw.sfc", target.romPath)
        assertEquals("snes", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_focusedAppMatchingLastDetectedSession() {
        val target =
            resolveTargetAppInfo(
                hoveredPackage = null,
                hoveredAppLabel = null,
                hoveredRomPath = null,
                hoveredSystemId = null,
                activeSession = null,
                lastDetectedSession = lastGameSession,
                focusedAppPackageName = "com.retroarch.lastgame",
                focusedRomPath = "/override/path.sfc",
                installedApps = listOf(testApp),
            )

        assertEquals("com.retroarch.lastgame", target.pkg)
        assertEquals("zelda.sfc", target.label)
        assertEquals("/sdcard/roms/snes/zelda.sfc", target.romPath)
        assertEquals("snes", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_focusedAppDifferentFromLastDetectedSession() {
        val target =
            resolveTargetAppInfo(
                hoveredPackage = null,
                hoveredAppLabel = null,
                hoveredRomPath = null,
                hoveredSystemId = null,
                activeSession = null,
                lastDetectedSession = lastGameSession,
                focusedAppPackageName = "com.example.game",
                focusedRomPath = null,
                installedApps = listOf(testApp),
            )

        assertEquals("com.example.game", target.pkg)
        assertEquals("Example Game", target.label)
        assertNull(target.romPath)
        assertNull(target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_lastDetectedSessionFallbackWhenNothingActive() {
        val target =
            resolveTargetAppInfo(
                hoveredPackage = null,
                hoveredAppLabel = null,
                hoveredRomPath = null,
                hoveredSystemId = null,
                activeSession = null,
                lastDetectedSession = lastGameSession,
                focusedAppPackageName = null,
                focusedRomPath = null,
                installedApps = emptyList(),
            )

        assertEquals("com.retroarch.lastgame", target.pkg)
        assertEquals("zelda.sfc", target.label)
        assertEquals("/sdcard/roms/snes/zelda.sfc", target.romPath)
        assertEquals("snes", target.systemId)
    }

    @Test
    fun resolveTargetAppInfo_allNullReturnsEmptyTargetInfo() {
        val target =
            resolveTargetAppInfo(
                hoveredPackage = null,
                hoveredAppLabel = null,
                hoveredRomPath = null,
                hoveredSystemId = null,
                activeSession = null,
                lastDetectedSession = null,
                focusedAppPackageName = null,
                focusedRomPath = null,
                installedApps = emptyList(),
            )

        assertNull(target.pkg)
        assertNull(target.label)
        assertNull(target.romPath)
        assertNull(target.systemId)
    }
}
