package com.stormpanda.megingiard.gamefocus.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.EMULATOR_ID_YUZU
import com.stormpanda.megingiard.catalog.SwitchEmulators
import kotlinx.coroutines.test.runTest
import org.junit.After
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

private const val TAG = "SwitchLauncherTest"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SwitchLauncherTest {
    private lateinit var context: Context
    private lateinit var launcher: SwitchLauncher

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        SwitchEmulators.invalidateCache()
        launcher = SwitchLauncher()
    }

    @After
    fun tearDown() {
        SwitchEmulators.invalidateCache()
    }

    @Test
    fun testLauncherMetadata() {
        AppLog.d(TAG, "Testing launcher metadata")
        assertEquals(EMULATOR_ID_YUZU, launcher.id)
        assertEquals("Nintendo Switch", launcher.displayName)
    }

    @Test
    fun testLaunchGameReturnsFalseWhenNoEmulatorInstalled() =
        runTest {
            AppLog.d(TAG, "Testing launch failure when no emulator is installed")
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = "/storage/emulated/0/Switch/Zelda.nsp",
                    systemId = "switch",
                    displayId = 0,
                )
            assertFalse(launched)
        }

    @Test
    fun testLaunchGameWithAutoDetectedFirstInstalledEmulator() =
        runTest {
            AppLog.d(TAG, "Testing launch with auto-detected emulator")
            val edenPackage = "dev.eden.eden_emulator"
            val suyuPackage = "com.suyu.suyu"
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = edenPackage })
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = suyuPackage })

            val romPath = "/storage/emulated/0/Switch/Mario.xci"
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = romPath,
                    systemId = "switch",
                    displayId = 0,
                    retroArchCore = null,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals("android.nfc.action.TECH_DISCOVERED", nextIntent.action)
            assertEquals("file://$romPath", nextIntent.dataString)
            assertEquals(edenPackage, nextIntent.component?.packageName)
            assertEquals("org.yuzu.yuzu_emu.activities.EmulationActivity", nextIntent.component?.className)
        }

    @Test
    fun testLaunchGameWithExplicitPreferredPackage() =
        runTest {
            AppLog.d(TAG, "Testing launch with explicit preferred package")
            val edenPackage = "dev.eden.eden_emulator"
            val suyuPackage = "com.suyu.suyu"
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = edenPackage })
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = suyuPackage })

            val romPath = "/storage/emulated/0/Switch/Pokemon.nsp"
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = romPath,
                    systemId = "switch",
                    displayId = 0,
                    retroArchCore = suyuPackage,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals(suyuPackage, nextIntent.component?.packageName)
            assertEquals("org.yuzu.yuzu_emu.activities.EmulationActivity", nextIntent.component?.className)
        }

    @Test
    fun testLaunchGameFallbackWhenPreferredPackageNotInstalled() =
        runTest {
            AppLog.d(TAG, "Testing launch fallback when preferred package is missing")
            val edenPackage = "dev.eden.eden_emulator"
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = edenPackage })

            val romPath = "/storage/emulated/0/Switch/Metroid.nsp"
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = romPath,
                    systemId = "switch",
                    displayId = 0,
                    retroArchCore = "com.suyu.suyu",
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals(edenPackage, nextIntent.component?.packageName)
        }

    @Test
    fun testLaunchGameWithContentUriPassesContentUriToEmulator() =
        runTest {
            AppLog.d(TAG, "Testing launch with SAF content URI")
            val edenPackage = "dev.eden.eden_emulator"
            shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = edenPackage })

            val romPath = "/storage/emulated/0/Switch/Mario.xci"
            val romUri = "content://com.android.externalstorage.documents/tree/primary%3ASwitch/document/primary%3ASwitch%2FMario.xci"
            val launched =
                launcher.launchGame(
                    context = context,
                    romPath = romPath,
                    systemId = "switch",
                    displayId = 0,
                    retroArchCore = null,
                    romUri = romUri,
                )
            assertTrue(launched)

            val nextIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull(nextIntent)
            assertEquals("android.nfc.action.TECH_DISCOVERED", nextIntent.action)
            assertEquals(romUri, nextIntent.dataString)
            assertEquals(edenPackage, nextIntent.component?.packageName)
            assertEquals("org.yuzu.yuzu_emu.activities.EmulationActivity", nextIntent.component?.className)
            assertTrue(nextIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        }
}
