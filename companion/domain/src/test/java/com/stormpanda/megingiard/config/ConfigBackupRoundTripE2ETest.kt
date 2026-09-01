package com.stormpanda.megingiard.config

import android.content.Context
import android.net.Uri
import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.MacroStep
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadButton
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * End-to-End integration test suite verifying configuration export and import round-trip:
 *
 * 1. Full-app backup export (.mgrd ZIP with settings, profiles, macros, background images, and SHA-256 checksum).
 * 2. Strict cryptographic checksum verification and tamper resistance.
 * 3. Complete state restoration across [SettingsManager] and [MacroPadState].
 * 4. Single-profile share export and isolated profile import without overriding global settings.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConfigBackupRoundTripE2ETest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val jsonCodec = Json { encodeDefaults = true }
    private lateinit var context: Context
    private lateinit var tempFile: File

    private val testMetadata =
        ExportMetadata(
            exportedAt = "2026-08-31T20:00:00Z",
            appVersionName = "1.2.0",
            appVersionCode = 120,
            deviceModel = "AYN Thor",
            author = "Megingiard Tester",
            description = "E2E Backup Roundtrip Test",
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        SettingsManager.resetForTesting(context)

        tempFile = File(context.cacheDir, "test_export_${UUID.randomUUID()}.mgrd")
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) tempFile.delete()
        ConfigManager.clearPendingImport()
        ConfigManager.clearInAppPendingImport()
        Dispatchers.resetMain()
    }

    @Test
    fun testFullBackupExportAndRestoreRoundTripE2E() =
        runTest(testDispatcher) {
            // 1. Setup rich settings state
            SettingsManager.setThemeMode(ThemeMode.VALHALLA)
            SettingsManager.setAccentColor(0xFF11AAFF.toInt())
            SettingsManager.setSteamGridDbApiToken("sgdb_e2e_token_987654")
            SettingsManager.setOverlayAtBottom(true)
            SettingsManager.setOverlayFadeOut(true)
            testScheduler.advanceUntilIdle()
            Thread.sleep(100)

            // 2. Setup rich MacroPad profile state with layouts, buttons, macros, and background images
            val macro1 =
                Macro(
                    id = "macro-hadouken-1",
                    name = "Hadouken",
                    steps =
                        listOf(
                            MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = 50L, btnCode = 304, label = "A"),
                        ),
                )

            val button1 =
                PadButton(
                    id = "btn-1",
                    label = "HP",
                    posX = 0.2f,
                    posY = 0.3f,
                    action = PadAction.GamepadButton(btnCode = 304, label = "HP"),
                )

            val button2 =
                PadButton(
                    id = "btn-2",
                    label = "Special",
                    posX = 0.5f,
                    posY = 0.3f,
                    action = PadAction.Macro(macroId = macro1.id),
                )

            val layoutId = "layout-fighting-1"
            val bgDir = File(context.filesDir, "backgrounds")
            bgDir.mkdirs()
            val bgFile = File(bgDir, "bg_$layoutId")
            bgFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) // Fake PNG header

            val layout1 =
                PadLayout(
                    id = layoutId,
                    name = "Fight Arcade",
                    buttons = listOf(button1, button2),
                    backgroundImagePath = "backgrounds/bg_$layoutId",
                )

            val fightingProfile =
                PadProfile(
                    id = "profile-fighting-id",
                    name = "Street Fighter 6",
                    layouts = listOf(layout1),
                    activeLayoutId = layoutId,
                    macros = listOf(macro1),
                    association =
                        ProfileAssociation(
                            packageName = "com.capcom.sf6",
                            systemId = "pc",
                        ),
                )

            val generalProfile =
                PadProfile(
                    id = "profile-general-id",
                    name = "General Media",
                    layouts = listOf(PadLayout(id = "layout-gen", name = "Media")),
                    activeLayoutId = "layout-gen",
                )

            MacroPadState.loadFrom(listOf(fightingProfile, generalProfile), fightingProfile.id)

            // 3. Build full export
            val export = ConfigManager.buildExport(testMetadata, context, includeBackgrounds = true)
            assertEquals(SCHEMA_VERSION, export.schemaVersion)
            assertEquals(2, export.profiles.size)
            assertTrue(export.settings.isNotEmpty())
            assertTrue(export.checksum.isNotBlank())

            // 4. Write export to .mgrd ZIP file
            val exportUri = Uri.fromFile(tempFile)
            ConfigManager.writeToUri(context, exportUri, export, includeBackgrounds = true)
            assertTrue(tempFile.exists())
            assertTrue(tempFile.length() > 0)

            // 5. Read and verify from URI
            val readResult = ConfigManager.readFromUri(context, exportUri, isInApp = true)
            assertTrue("Expected readFromUri to succeed", readResult.isSuccess)
            val parsedExport = readResult.getOrThrow()
            assertEquals(SCHEMA_VERSION, parsedExport.schemaVersion)
            assertEquals(export.checksum, parsedExport.checksum)
            assertEquals(2, parsedExport.profiles.size)

            val extractedImages = ConfigManager.getPendingInAppImages()
            assertTrue("Expected background image extracted from ZIP", extractedImages.containsKey(layoutId))

            // 6. Reset all settings and MacroPad profiles to clean/default state
            SettingsManager.setThemeMode(ThemeMode.DARK)
            SettingsManager.setAccentColor(0xFFFFFFFF.toInt())
            SettingsManager.setSteamGridDbApiToken("")
            SettingsManager.setOverlayAtBottom(false)
            SettingsManager.setOverlayFadeOut(false)
            testScheduler.advanceUntilIdle()
            Thread.sleep(100)
            MacroPadState.loadFrom(
                listOf(
                    PadProfile(
                        id = "default-clean",
                        name = "Clean Default",
                        layouts = listOf(PadLayout(id = "clean-layout", name = "Default")),
                    ),
                ),
                "default-clean",
            )

            // Verify settings are currently in reset state
            assertEquals(ThemeMode.DARK, SettingsManager.themeMode.value)
            assertEquals("", SettingsManager.steamGridDbApiToken.value)
            assertEquals(1, MacroPadState.profiles.value.size)

            // 7. Apply import
            ConfigManager.applyImport(context, parsedExport, extractedImages)
            testScheduler.advanceUntilIdle()
            Thread.sleep(100)

            // 8. Verify all settings and profiles are restored
            assertEquals(ThemeMode.VALHALLA, SettingsManager.themeMode.value)
            assertEquals(0xFF11AAFF.toInt(), SettingsManager.accentColor.value)
            assertEquals("sgdb_e2e_token_987654", SettingsManager.steamGridDbApiToken.value)
            assertTrue(SettingsManager.overlayAtBottom.value)
            assertTrue(SettingsManager.overlayFadeOut.value)

            val restoredProfiles = MacroPadState.profiles.value
            val restoredFightingProfile = restoredProfiles.firstOrNull { it.name == "Street Fighter 6" }
            assertNotNull("Expected restored Street Fighter 6 profile", restoredFightingProfile)
            assertEquals(1, restoredFightingProfile?.layouts?.size)
            assertEquals(
                2,
                restoredFightingProfile
                    ?.layouts
                    ?.first()
                    ?.buttons
                    ?.size,
            )
            assertEquals(1, restoredFightingProfile?.macros?.size)
            assertEquals("Hadouken", restoredFightingProfile?.macros?.first()?.name)
            assertEquals("com.capcom.sf6", restoredFightingProfile?.association?.packageName)
        }

    @Test
    fun testSingleProfileExportAndImportE2E() =
        runTest(testDispatcher) {
            // 1. Prepare single profile to share
            val macro =
                Macro(
                    id = "macro-jump-1",
                    name = "Super Jump",
                    steps = listOf(MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = 100L, btnCode = 20, label = "Jump")),
                )
            val profile =
                PadProfile(
                    id = "profile-jump-id",
                    name = "Platformer Pack",
                    layouts = listOf(PadLayout(id = "layout-plat", name = "Main")),
                    activeLayoutId = "layout-plat",
                    macros = listOf(macro),
                    association = ProfileAssociation(packageName = "com.nintendo.snes"),
                )

            // 2. Build profile-only export
            val export = ConfigManager.buildProfileExport(testMetadata, profile, context, includeBackgrounds = false)
            assertEquals(SCHEMA_VERSION, export.schemaVersion)
            assertEquals(1, export.profiles.size)
            assertTrue("Profile share export must not contain global settings", export.settings.isEmpty())

            // 3. Current app state has existing profiles and settings
            val initialTheme = SettingsManager.themeMode.value
            val initialProfileCount = MacroPadState.profiles.value.size

            // 4. Apply profile import
            ConfigManager.applyProfileImport(context, export)

            // 5. Verify settings remained unchanged and profile is appended
            assertEquals(initialTheme, SettingsManager.themeMode.value)
            val updatedProfiles = MacroPadState.profiles.value
            assertEquals(initialProfileCount + 1, updatedProfiles.size)
            assertTrue(updatedProfiles.any { it.name == "Platformer Pack" })
        }

    @Test
    fun testTamperedExportThrowsChecksumMismatchE2E() =
        runTest {
            val testProfile =
                PadProfile(
                    id = "profile-tamper-id",
                    name = "Original Tamper Target",
                    layouts = listOf(PadLayout(id = "l1", name = "Default")),
                )
            val export = ConfigManager.buildProfileExport(testMetadata, testProfile, includeBackgrounds = false)

            // Serialize export to JSON
            val validJson = jsonCodec.encodeToString(export)
            assertTrue("JSON must contain original profile name", validJson.contains("Original Tamper Target"))

            // Tamper JSON payload (modify profile name in profiles array) without updating checksum
            val tamperedJson = validJson.replace("Original Tamper Target", "Tampered Name")

            // Verify parseAndVerify throws Checksum mismatch error
            val exception =
                assertThrows(IllegalStateException::class.java) {
                    ConfigManager.parseAndVerify(tamperedJson)
                }
            assertTrue(
                "Expected checksum mismatch exception message",
                exception.message?.contains("Checksum mismatch") == true,
            )
        }
}
