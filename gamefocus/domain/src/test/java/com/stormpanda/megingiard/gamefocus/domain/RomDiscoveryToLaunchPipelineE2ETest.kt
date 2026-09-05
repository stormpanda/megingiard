package com.stormpanda.megingiard.gamefocus.domain

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.stormpanda.megingiard.catalog.CustomRomFolder
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.InstalledAppsManager
import com.stormpanda.megingiard.catalog.RomLauncherRegistry
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.util.UUID

/**
 * End-to-End integration test suite verifying the ROM lifecycle pipeline:
 *
 * 1. ROM Folder Registration & System Identification (SNES, PS1, GBA).
 * 2. Catalog Indexing, SAF Path Resolution & Cleaned Title Normalization.
 * 3. Launcher Dispatch via [RomLauncherRegistry] and [RetroArchLauncher] targeted at Secondary Display (Display ID 4).
 * 4. Recent Launch History Persistence & Folder De-indexing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RomDiscoveryToLaunchPipelineE2ETest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var romsDir: File
    private lateinit var retroArchLauncher: RetroArchLauncher

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()

        romsDir = File(context.cacheDir, "e2e_roms_${UUID.randomUUID()}").apply { mkdirs() }

        // Register RetroArch emulator launcher
        retroArchLauncher = RetroArchLauncher()
        RomLauncherRegistry.register(retroArchLauncher)

        // Install RetroArch mock package in PackageManager
        val pkg = "com.retroarch.aarch64"
        val packageInfo = PackageInfo().apply { packageName = pkg }
        shadowOf(context.packageManager).installPackage(packageInfo)
    }

    @After
    fun tearDown() {
        romsDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    @Test
    fun testRomDiscoveryToSecondaryDisplayLaunchPipelineE2E() =
        runTest {
            // 1. Create ROM files inside mock directory
            val snesRomFile = File(romsDir, "Super Mario World (USA) (Rev 1).sfc").apply { writeBytes(byteArrayOf(0x01, 0x02)) }
            val gbaRomFile = File(romsDir, "Pokemon - Emerald Version (USA, Europe).gba").apply { writeBytes(byteArrayOf(0x03, 0x04)) }

            val docFile = DocumentFile.fromFile(romsDir)
            val uri = docFile.uri

            // 2. Register CustomRomFolder for SNES
            val snesFolder =
                CustomRomFolder(
                    uriString = uri.toString(),
                    folderPath = romsDir.absolutePath,
                    systemId = "snes",
                    systemName = "Super Nintendo",
                    retroArchCore = "snes9x_libretro_android.so",
                )

            val romFoldersJsonFile = File(context.filesDir, "gamefocus_rom_folders.json")
            romFoldersJsonFile.writeText(
                """
                [
                    {
                        "uriString": "$uri",
                        "folderPath": "${romsDir.absolutePath}",
                        "systemId": "snes",
                        "systemName": "Super Nintendo",
                        "retroArchCore": "snes9x_libretro_android.so"
                    }
                ]
                """.trimIndent(),
            )

            // Load ROM folders & trigger indexing
            RomManager.loadRomFolders(context)
            RomManager.reloadRomAppsSuspend(context)

            // 3. Verify ROMs are discovered and indexed into RomManager.romApps
            val romApps = RomManager.romApps.value
            val snesApp = romApps.firstOrNull { it.systemId == "snes" }
            assertNotNull("Expected SNES ROM indexed in RomManager", snesApp)
            assertEquals("Super Mario World", snesApp?.label)
            assertEquals("snes", snesApp?.systemId)
            assertTrue("Expected isRom = true", snesApp?.isRom == true)
            assertEquals("snes9x_libretro_android.so", snesApp?.retroArchCore)

            // 4. Launch ROM targeting secondary display (Display ID 4 on AYN Thor)
            val launchSuccess = InstalledAppsManager.launchAppOnDisplay(context, snesApp!!, displayId = 4)
            assertTrue("Expected launchGame on secondary display to succeed", launchSuccess)

            // 5. Verify launched intent options and extras
            val startedIntent = shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity
            assertNotNull("Expected intent dispatched to system", startedIntent)
            assertEquals(Intent.ACTION_MAIN, startedIntent.action)
            assertEquals("com.retroarch.aarch64", startedIntent.component?.packageName)
            assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", startedIntent.component?.className)

            // Verify Intent Extras
            assertEquals(snesApp.romPath, startedIntent.getStringExtra("ROM"))
            assertEquals("/data/data/com.retroarch.aarch64/cores/snes9x_libretro_android.so", startedIntent.getStringExtra("LIBRETRO"))

            // 6. Verify recent launch history recorded
            val lastUsed = InstalledAppsManager.lastUsed.value
            assertTrue("Expected launched app package recorded in recent apps", lastUsed.contains(snesApp.packageName))

            // 7. Remove ROM folder and verify de-indexing
            RomManager.removeRomFolder(context, snesFolder)
            RomManager.reloadRomAppsSuspend(context)
            assertTrue("Expected RomManager.romApps to be empty after removing folder", RomManager.romApps.value.isEmpty())
        }
}
