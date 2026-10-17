package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.stormpanda.megingiard.catalog.PcEmulators
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PcLauncherTest {
    private lateinit var context: Context
    private lateinit var launcher: PcLauncher

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        PcEmulators.invalidateCache()
        launcher = PcLauncher()
    }

    @Test
    fun testLauncherMetadata() {
        assertEquals("pc", launcher.id)
        assertEquals("PC", launcher.displayName)
    }

    @Test
    fun testLaunchGameReturnsFalseWhenNotInstalled() =
        runTest {
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = "/storage/emulated/0/Steam/620.steam",
                    systemId = "steam",
                    displayId = 0,
                )
            assertFalse(launched)
        }

    @Test
    fun testLaunchGameWithNumericFilenameSteamAppId() =
        runTest {
            val pkg = "app.gamenative"
            val packageInfo = PackageInfo().apply { packageName = pkg }
            shadowOf(context.packageManager).installPackage(packageInfo)

            // Create temporary dummy file for 620.steam
            val tempFile = File(context.cacheDir, "620.steam")
            tempFile.writeText("")

            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = tempFile.absolutePath,
                    systemId = "steam",
                    displayId = 0,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals("$pkg.LAUNCH_GAME", nextIntent.action)
            assertEquals(620, nextIntent.getIntExtra("app_id", -1))
            assertEquals("$pkg.MainActivity", nextIntent.component?.className)

            tempFile.delete()
        }

    @Test
    fun testLaunchGameWithContentSteamAppId() =
        runTest {
            val pkg = "app.gamenative"
            val packageInfo = PackageInfo().apply { packageName = pkg }
            shadowOf(context.packageManager).installPackage(packageInfo)

            // Create temporary dummy file named Portal2.steam containing "620"
            val tempFile = File(context.cacheDir, "Portal2.steam")
            tempFile.writeText("620")

            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = tempFile.absolutePath,
                    systemId = "steam",
                    displayId = 0,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals("$pkg.LAUNCH_GAME", nextIntent.action)
            assertEquals(620, nextIntent.getIntExtra("app_id", -1))

            tempFile.delete()
        }

    @Test
    fun testLaunchGameWithoutAppIdFallbackToRomExtra() =
        runTest {
            val pkg = "app.gamenative"
            val packageInfo = PackageInfo().apply { packageName = pkg }
            shadowOf(context.packageManager).installPackage(packageInfo)

            val tempFile = File(context.cacheDir, "custom_app.exe")
            tempFile.writeText("binary_data")

            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = tempFile.absolutePath,
                    systemId = "pc",
                    displayId = 0,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals(Intent.ACTION_MAIN, nextIntent.action)
            assertEquals(tempFile.absolutePath, nextIntent.getStringExtra("ROM"))

            tempFile.delete()
        }

    @Test
    fun testLaunchGameWithDroidDeckWhenPreferred() =
        runTest {
            val pkgGameNative = "app.gamenative"
            val pkgDroidDeck = "com.droiddeck.launcher"
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgGameNative })
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = pkgDroidDeck })

            val tempFile = File(context.cacheDir, "367520.steam")
            tempFile.writeText("")

            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = tempFile.absolutePath,
                    systemId = "pc",
                    displayId = 0,
                    retroArchCore = pkgDroidDeck,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals("$pkgDroidDeck.LAUNCH_GAME", nextIntent.action)
            assertEquals(367520, nextIntent.getIntExtra("app_id", -1))
            assertEquals("$pkgDroidDeck.MainActivity", nextIntent.component?.className)

            tempFile.delete()
        }

    @Test
    fun testParseSteamAppIdFromSafUriWhenPosixFileDoesNotExist() {
        val appId =
            launcher.parseSteamAppId(
                context = context,
                romPath = "/storage/6914-318F/Steam/1145360.steam",
                romUri = "content://com.android.externalstorage.documents/tree/6914-318F%3ASteam/document/6914-318F%3ASteam%2F1145360.steam",
            )
        assertEquals(1145360, appId)
    }
}
