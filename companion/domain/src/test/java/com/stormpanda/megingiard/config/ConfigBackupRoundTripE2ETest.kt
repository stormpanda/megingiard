package com.stormpanda.megingiard.config

import android.content.Context
import android.net.Uri
import com.stormpanda.megingiard.macropad.LayoutCanvasSpec
import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.MacroStep
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadButton
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.macropad.ProfileAssociation
import com.stormpanda.megingiard.macropad.THOR_BOTTOM_CANVAS_SPEC
import com.stormpanda.megingiard.mirror.CutoutMaskFileStore
import com.stormpanda.megingiard.mirror.ScreenCutout
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        File(context.filesDir, CutoutMaskFileStore.MASKS_DIR).deleteRecursively()
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

            val shiftTargetLayoutId = "layout-fighting-2"
            val button3 =
                PadButton(
                    id = "btn-3",
                    label = "Shift",
                    posX = 0.8f,
                    posY = 0.3f,
                    action = PadAction.SwitchToLayout(layoutId = shiftTargetLayoutId, holdToShift = true),
                )

            val button4 =
                PadButton(
                    id = "btn-4",
                    label = "To Media",
                    posX = 0.8f,
                    posY = 0.6f,
                    action = PadAction.SwitchToProfile(profileId = "profile-general-id"),
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
                    buttons = listOf(button1, button2, button3, button4),
                    backgroundImagePath = "backgrounds/bg_$layoutId",
                )

            val layout2 =
                PadLayout(
                    id = shiftTargetLayoutId,
                    name = "Fight Shift",
                )

            val fightingProfile =
                PadProfile(
                    id = "profile-fighting-id",
                    name = "Street Fighter 6",
                    layouts = listOf(layout1, layout2),
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
            val readResult = ConfigManager.readFromUri(context, exportUri)
            assertTrue("Expected readFromUri to succeed", readResult.isSuccess)
            val parsedArchive = readResult.getOrThrow()
            val parsedExport = parsedArchive.export
            assertEquals(SCHEMA_VERSION, parsedExport.schemaVersion)
            assertEquals(export.checksum, parsedExport.checksum)
            assertEquals(2, parsedExport.profiles.size)

            val extractedImages = parsedArchive.images
            assertTrue("Expected background image extracted from ZIP", extractedImages.containsKey("bg_$layoutId"))

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
            val restoredGeneralProfile = restoredProfiles.firstOrNull { it.name == "General Media" }
            assertNotNull("Expected restored Street Fighter 6 profile", restoredFightingProfile)
            assertNotNull("Expected restored General Media profile", restoredGeneralProfile)
            assertNotEquals("profile-general-id", restoredGeneralProfile!!.id)
            assertEquals(2, restoredFightingProfile?.layouts?.size)
            val restoredArcadeLayout = restoredFightingProfile!!.layouts.first { it.name == "Fight Arcade" }
            val restoredShiftLayout = restoredFightingProfile.layouts.first { it.name == "Fight Shift" }
            assertNotEquals(shiftTargetLayoutId, restoredShiftLayout.id)
            assertEquals(4, restoredArcadeLayout.buttons.size)
            val restoredShiftAction = restoredArcadeLayout.buttons.first { it.label == "Shift" }.action as PadAction.SwitchToLayout
            assertEquals(restoredShiftLayout.id, restoredShiftAction.layoutId)
            assertTrue(restoredShiftAction.holdToShift)
            val restoredMediaSwitch = restoredArcadeLayout.buttons.first { it.label == "To Media" }.action as PadAction.SwitchToProfile
            assertEquals(restoredGeneralProfile.id, restoredMediaSwitch.profileId)
            assertEquals(1, restoredFightingProfile.macros.size)
            assertEquals("Hadouken", restoredFightingProfile.macros.first().name)
            assertEquals("com.capcom.sf6", restoredFightingProfile.association?.packageName)
        }

    @Test
    fun testSingleProfileExportAndImportE2E() =
        runTest(testDispatcher) {
            testScheduler.advanceUntilIdle()
            // 1. Prepare single profile to share
            val macro =
                Macro(
                    id = "macro-jump-1",
                    name = "Super Jump",
                    steps = listOf(MacroStep.GamepadButtonTap(startTimeMs = 0L, durationMs = 100L, btnCode = 20, label = "Jump")),
                )
            val switchButton =
                PadButton(
                    id = "btn-plat-switch",
                    label = "To Alt",
                    posX = 0.5f,
                    posY = 0.5f,
                    action = PadAction.SwitchToLayout(layoutId = "layout-plat-alt", holdToShift = false),
                )
            val profile =
                PadProfile(
                    id = "profile-jump-id",
                    name = "Platformer Pack",
                    layouts =
                        listOf(
                            PadLayout(id = "layout-plat", name = "Main", buttons = listOf(switchButton)),
                            PadLayout(id = "layout-plat-alt", name = "Alt"),
                        ),
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
            testScheduler.advanceUntilIdle()

            // 5. Verify settings remained unchanged, profile is appended, and SwitchToLayout is remapped
            assertEquals(initialTheme, SettingsManager.themeMode.value)
            val updatedProfiles = MacroPadState.profiles.value
            assertEquals(initialProfileCount + 1, updatedProfiles.size)
            val importedPack = updatedProfiles.firstOrNull { it.name == "Platformer Pack" }
            assertNotNull(importedPack)
            val importedMainLayout = importedPack!!.layouts.first { it.name == "Main" }
            val importedAltLayout = importedPack.layouts.first { it.name == "Alt" }
            assertNotEquals("layout-plat-alt", importedAltLayout.id)
            val importedSwitch = importedMainLayout.buttons.single().action as PadAction.SwitchToLayout
            assertEquals(importedAltLayout.id, importedSwitch.layoutId)
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

    @Test
    fun testImportRejectsMediaPathsOutsideMediaSubdirectories() =
        runTest(testDispatcher) {
            val datastoreDir = File(context.filesDir, "datastore").apply { mkdirs() }
            val secretFile = File(datastoreDir, "megingiard_settings.preferences_pb")
            secretFile.writeBytes("secret_token".toByteArray(Charsets.UTF_8))

            val maliciousLayout =
                PadLayout(
                    id = "layout-malicious",
                    name = "Malicious",
                    backgroundImagePath = "datastore/megingiard_settings.preferences_pb",
                    maskImagePath = "datastore/megingiard_settings.preferences_pb",
                )
            val maliciousProfile =
                PadProfile(
                    id = "profile-malicious",
                    name = "Malicious Profile",
                    layouts = listOf(maliciousLayout),
                    activeLayoutId = maliciousLayout.id,
                )

            val export = ConfigManager.buildProfileExport(testMetadata, maliciousProfile, context, includeBackgrounds = true)
            ConfigManager.applyProfileImport(context, export, emptyMap())
            testScheduler.advanceUntilIdle()

            val imported = MacroPadState.profiles.value.first { it.name == "Malicious Profile" }
            val importedLayout = imported.layouts.single()
            assertEquals(null, importedLayout.backgroundImagePath)
            assertEquals(null, importedLayout.maskImagePath)
        }

    @Test
    fun testProfileExportImportBundlesCutoutMasksPreservesDesignCanvasAndIsolatesRepeatedImportsE2E() =
        runTest(testDispatcher) {
            MacroPadState.setHostSecondaryCanvas(THOR_BOTTOM_CANVAS_SPEC)

            val rpDuoCanvas = LayoutCanvasSpec(widthPx = 1280, heightPx = 960)
            val srcLayoutId = "layout-rpduo-cutout-e2e"
            val srcCutoutId = "cutout-rpduo-hud-e2e"
            val layoutFreezeBytes = byteArrayOf(10, 20, 30, 40)
            val maskPngBytes = byteArrayOf(1, 2, 3, 4, 5)
            val varianceBytes = byteArrayOf(6, 7, 8, 9)
            val cutoutFreezeBytes = byteArrayOf(11, 12, 13)

            File(context.filesDir, CutoutMaskFileStore.MASKS_DIR).mkdirs()
            CutoutMaskFileStore.freezeFrameFile(context.filesDir, srcLayoutId).writeBytes(layoutFreezeBytes)
            CutoutMaskFileStore.baseMaskFile(context.filesDir, srcCutoutId).writeBytes(maskPngBytes)
            CutoutMaskFileStore.varianceFile(context.filesDir, srcCutoutId).writeBytes(varianceBytes)
            CutoutMaskFileStore.freezeFrameFile(context.filesDir, srcCutoutId).writeBytes(cutoutFreezeBytes)

            val minButton =
                PadButton(
                    id = "btn-rpduo-min",
                    label = "HUD",
                    posX = 0.15f,
                    posY = 0.85f,
                    widthPx = 60f,
                    heightPx = 60f,
                    action = PadAction.KeyboardKey(keycode = 30, label = "A"),
                )
            val cutout =
                ScreenCutout(
                    id = srcCutoutId,
                    name = "Speedometer",
                    srcX = 0.7f,
                    srcY = 0.75f,
                    srcWidth = 0.25f,
                    srcHeight = 0.2f,
                    destX = 0.6f,
                    destY = 0.1f,
                    destWidth = 0.35f,
                    destHeight = 0.3f,
                    hasTransparencyMask = true,
                )
            val sourceProfile =
                PadProfile(
                    id = "profile-rpduo-share-e2e",
                    name = "RP Duo Racing HUD",
                    layouts =
                        listOf(
                            PadLayout(
                                id = srcLayoutId,
                                name = "Cockpit",
                                designCanvas = rpDuoCanvas,
                                buttons = listOf(minButton),
                                mirrorCutouts = listOf(cutout),
                            ),
                        ),
                    activeLayoutId = srcLayoutId,
                )

            val export =
                ConfigManager.buildProfileExport(
                    metadata = testMetadata,
                    profile = sourceProfile,
                    context = context,
                    includeBackgrounds = true,
                )
            val exportUri = Uri.fromFile(tempFile)
            ConfigManager.writeToUri(context, exportUri, export, includeBackgrounds = true)

            // Wipe source cutout_masks so import relies purely on extracted .mgrd ZIP entries
            File(context.filesDir, CutoutMaskFileStore.MASKS_DIR).deleteRecursively()

            val readResult = ConfigManager.readFromUri(context, exportUri)
            assertTrue("Expected valid .mgrd archive: ${readResult.exceptionOrNull()}", readResult.isSuccess)
            val parsed = readResult.getOrThrow()
            assertEquals(4, parsed.images.count { it.key.startsWith("cutout_masks/") })

            // Import twice to verify fresh UUID isolation across repeated imports
            ConfigManager.applyProfileImport(context, parsed.export, parsed.images)
            ConfigManager.applyProfileImport(context, parsed.export, parsed.images)
            testScheduler.advanceUntilIdle()

            val importedProfiles = MacroPadState.profiles.value.filter { it.name.startsWith("RP Duo Racing HUD") }
            assertEquals(2, importedProfiles.size)
            assertEquals("RP Duo Racing HUD", importedProfiles[0].name)
            assertEquals("RP Duo Racing HUD (2)", importedProfiles[1].name)

            val firstLayout = importedProfiles[0].layouts.single()
            val secondLayout = importedProfiles[1].layouts.single()
            val firstCutout = firstLayout.mirrorCutouts.single()
            val secondCutout = secondLayout.mirrorCutouts.single()

            assertEquals(rpDuoCanvas, firstLayout.designCanvas)
            assertEquals(rpDuoCanvas, secondLayout.designCanvas)
            assertEquals(60f, firstLayout.buttons.single().widthPx, 0.001f)
            assertEquals(60f, secondLayout.buttons.single().widthPx, 0.001f)

            assertNotEquals(srcLayoutId, firstLayout.id)
            assertNotEquals(firstLayout.id, secondLayout.id)
            assertNotEquals(srcCutoutId, firstCutout.id)
            assertNotEquals(firstCutout.id, secondCutout.id)

            for ((layout, importedCutout) in listOf(firstLayout to firstCutout, secondLayout to secondCutout)) {
                assertArrayEquals(
                    layoutFreezeBytes,
                    CutoutMaskFileStore.freezeFrameFile(context.filesDir, layout.id).readBytes(),
                )
                assertArrayEquals(
                    maskPngBytes,
                    CutoutMaskFileStore.baseMaskFile(context.filesDir, importedCutout.id).readBytes(),
                )
                assertArrayEquals(
                    varianceBytes,
                    CutoutMaskFileStore.varianceFile(context.filesDir, importedCutout.id).readBytes(),
                )
                assertArrayEquals(
                    cutoutFreezeBytes,
                    CutoutMaskFileStore.freezeFrameFile(context.filesDir, importedCutout.id).readBytes(),
                )
            }

            // Deleting the first imported layout's cutout files must leave the second import untouched
            CutoutMaskFileStore.deleteLayoutCutoutAssets(context.filesDir, firstLayout)
            assertTrue(CutoutMaskFileStore.freezeFrameFile(context.filesDir, secondLayout.id).exists())
            assertTrue(CutoutMaskFileStore.baseMaskFile(context.filesDir, secondCutout.id).exists())
            assertTrue(CutoutMaskFileStore.varianceFile(context.filesDir, secondCutout.id).exists())
            assertTrue(CutoutMaskFileStore.freezeFrameFile(context.filesDir, secondCutout.id).exists())
        }
}
