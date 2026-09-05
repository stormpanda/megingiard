package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RetroArchLauncherTest {
    private lateinit var context: Context
    private lateinit var launcher: RetroArchLauncher

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        launcher = RetroArchLauncher()
    }

    @Test
    fun testLauncherMetadata() {
        assertEquals("retroarch", launcher.id)
        assertEquals("RetroArch", launcher.displayName)
    }

    @Test
    fun testLaunchGameReturnsFalseWhenNotInstalled() =
        runTest {
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = "/storage/emulated/0/ROMs/snes/smw.sfc",
                    systemId = "snes",
                    displayId = 0,
                )
            assertFalse(launched)
        }

    @Test
    fun testLaunchGameReturnsFalseWhenNoCoreAvailable() =
        runTest {
            val pkg = "com.retroarch.aarch64"
            val packageInfo = PackageInfo().apply { packageName = pkg }
            shadowOf(context.packageManager).installPackage(packageInfo)

            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = "/storage/emulated/0/ROMs/unknown/game.bin",
                    systemId = "invalid_system_id",
                    displayId = 0,
                    retroArchCore = null,
                )
            assertFalse(launched)
        }

    @Test
    fun testLaunchGameWithExplicitCore() =
        runTest {
            val pkg = "com.retroarch.aarch64"
            val packageInfo = PackageInfo().apply { packageName = pkg }
            shadowOf(context.packageManager).installPackage(packageInfo)

            val romPath = "/storage/emulated/0/ROMs/gba/pokemon.gba"
            val coreName = "mgba_libretro_android.so"

            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = romPath,
                    systemId = "gba",
                    displayId = 0,
                    retroArchCore = coreName,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals(Intent.ACTION_MAIN, nextIntent.action)
            assertEquals(romPath, nextIntent.getStringExtra("ROM"))
            assertEquals("/data/data/$pkg/cores/$coreName", nextIntent.getStringExtra("LIBRETRO"))
            assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", nextIntent.component?.className)
        }
}
