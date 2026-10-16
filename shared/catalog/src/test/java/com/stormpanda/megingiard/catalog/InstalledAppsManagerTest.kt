package com.stormpanda.megingiard.catalog

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

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
        val context: Context = RuntimeEnvironment.getApplication()
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

        val coverFile = InstalledAppsManager.coverFileFor(context, "com.example.game")
        coverFile.parentFile?.mkdirs()
        coverFile.writeBytes(byteArrayOf(1, 2, 3))
        InstalledAppsManager.setCoverImageId(context, "com.example.game", 42)

        val iconFile = InstalledAppsManager.libraryIconFileFor(context, "com.example.game")
        iconFile.parentFile?.mkdirs()
        iconFile.writeBytes(byteArrayOf(4, 5, 6))
        InstalledAppsManager.setLibraryIconRef(context, "com.example.game", LibraryIconRef(777, "icons"))

        val enriched = InstalledAppsManager.enrichAppInfo(app, context)
        assertEquals(coverFile.absolutePath, enriched.coverPath)
        assertEquals(42, enriched.coverImageId)
        assertEquals(coverFile.lastModified(), enriched.coverLastModified)
        assertEquals(iconFile.absolutePath, enriched.libraryIconPath)
        assertEquals(777, enriched.libraryIconImageId)
        assertEquals("icons", enriched.libraryIconType)
        assertEquals(iconFile.lastModified(), enriched.libraryIconLastModified)

        coverFile.delete()
        iconFile.delete()
    }

    @Test
    fun testEnrichAppInfo_withoutImageIdClearsStaleId() {
        val context: Context = RuntimeEnvironment.getApplication()
        val coverFile = InstalledAppsManager.coverFileFor(context, PKG_GAME)
        coverFile.parentFile?.mkdirs()
        coverFile.writeBytes(byteArrayOf(1))
        val app = InstalledAppInfo(packageName = PKG_GAME, activityName = "", label = "Game", coverPath = "/a.png", coverImageId = 7)
        val updated = InstalledAppsManager.enrichAppInfo(app, context)
        assertEquals(coverFile.absolutePath, updated.coverPath)
        assertNull(updated.coverImageId)
        coverFile.delete()
    }

    @Test
    fun testLabelHelpersAndHasCustomLabel() {
        val app = InstalledAppInfo(packageName = PKG_GAME, activityName = "", label = "Default", defaultLabel = "Default")
        assertFalse(app.hasCustomLabel)

        val renamed = app.copy(label = "Custom")
        assertEquals("Custom", renamed.label)
        assertTrue(renamed.hasCustomLabel)

        val other = InstalledAppInfo(packageName = "com.other", activityName = "", label = "Other")
        assertFalse("No defaultLabel means no custom-label state", other.hasCustomLabel)
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
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(seedApp()))
        val coverFile = InstalledAppsManager.coverFileFor(context, PKG_GAME)
        coverFile.parentFile?.mkdirs()
        coverFile.writeBytes(byteArrayOf(1, 2, 3))
        InstalledAppsManager.setCoverImageId(context, PKG_GAME, 101)
        InstalledAppsManager.refreshAppMetadata(context, PKG_GAME)

        val app = InstalledAppsManager.installedAndroidAppsForTesting().single()
        assertEquals(coverFile.absolutePath, app.coverPath)
        assertEquals(101, app.coverImageId)
        coverFile.delete()
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
    fun testApplyAndRevertCustomLibraryIcon() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(seedApp()))
            val testBmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val stream = ByteArrayOutputStream()
            testBmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val bytes = stream.toByteArray()
            var writtenCallbacks = 0

            val path = InstalledAppsManager.applyCustomLibraryIcon(context, PKG_GAME, bytes, 777, "icons") { writtenCallbacks++ }

            val file = InstalledAppsManager.libraryIconFileFor(context, PKG_GAME)
            assertEquals(file.absolutePath, path)
            assertTrue(file.exists())
            assertTrue(bytes.contentEquals(file.readBytes()))
            assertEquals(1, writtenCallbacks)
            val iconRef = InstalledAppsManager.getLibraryIconRef(PKG_GAME)
            assertNotNull(iconRef)
            assertEquals(777, iconRef?.id)
            assertEquals("icons", iconRef?.type)
            val applied = InstalledAppsManager.installedAndroidAppsForTesting().single()
            assertEquals(file.absolutePath, applied.libraryIconPath)
            assertEquals(777, applied.libraryIconImageId)
            assertEquals("icons", applied.libraryIconType)
            assertEquals(file.lastModified(), applied.libraryIconLastModified)

            var removedCallbacks = 0
            InstalledAppsManager.revertToDefaultLibraryIcon(context, PKG_GAME) { removedCallbacks++ }

            assertFalse(file.exists())
            assertEquals(1, removedCallbacks)
            assertNull(InstalledAppsManager.getLibraryIconRef(PKG_GAME))
            val reverted = InstalledAppsManager.installedAndroidAppsForTesting().single()
            assertNull(reverted.libraryIconPath)
            assertNull(reverted.libraryIconImageId)
            assertNull(reverted.libraryIconType)
            assertEquals(0L, reverted.libraryIconLastModified)
        }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun testApplyCustomLibraryIconWithUndecodableBytesReturnsNull() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(seedApp()))
            val corruptBytes = byteArrayOf(1, 2, 3, 4, 5)

            val path = InstalledAppsManager.applyCustomLibraryIcon(context, PKG_GAME, corruptBytes, 999, "icons")
            assertNull(path)
            val file = InstalledAppsManager.libraryIconFileFor(context, PKG_GAME)
            assertFalse(file.exists())
        }

    @Test
    fun testApplyCustomLibraryIconDownsamplesOversizedImage() =
        runTest {
            val context: Context = RuntimeEnvironment.getApplication()
            InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(seedApp()))
            val largeBitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val stream = ByteArrayOutputStream()
            largeBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val largeBytes = stream.toByteArray()

            val path = InstalledAppsManager.applyCustomLibraryIcon(context, PKG_GAME, largeBytes, 888, "logos")
            assertNotNull(path)
            val file = File(path!!)
            assertTrue(file.exists())

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            assertTrue(bounds.outWidth <= 256)
            assertTrue(bounds.outHeight <= 256)
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
        InstalledAppsManager.refreshAppMetadata(context, "rom.snes.smw")
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

    @Test
    fun testShouldAutoScrape_truthTable() {
        assertTrue(shouldAutoScrape(isOptedOut = false, wasAttempted = false, hasFile = false))
        assertFalse(shouldAutoScrape(isOptedOut = true, wasAttempted = false, hasFile = false))
        assertFalse(shouldAutoScrape(isOptedOut = false, wasAttempted = true, hasFile = false))
        assertFalse(shouldAutoScrape(isOptedOut = false, wasAttempted = false, hasFile = true))
        assertFalse(shouldAutoScrape(isOptedOut = true, wasAttempted = true, hasFile = true))
    }

    @Test
    fun testArtworkOptOuts_laterReloadDoesNotOverwriteInMemoryChoice() {
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.setCoverOptedOut(context, PKG_GAME, true)
        runBlocking { InstalledAppsManager.awaitPendingWritesForTesting() }

        // Simulate a stale on-disk snapshot (e.g. a save that has not been flushed yet).
        File(context.filesDir, "gamefocus_artwork_optouts.json").writeText("""{"cover":[],"libraryIcon":[]}""")
        InstalledAppsManager.reloadPersistedMetadataForTesting(context)

        assertTrue(InstalledAppsManager.isCoverOptedOut(PKG_GAME))
    }

    @Test
    fun testMetadataMaps_laterReloadDoesNotOverwriteInMemoryChanges() {
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.updateAppLabel(context, PKG_ROM, "Fresh Name")
        InstalledAppsManager.setCoverImageId(context, PKG_GAME, COVER_ID)
        InstalledAppsManager.setLibraryIconRef(context, PKG_GAME, LibraryIconRef(id = COVER_ID, type = LibraryIconRef.TYPE_ICONS))
        runBlocking { InstalledAppsManager.awaitPendingWritesForTesting() }

        // Simulate stale on-disk snapshots (e.g. saves that have not been flushed yet).
        listOf("gamefocus_app_names.json", "gamefocus_cover_image_ids.json", "gamefocus_library_icon_ids.json")
            .forEach { File(context.filesDir, it).writeText("{}") }
        InstalledAppsManager.reloadPersistedMetadataForTesting(context)

        assertEquals("Fresh Name", InstalledAppsManager.getCustomLabel(PKG_ROM))
        assertEquals(COVER_ID, InstalledAppsManager.getCoverImageId(PKG_GAME))
        assertEquals(COVER_ID, InstalledAppsManager.getLibraryIconRef(PKG_GAME)?.id)
    }

    @Test
    fun testRecordScrapeAttempt_mergesWithNotYetLoadedDiskState() {
        val context: Context = RuntimeEnvironment.getApplication()
        File(context.filesDir, "gamefocus_scrape_attempts.json").writeText("""{"cover":["com.disk.app"]}""")

        InstalledAppsManager.recordScrapeAttempt(context, "com.new.app", ArtworkKind.COVER)

        assertTrue(InstalledAppsManager.wasAttempted("com.disk.app", ArtworkKind.COVER))
        assertTrue(InstalledAppsManager.wasAttempted("com.new.app", ArtworkKind.COVER))
    }

    @Test
    fun testArtworkOptOuts_persistenceAndCatalogUpdate() {
        val context: Context = RuntimeEnvironment.getApplication()
        InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(seedApp()))

        assertFalse(InstalledAppsManager.isCoverOptedOut(PKG_GAME))
        assertFalse(InstalledAppsManager.isLibraryIconOptedOut(PKG_GAME))

        InstalledAppsManager.setCoverOptedOut(context, PKG_GAME, true)
        InstalledAppsManager.setLibraryIconOptedOut(context, PKG_GAME, true)

        assertTrue(InstalledAppsManager.isCoverOptedOut(PKG_GAME))
        assertTrue(InstalledAppsManager.isLibraryIconOptedOut(PKG_GAME))
        val inMemoryApp = InstalledAppsManager.installedAndroidAppsForTesting().single()
        assertTrue(inMemoryApp.coverOptedOut)
        assertTrue(inMemoryApp.libraryIconOptedOut)

        runBlocking { InstalledAppsManager.awaitPendingWritesForTesting() }
        InstalledAppsManager.resetForTesting()
        assertFalse(InstalledAppsManager.isCoverOptedOut(PKG_GAME))
        assertFalse(InstalledAppsManager.isLibraryIconOptedOut(PKG_GAME))

        InstalledAppsManager.reloadPersistedMetadataForTesting(context)
        assertTrue(InstalledAppsManager.isCoverOptedOut(PKG_GAME))
        assertTrue(InstalledAppsManager.isLibraryIconOptedOut(PKG_GAME))
    }

    @Test
    fun testScrapeAttempts_perKind() {
        val context: Context = RuntimeEnvironment.getApplication()

        assertFalse(InstalledAppsManager.wasAttempted("com.new.app", ArtworkKind.COVER))
        InstalledAppsManager.recordScrapeAttempt(context, "com.new.app", ArtworkKind.COVER)
        assertTrue(InstalledAppsManager.wasAttempted("com.new.app", ArtworkKind.COVER))
        assertFalse(InstalledAppsManager.wasAttempted("com.new.app", ArtworkKind.LOGO))
        assertFalse(InstalledAppsManager.wasAttempted("com.new.app", ArtworkKind.LIBRARY_ICON))
    }

    @Test
    fun testUpdateAppLogo_updatesState() {
        val context: Context = RuntimeEnvironment.getApplication()
        val app = seedApp()
        InstalledAppsManager.setInstalledAndroidAppsForTesting(listOf(app))
        val logoFile = InstalledAppsManager.logoFileFor(context, PKG_GAME)
        logoFile.parentFile?.mkdirs()
        logoFile.writeBytes(byteArrayOf(1, 2, 3))
        InstalledAppsManager.refreshAppMetadata(context, PKG_GAME)

        val updated = InstalledAppsManager.installedAndroidAppsForTesting().single()
        assertEquals(logoFile.lastModified(), updated.logoLastModified)
        logoFile.delete()
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
        const val COVER_ID = 4242
    }
}
