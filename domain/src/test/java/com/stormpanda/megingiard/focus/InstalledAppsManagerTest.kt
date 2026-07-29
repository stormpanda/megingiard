package com.stormpanda.megingiard.focus

import android.content.Context
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val TAG = "InstalledAppsManagerTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InstalledAppsManagerTest {
    @Test
    fun testInstalledAppInfoDataModel() {
        val app =
            InstalledAppInfo(
                packageName = "com.example.game",
                activityName = "com.example.game.MainActivity",
                label = "Super Game",
                icon = null,
                coverPath = "/tmp/cover.png",
            )

        assertEquals("com.example.game", app.packageName)
        assertEquals("com.example.game.MainActivity", app.activityName)
        assertEquals("Super Game", app.label)
        assertEquals("/tmp/cover.png", app.coverPath)
        assertNull(app.icon)
        assertEquals(false, app.isGame)

        val gameApp = app.copy(isGame = true)
        assertEquals(true, gameApp.isGame)
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
}
