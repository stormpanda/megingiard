package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import kotlinx.coroutines.test.runTest
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPackageManager

private const val TAG = "GameFocusSessionTrackerTest"
private const val PRIMARY_DISPLAY_ID = 0
private const val TEST_SECONDARY_DISPLAY_ID = 4
private const val PKG_TOP = "com.example.topgame"
private const val PKG_BOTTOM = "com.example.bottomcompanion"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@Suppress("DEPRECATION")
class GameFocusSessionTrackerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        AppLog.d(TAG, "Setting up GameFocusSessionTrackerTest")
        context = RuntimeEnvironment.getApplication()
        GameFocusSessionTracker.resetForTesting()
    }

    @Test
    fun testInitialStateHasNoSession() {
        assertFalse(GameFocusSessionTracker.hasActiveSession())
        assertNull(GameFocusSessionTracker.lastTopPackage)
        assertNull(GameFocusSessionTracker.lastBottomPackage)
        assertNull(GameFocusSessionTracker.lastTopApp)
        assertNull(GameFocusSessionTracker.lastBottomApp)
    }

    @Test
    fun testRecordTopAndBottomLaunch() {
        val topApp =
            InstalledAppInfo(
                packageName = PKG_TOP,
                activityName = "$PKG_TOP.MainActivity",
                label = "Top Game",
                isGame = true,
            )
        val bottomApp =
            InstalledAppInfo(
                packageName = PKG_BOTTOM,
                activityName = "$PKG_BOTTOM.MainActivity",
                label = "Bottom Companion",
                isGame = false,
            )

        GameFocusSessionTracker.recordTopLaunch(topApp)
        assertTrue(GameFocusSessionTracker.hasActiveSession())
        assertEquals(PKG_TOP, GameFocusSessionTracker.lastTopPackage)
        assertEquals(topApp, GameFocusSessionTracker.lastTopApp)
        assertNull(GameFocusSessionTracker.lastBottomPackage)

        GameFocusSessionTracker.recordBottomLaunch(bottomApp)
        assertEquals(PKG_BOTTOM, GameFocusSessionTracker.lastBottomPackage)
        assertEquals(bottomApp, GameFocusSessionTracker.lastBottomApp)

        GameFocusSessionTracker.clearSession()
        assertFalse(GameFocusSessionTracker.hasActiveSession())
        assertNull(GameFocusSessionTracker.lastTopPackage)
        assertNull(GameFocusSessionTracker.lastBottomPackage)
    }

    @Test
    fun testRecordWindowChanged() {
        GameFocusSessionTracker.recordWindowChanged(
            displayId = PRIMARY_DISPLAY_ID,
            packageName = PKG_TOP,
            secondaryDisplayId = TEST_SECONDARY_DISPLAY_ID,
        )
        assertEquals(PKG_TOP, GameFocusSessionTracker.lastTopPackage)

        GameFocusSessionTracker.recordWindowChanged(
            displayId = TEST_SECONDARY_DISPLAY_ID,
            packageName = PKG_BOTTOM,
            secondaryDisplayId = TEST_SECONDARY_DISPLAY_ID,
        )
        assertEquals(PKG_BOTTOM, GameFocusSessionTracker.lastBottomPackage)
    }

    @Test
    fun testRecordWindowChangedPreservesRomSessionWhenEmulatorWindowChanges() {
        val romApp =
            InstalledAppInfo(
                packageName = "rom.n64.sm64",
                activityName = "",
                label = "Super Mario 64",
                isRom = true,
                romPath = "/roms/sm64.z64",
                systemId = "n64",
                emulatorPackage = "com.retroarch.aarch64",
            )
        GameFocusSessionTracker.recordTopLaunch(romApp)
        assertEquals(romApp, GameFocusSessionTracker.lastTopApp)

        // Emulator window state change arrives on primary display
        GameFocusSessionTracker.recordWindowChanged(
            displayId = PRIMARY_DISPLAY_ID,
            packageName = "com.retroarch.aarch64",
            secondaryDisplayId = TEST_SECONDARY_DISPLAY_ID,
        )

        // ROM session must be preserved
        assertEquals(romApp, GameFocusSessionTracker.lastTopApp)
        assertEquals("rom.n64.sm64", GameFocusSessionTracker.lastTopPackage)
    }

    @Test
    fun testRecordWindowChangedPreservesRomSessionWhenRetroArchCoreChanges() {
        val romApp =
            InstalledAppInfo(
                packageName = "rom.n64.zelda",
                activityName = "",
                label = "The Legend of Zelda",
                isRom = true,
                romPath = "/roms/zelda.z64",
                systemId = "n64",
                retroArchCore = "mupen64plus_next_libretro_android.so",
            )
        GameFocusSessionTracker.recordTopLaunch(romApp)
        assertEquals(romApp, GameFocusSessionTracker.lastTopApp)

        GameFocusSessionTracker.recordWindowChanged(
            displayId = PRIMARY_DISPLAY_ID,
            packageName = "com.retroarch",
            secondaryDisplayId = TEST_SECONDARY_DISPLAY_ID,
        )

        assertEquals(romApp, GameFocusSessionTracker.lastTopApp)
        assertEquals("rom.n64.zelda", GameFocusSessionTracker.lastTopPackage)
    }

    @Test
    fun testRestorePreviousSessionReturnsFalseWhenEmpty() =
        runTest {
            val restored = GameFocusSessionTracker.restorePreviousSession(context)
            assertFalse(restored)
        }

    @Test
    fun testRestorePreviousSessionLaunchesTrackedPackages() =
        runTest {
            GameFocusSessionTracker.overrideSecondaryDisplayIdForTesting = TEST_SECONDARY_DISPLAY_ID
            val shadowPm = shadowOf(context.packageManager)

            installMockPackage(shadowPm, PKG_TOP)
            installMockPackage(shadowPm, PKG_BOTTOM)

            GameFocusSessionTracker.recordWindowChanged(
                displayId = PRIMARY_DISPLAY_ID,
                packageName = PKG_TOP,
                secondaryDisplayId = TEST_SECONDARY_DISPLAY_ID,
            )
            GameFocusSessionTracker.recordWindowChanged(
                displayId = TEST_SECONDARY_DISPLAY_ID,
                packageName = PKG_BOTTOM,
                secondaryDisplayId = TEST_SECONDARY_DISPLAY_ID,
            )

            val restored = GameFocusSessionTracker.restorePreviousSession(context)
            assertTrue(restored)

            val shadowApp = shadowOf(RuntimeEnvironment.getApplication())
            val firstIntent = shadowApp.nextStartedActivity
            val secondIntent = shadowApp.nextStartedActivity
            assertNotNull(firstIntent)
            assertNotNull(secondIntent)

            val intents = listOfNotNull(firstIntent, secondIntent)
            val topIntent = intents.find { it.`package` == PKG_TOP }
            assertNotNull(topIntent)
            val topFlags = topIntent!!.flags
            assertTrue((topFlags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
            assertTrue((topFlags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0)

            val bottomIntent = intents.find { it.`package` == PKG_BOTTOM }
            assertNotNull(bottomIntent)
            val bottomFlags = bottomIntent!!.flags
            assertTrue((bottomFlags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
            assertTrue((bottomFlags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0)
        }

    private fun installMockPackage(
        shadowPm: ShadowPackageManager,
        pkgName: String,
    ) {
        val packageInfo =
            PackageInfo().apply {
                packageName = pkgName
                activities =
                    arrayOf(
                        ActivityInfo().apply {
                            packageName = pkgName
                            name = "$pkgName.MainActivity"
                            applicationInfo = ApplicationInfo().apply { packageName = pkgName }
                        },
                    )
            }
        shadowPm.installPackage(packageInfo)

        val launcherIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                `package` = pkgName
            }
        val resolveInfo =
            ResolveInfo().apply {
                activityInfo = packageInfo.activities!![0]
            }
        shadowPm.addResolveInfoForIntent(launcherIntent, resolveInfo)
    }
}
