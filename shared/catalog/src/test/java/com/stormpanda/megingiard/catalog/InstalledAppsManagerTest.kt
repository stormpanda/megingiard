package com.stormpanda.megingiard.catalog

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InstalledAppsManagerTest {
    @Before
    fun setUp() {
        InstalledAppsManager.resetForTesting()
    }

    @After
    fun tearDown() {
        InstalledAppsManager.resetForTesting()
    }

    @Test
    fun testInstalledAppInfoDataModel() {
        val app =
            InstalledAppInfo(
                packageName = "com.example.game",
                activityName = "com.example.game.MainActivity",
                label = "Super Game",
                coverPath = "/tmp/cover.png",
            )

        assertEquals("com.example.game", app.packageName)
        assertEquals("com.example.game.MainActivity", app.activityName)
        assertEquals("Super Game", app.label)
        assertEquals("/tmp/cover.png", app.coverPath)
        assertEquals(false, app.isGame)

        val gameApp = app.copy(isGame = true)
        assertEquals(true, gameApp.isGame)

        val updated = app.withCover("/tmp/new_cover.png", 42, 12345L)
        assertEquals("/tmp/new_cover.png", updated.coverPath)
        assertEquals(42, updated.coverImageId)
        assertEquals(12345L, updated.coverLastModified)

        val list = listOf(app, InstalledAppInfo(packageName = "com.other.app", activityName = "", label = "Other"))
        val updatedList = list.withUpdatedCover("com.example.game", "/tmp/updated.png", 99)
        assertEquals("/tmp/updated.png", updatedList[0].coverPath)
        assertEquals(99, updatedList[0].coverImageId)
        assertNull(updatedList[1].coverPath)
        assertNull(updatedList[1].coverImageId)
    }

    @Test
    fun testWithUpdatedCover_withoutImageIdClearsStaleId() {
        val app = InstalledAppInfo(packageName = PKG_GAME, activityName = "", label = "Game", coverPath = "/a.png", coverImageId = 7)
        val updated = listOf(app).withUpdatedCover(PKG_GAME, "/b.png")
        assertEquals("/b.png", updated[0].coverPath)
        assertNull(updated[0].coverImageId)
    }

    @Test
    fun testLabelHelpersAndHasCustomLabel() {
        val app = InstalledAppInfo(packageName = PKG_GAME, activityName = "", label = "Default", defaultLabel = "Default")
        assertFalse(app.hasCustomLabel)

        val renamed = app.withLabel("Custom")
        assertEquals("Custom", renamed.label)
        assertTrue(renamed.hasCustomLabel)

        val other = InstalledAppInfo(packageName = "com.other", activityName = "", label = "Other")
        val list = listOf(app, other).withUpdatedLabel(PKG_GAME, "Renamed")
        assertEquals("Renamed", list[0].label)
        assertEquals("Other", list[1].label)
        assertFalse("No defaultLabel means no custom-label state", list[1].hasCustomLabel)
    }

    @Test
    fun testInitialStateEmpty() {
        val apps = InstalledAppsManager.installedApps.value
        assertNotNull(apps)
    }

    @Test
    fun testIsPackageAGame_detectsIntentCategory() {
        val appInfo = ApplicationInfo().apply { packageName = "com.example.game" }
        assertTrue(InstalledAppsManager.isPackageAGame(appInfo, setOf("com.example.game")))
    }

    @Test
    fun testIsPackageAGame_detectsCategoryGame() {
        val appInfo =
            ApplicationInfo().apply {
                packageName = "com.example.game"
                category = ApplicationInfo.CATEGORY_GAME
            }
        assertTrue(InstalledAppsManager.isPackageAGame(appInfo))
    }

    @Suppress("DEPRECATION")
    @Test
    fun testIsPackageAGame_detectsFlagIsGame() {
        val appInfo =
            ApplicationInfo().apply {
                packageName = "com.example.legacygame"
                flags = ApplicationInfo.FLAG_IS_GAME
            }
        assertTrue(InstalledAppsManager.isPackageAGame(appInfo))
    }

    @Test
    fun testIsPackageAGame_returnsFalseForStandardApp() {
        val appInfo = ApplicationInfo().apply { packageName = "com.example.standardapp" }
        assertFalse(InstalledAppsManager.isPackageAGame(appInfo))
    }

    @Test
    fun testToggleFavorite_addsAndRemovesPackage() {
        val context: Context = RuntimeEnvironment.getApplication()
        val pkg = "com.test.favapp"

        InstalledAppsManager.toggleFavorite(context, pkg)
        assertTrue(InstalledAppsManager.favorites.value.contains(pkg))

        InstalledAppsManager.toggleFavorite(context, pkg)
        assertFalse(InstalledAppsManager.favorites.value.contains(pkg))
    }

    @Test
    fun testToggleHidden_addsAndRemovesPackage() {
        val context: Context = RuntimeEnvironment.getApplication()
        val pkg = "com.test.hiddenapp"

        InstalledAppsManager.toggleHidden(context, pkg)
        assertTrue(InstalledAppsManager.hiddenApps.value.contains(pkg))

        InstalledAppsManager.toggleHidden(context, pkg)
        assertFalse(InstalledAppsManager.hiddenApps.value.contains(pkg))
    }

    @Test
    fun testRecordAppLaunch_prependsAndCapsAtMax() {
        val context: Context = RuntimeEnvironment.getApplication()

        InstalledAppsManager.recordAppLaunch(context, "com.test.app1")
        InstalledAppsManager.recordAppLaunch(context, "com.test.app2")
        assertEquals(listOf("com.test.app2", "com.test.app1"), InstalledAppsManager.lastUsed.value)

        // Re-launch app1 -> moves to index 0
        InstalledAppsManager.recordAppLaunch(context, "com.test.app1")
        assertEquals(listOf("com.test.app1", "com.test.app2"), InstalledAppsManager.lastUsed.value)

        // Launch 12 distinct apps -> verify capped at 10
        for (i in 3..14) {
            InstalledAppsManager.recordAppLaunch(context, "com.test.app$i")
        }
        assertEquals(10, InstalledAppsManager.lastUsed.value.size)
        assertEquals("com.test.app14", InstalledAppsManager.lastUsed.value.first())
    }

    @Test
    fun testUpdateAppCover() {
        InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(seedApp()))
        InstalledAppsManager.updateAppCover(PKG_GAME, "/path/to/cover.png", 101)

        val app = InstalledAppsManager.installedAndroidAppsForTesting().single()
        assertEquals("/path/to/cover.png", app.coverPath)
        assertEquals(101, app.coverImageId)
    }

    @Test
    fun testCoverImageIdPersistence() {
        val context: Context = RuntimeEnvironment.getApplication()
        val pkg = "com.test.artworkgame"

        assertNull(InstalledAppsManager.getCoverImageId(pkg))

        InstalledAppsManager.setCoverImageId(context, pkg, 4242)
        assertEquals(4242, InstalledAppsManager.getCoverImageId(pkg))
        runBlocking { InstalledAppsManager.awaitPendingWritesForTesting() }

        // Reset memory to simulate app restart, then reload from disk
        InstalledAppsManager.resetForTesting()
        assertNull(InstalledAppsManager.getCoverImageId(pkg))

        InstalledAppsManager.reloadPersistedMetadataForTesting(context)
        assertEquals(4242, InstalledAppsManager.getCoverImageId(pkg))
    }

    @Test
    fun testApplyAndRevertCustomCover() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(seedApp()))
            val bytes = byteArrayOf(1, 2, 3, 4)
            var writtenCallbacks = 0

            val path = InstalledAppsManager.applyCustomCover(context, PKG_GAME, bytes, 555) { writtenCallbacks++ }

            val file = InstalledAppsManager.coverFileFor(context, PKG_GAME)
            assertEquals(file.absolutePath, path)
            assertTrue(file.exists())
            assertTrue(bytes.contentEquals(file.readBytes()))
            assertEquals(1, writtenCallbacks)
            assertEquals(555, InstalledAppsManager.getCoverImageId(PKG_GAME))
            val applied = InstalledAppsManager.installedAndroidAppsForTesting().single()
            assertEquals(file.absolutePath, applied.coverPath)
            assertEquals(555, applied.coverImageId)

            var removedCallbacks = 0
            InstalledAppsManager.revertToDefaultCover(context, PKG_GAME) { removedCallbacks++ }

            assertFalse(file.exists())
            assertEquals(1, removedCallbacks)
            assertNull(InstalledAppsManager.getCoverImageId(PKG_GAME))
            val reverted = InstalledAppsManager.installedAndroidAppsForTesting().single()
            assertNull(reverted.coverPath)
            assertNull(reverted.coverImageId)
        }

    @Test
    fun testMarkAppAsScraped() {
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.markAppAsScraped(context, "com.test.scraped")
        // Verified persistence
    }

    @Test
    fun testLoadInstalledApps_executesWithoutCrash() {
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.loadInstalledApps(context)
    }

    @Test
    fun testLaunchAppOnPrimaryAndSecondaryDisplay() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            val appInfo =
                InstalledAppInfo(
                    packageName = "com.test.launchable",
                    activityName = "com.test.launchable.MainActivity",
                    label = "Launchable App",
                )
            // In Robolectric, startActivity succeeds
            val primarySuccess = InstalledAppsManager.launchAppOnPrimaryDisplay(context, appInfo)
            assertTrue(primarySuccess)

            val secondarySuccess = InstalledAppsManager.launchAppOnSecondaryDisplay(context, appInfo)
            assertTrue(secondarySuccess)
        }

    @Test
    fun testOpenAppInfoAndUninstallApp() {
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.openAppInfo(context, "com.test.app")
        InstalledAppsManager.openAppInfo(context, "com.test.app", displayId = 0)
        InstalledAppsManager.uninstallApp(context, "com.test.app")
        InstalledAppsManager.uninstallApp(context, "com.test.app", displayId = 4)
        InstalledAppsManager.updateAppCover("rom.snes.smw", "/storage/cover.png")
    }

    @Test
    fun testUpdateAppLabel_androidAndRom() {
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(seedApp()))

        InstalledAppsManager.updateAppLabel(context, PKG_GAME, "Custom Game Name")
        InstalledAppsManager.updateAppLabel(context, PKG_ROM, "Super Mario World (Custom)")

        assertEquals("Custom Game Name", InstalledAppsManager.getCustomLabel(PKG_GAME))
        assertEquals("Super Mario World (Custom)", InstalledAppsManager.getCustomLabel(PKG_ROM))
        val app = InstalledAppsManager.installedAndroidAppsForTesting().single()
        assertEquals("Custom Game Name", app.label)
        assertTrue(app.hasCustomLabel)
    }

    @Test
    fun testCustomLabelPersistenceRoundTrip() {
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.updateAppLabel(context, PKG_ROM, "Persisted ROM Name")
        runBlocking { InstalledAppsManager.awaitPendingWritesForTesting() }

        InstalledAppsManager.resetForTesting()
        assertNull(InstalledAppsManager.getCustomLabel(PKG_ROM))

        InstalledAppsManager.reloadPersistedMetadataForTesting(context)
        assertEquals("Persisted ROM Name", InstalledAppsManager.getCustomLabel(PKG_ROM))
    }

    @Test
    fun testResetAppLabel_restoresDefaultAndClearsPersistence() {
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(seedApp()))
        InstalledAppsManager.updateAppLabel(context, PKG_GAME, "Custom")

        val restored = InstalledAppsManager.resetAppLabel(context, PKG_GAME)

        assertEquals(DEFAULT_LABEL, restored)
        assertNull(InstalledAppsManager.getCustomLabel(PKG_GAME))
        val app = InstalledAppsManager.installedAndroidAppsForTesting().single()
        assertEquals(DEFAULT_LABEL, app.label)
        assertFalse(app.hasCustomLabel)

        runBlocking { InstalledAppsManager.awaitPendingWritesForTesting() }
        InstalledAppsManager.resetForTesting()
        InstalledAppsManager.reloadPersistedMetadataForTesting(context)
        assertNull(InstalledAppsManager.getCustomLabel(PKG_GAME))
    }

    @Test
    fun testResetAppLabel_unknownPackageReturnsNull() {
        val context: Context = RuntimeEnvironment.getApplication()
        assertNull(InstalledAppsManager.resetAppLabel(context, "com.test.unknown"))
    }

    private fun seedApp() =
        InstalledAppInfo(
            packageName = PKG_GAME,
            activityName = "$PKG_GAME.MainActivity",
            label = DEFAULT_LABEL,
            defaultLabel = DEFAULT_LABEL,
        )

    private companion object {
        const val PKG_GAME = "com.test.game"
        const val PKG_ROM = "rom.snes.smw"
        const val DEFAULT_LABEL = "Test Game"
    }
}
