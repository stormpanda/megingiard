package com.stormpanda.megingiard.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadLayout
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.security.HmacUtil
import com.stormpanda.megingiard.settings.KEY_ACCENT_COLOR
import com.stormpanda.megingiard.settings.KEY_MACROPAD_AMBIENT_DIM
import com.stormpanda.megingiard.settings.KEY_MACROPAD_RECENT_COLORS
import com.stormpanda.megingiard.settings.KEY_OVERLAY_AT_BOTTOM
import com.stormpanda.megingiard.settings.KEY_PRIVD_DEADZONE_LEFT
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Tests for the [ConfigManager.ExportKind] sealed interface and
 * [ConfigManager.ImportMode] enum introduced with the per-profile
 * export/import feature.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConfigExportImportStructureTest {
    private val testJson = Json { encodeDefaults = true }
    private val testMetadata =
        ExportMetadata(
            exportedAt = "2025-01-01T00:00:00Z",
            appVersionName = "1.0.0",
            appVersionCode = 1,
        )

    private val testProfile =
        PadProfile(
            id = "profile-uuid-test",
            name = "SharedProfile",
        )

    private fun invokeComputeChecksum(
        settings: Map<String, Any> = emptyMap(),
        profiles: List<PadProfile> = emptyList(),
        imageHashes: Map<String, String> = emptyMap(),
    ): String {
        val checksumMethod =
            ConfigManager::class.java
                .getDeclaredMethod(
                    "computeChecksum",
                    Map::class.java,
                    List::class.java,
                    Map::class.java,
                ).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return checksumMethod.invoke(ConfigManager, settings, profiles, imageHashes) as String
    }

    private fun setupTestDataStore(prefix: String): DataStore<Preferences> {
        val tempFile = File.createTempFile(prefix, ".preferences_pb").apply { deleteOnExit() }
        val testDataStore = PreferenceDataStoreFactory.create(produceFile = { tempFile })
        SettingsManager::class.java.getDeclaredField("dataStore").apply {
            isAccessible = true
            set(SettingsManager, testDataStore)
        }
        SettingsManager::class.java.getDeclaredField("initialized").apply {
            isAccessible = true
            set(SettingsManager, true)
        }
        return testDataStore
    }

    private fun zipArchive(
        jsonStr: String,
        images: Map<String, ByteArray>,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("config.json"))
            zos.write(jsonStr.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            images.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun unzipArchive(zipBytes: ByteArray): Pair<String?, Map<String, ByteArray>> {
        var extractedJson: String? = null
        val extractedImages = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "config.json") {
                    extractedJson = zis.readBytes().toString(Charsets.UTF_8)
                } else if (entry.name.startsWith("backgrounds/")) {
                    extractedImages[entry.name] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return Pair(extractedJson, extractedImages)
    }

    private suspend fun assertBackgroundRestoration(
        isProfileImport: Boolean,
        layoutId: String,
        profileName: String,
        imageKey: String,
    ) {
        val context: Context = RuntimeEnvironment.getApplication()
        val mockImageBytes = "mock_bytes_$layoutId".toByteArray(Charsets.UTF_8)
        val bgLayout = PadLayout(id = layoutId, name = "LayoutWithBg", backgroundImagePath = "backgrounds/bg_$layoutId")
        val bgProfile = PadProfile(id = "p-$layoutId", name = profileName, layouts = listOf(bgLayout))
        val export =
            MegingiardExport(
                schemaVersion = SCHEMA_VERSION,
                metadata = testMetadata,
                checksum = "dummy",
                settings = emptyMap(),
                profiles = listOf(bgProfile),
            )
        val imagesMap = mapOf(imageKey to mockImageBytes)

        if (isProfileImport) {
            ConfigManager.applyProfileImport(context, export, imagesMap)
        } else {
            ConfigManager.applyImport(context, export, imagesMap)
        }

        val importedProfile = MacroPadState.profiles.value.find { it.name == profileName }
        assertNotNull(importedProfile)
        val importedLayout = importedProfile!!.layouts.first()
        assertNotNull(importedLayout.backgroundImagePath)
        assertTrue(importedLayout.backgroundImagePath!!.startsWith("backgrounds/bg_"))

        val savedFile = File(context.filesDir, importedLayout.backgroundImagePath!!)
        assertTrue(savedFile.exists())
        assertTrue(savedFile.readBytes().contentEquals(mockImageBytes))
    }

    // ── ImportMode enum ───────────────────────────────────────────────────────

    @Test
    fun `ImportMode has BACKUP_RESTORE and PROFILE_SHARE entries`() {
        val entries = ConfigManager.ImportMode.entries
        assertTrue(entries.contains(ConfigManager.ImportMode.BACKUP_RESTORE))
        assertTrue(entries.contains(ConfigManager.ImportMode.PROFILE_SHARE))
        assertEquals(2, entries.size)
    }

    // ── ExportKind sealed interface ───────────────────────────────────────────

    @Test
    fun `ExportKind_Backup carries metadata`() {
        val kind: ConfigManager.ExportKind = ConfigManager.ExportKind.Backup(testMetadata)
        assertTrue(kind is ConfigManager.ExportKind.Backup)
        assertEquals(testMetadata, (kind as ConfigManager.ExportKind.Backup).metadata)
    }

    @Test
    fun `ExportKind_ProfileShare carries metadata and profile`() {
        val kind: ConfigManager.ExportKind = ConfigManager.ExportKind.ProfileShare(testMetadata, testProfile)
        assertTrue(kind is ConfigManager.ExportKind.ProfileShare)
        val share = kind as ConfigManager.ExportKind.ProfileShare
        assertEquals(testMetadata, share.metadata)
        assertEquals(testProfile.id, share.profile.id)
        assertEquals(testProfile.name, share.profile.name)
    }

    @Test
    fun `ExportKind subtypes are distinct`() {
        val backup: ConfigManager.ExportKind = ConfigManager.ExportKind.Backup(testMetadata)
        val share: ConfigManager.ExportKind = ConfigManager.ExportKind.ProfileShare(testMetadata, testProfile)
        assertTrue(backup is ConfigManager.ExportKind.Backup)
        assertTrue(share is ConfigManager.ExportKind.ProfileShare)
    }

    @Test
    fun `ExportKind_Backup carries includeBackgrounds flag`() {
        val kindWithBg: ConfigManager.ExportKind = ConfigManager.ExportKind.Backup(testMetadata, includeBackgrounds = true)
        val kindWithoutBg: ConfigManager.ExportKind = ConfigManager.ExportKind.Backup(testMetadata, includeBackgrounds = false)
        assertTrue((kindWithBg as ConfigManager.ExportKind.Backup).includeBackgrounds)
        assertTrue(!(kindWithoutBg as ConfigManager.ExportKind.Backup).includeBackgrounds)
    }

    @Test
    fun `ExportKind_ProfileShare carries includeBackgrounds flag`() {
        val kindWithBg: ConfigManager.ExportKind =
            ConfigManager.ExportKind.ProfileShare(
                testMetadata,
                testProfile,
                includeBackgrounds = true,
            )
        val kindWithoutBg: ConfigManager.ExportKind =
            ConfigManager.ExportKind.ProfileShare(
                testMetadata,
                testProfile,
                includeBackgrounds = false,
            )
        assertTrue((kindWithBg as ConfigManager.ExportKind.ProfileShare).includeBackgrounds)
        assertTrue(!(kindWithoutBg as ConfigManager.ExportKind.ProfileShare).includeBackgrounds)
    }

    @Test
    fun testParseAndVerifyPlainJson() {
        val validChecksum = invokeComputeChecksum(profiles = listOf(testProfile))
        val validExport =
            MegingiardExport(
                schemaVersion = 4,
                metadata = testMetadata,
                checksum = validChecksum,
                settings = emptyMap(),
                profiles = listOf(testProfile),
            )
        val jsonStr = testJson.encodeToString(MegingiardExport.serializer(), validExport)
        val parsed = ConfigManager.parseAndVerify(jsonStr)
        assertEquals(validExport.schemaVersion, parsed.schemaVersion)
        assertEquals(validExport.checksum, parsed.checksum)
    }

    @Test
    fun `testParseAndVerifyOlderSchemaVersionJsonWithoutNewerFields`() {
        val legacyProfilesJson = """[{"id":"legacy-p1","name":"Legacy Profile","layouts":[{"id":"l1","name":"Main","buttons":[]}]}]"""
        val legacySettingsJson = "{}"
        val imageHashesJson = "{}"

        val payload = """{"settings":$legacySettingsJson,"profiles":$legacyProfilesJson,"imageHashes":$imageHashesJson}"""
        val hex = HmacUtil.sha256Hex(payload.toByteArray(Charsets.UTF_8)).lowercase()
        val expectedChecksum = "sha256:$hex"

        val legacyExportJson =
            """
            {
              "schemaVersion": 3,
              "metadata": {
                "exportedAt": "2025-01-01T00:00:00Z",
                "appVersionName": "0.5.0",
                "appVersionCode": 5
              },
              "checksum": "$expectedChecksum",
              "settings": $legacySettingsJson,
              "profiles": $legacyProfilesJson
            }
            """.trimIndent()

        val parsed = ConfigManager.parseAndVerify(legacyExportJson)
        assertEquals(3, parsed.schemaVersion)
        assertEquals(expectedChecksum, parsed.checksum)
        assertEquals(1, parsed.profiles.size)
        assertEquals("Legacy Profile", parsed.profiles.first().name)
    }

    @Test
    fun testImportSettingsTypeSafety() =
        runBlocking {
            val testDispatcher = StandardTestDispatcher()
            Dispatchers.setMain(testDispatcher)
            try {
                val testDataStore = setupTestDataStore("datastore_test")
                val importPayload =
                    mapOf(
                        "global" to
                            mapOf(
                                "accent_color" to kotlinx.serialization.json.JsonPrimitive(-6087623),
                                "overlay_at_bottom" to kotlinx.serialization.json.JsonPrimitive(false),
                            ),
                        "macropad_settings" to
                            mapOf(
                                "macropad_recent_colors" to kotlinx.serialization.json.JsonPrimitive("-1716912067,-421677056,-430776976"),
                                "macropad_ambient_dim" to kotlinx.serialization.json.JsonPrimitive(0.0),
                                "privd_deadzone_left" to kotlinx.serialization.json.JsonPrimitive(0.15),
                            ),
                    )

                SettingsManager.importGroupedSettingsAwait(importPayload)
                val firstPrefs = testDataStore.data.first()

                assertTrue(firstPrefs[KEY_ACCENT_COLOR] is Int)
                assertTrue(firstPrefs[KEY_OVERLAY_AT_BOTTOM] is Boolean)
                assertTrue(firstPrefs[KEY_MACROPAD_RECENT_COLORS] is String)
                assertTrue(firstPrefs[KEY_MACROPAD_AMBIENT_DIM] is Float)
                assertTrue(firstPrefs[KEY_PRIVD_DEADZONE_LEFT] is Float)

                assertEquals(-6087623, firstPrefs[KEY_ACCENT_COLOR])
                assertEquals(false, firstPrefs[KEY_OVERLAY_AT_BOTTOM])
                assertEquals("-1716912067,-421677056,-430776976", firstPrefs[KEY_MACROPAD_RECENT_COLORS])
                assertEquals(0.0f, firstPrefs[KEY_MACROPAD_AMBIENT_DIM])
                assertEquals(0.15f, firstPrefs[KEY_PRIVD_DEADZONE_LEFT])
            } finally {
                Dispatchers.resetMain()
            }
        }

    // ── 1. Full Backup (Without Backgrounds) ──────────────────────────────────

    @Test
    fun testFullBackupWithoutBackgroundsRoundTrip() =
        runBlocking {
            val testDispatcher = StandardTestDispatcher()
            Dispatchers.setMain(testDispatcher)
            try {
                val testDataStore = setupTestDataStore("datastore_full_nobg")
                val initialSettings =
                    mapOf(
                        "global" to
                            mapOf(
                                "accent_color" to kotlinx.serialization.json.JsonPrimitive(-16743169),
                                "overlay_at_bottom" to kotlinx.serialization.json.JsonPrimitive(true),
                            ),
                    )
                SettingsManager.importGroupedSettingsAwait(initialSettings)

                val export = ConfigManager.buildExport(metadata = testMetadata, includeBackgrounds = false)
                val jsonStr = testJson.encodeToString(MegingiardExport.serializer(), export)
                val parsed = ConfigManager.parseAndVerify(jsonStr)

                assertEquals(export.schemaVersion, parsed.schemaVersion)
                assertEquals(export.checksum, parsed.checksum)
                assertEquals(export.metadata.appVersionName, parsed.metadata.appVersionName)
                assertTrue(parsed.settings.containsKey("global"))

                SettingsManager.importGroupedSettingsAwait(parsed.settings)
                val restoredPrefs = testDataStore.data.first()
                assertEquals(-16743169, restoredPrefs[KEY_ACCENT_COLOR])
                assertEquals(true, restoredPrefs[KEY_OVERLAY_AT_BOTTOM])
            } finally {
                Dispatchers.resetMain()
            }
        }

    // ── 2. Full Backup (With Backgrounds ZIP Container) ─────────────────────────

    @Test
    fun testFullBackupWithBackgroundsRoundTrip() {
        val mockImageBytes = "fake_webp_image_bytes_full_backup".toByteArray(Charsets.UTF_8)
        val layoutId = "layout-full-bg-1"
        val bgLayout = PadLayout(id = layoutId, name = "LayoutWithBg", backgroundImagePath = "backgrounds/bg_$layoutId")
        val bgProfile = PadProfile(id = "profile-full-bg", name = "FullBackupProfile", layouts = listOf(bgLayout))
        val settingsMap = mapOf("global" to mapOf("accent_color" to kotlinx.serialization.json.JsonPrimitive(-16743169)))
        val imageHashes = mapOf("bg_$layoutId" to HmacUtil.sha256Hex(mockImageBytes).lowercase())

        val validChecksum = invokeComputeChecksum(settingsMap, listOf(bgProfile), imageHashes)
        val export =
            MegingiardExport(
                schemaVersion = SCHEMA_VERSION,
                metadata = testMetadata,
                checksum = validChecksum,
                settings = settingsMap,
                profiles = listOf(bgProfile),
            )

        val jsonStr = testJson.encodeToString(MegingiardExport.serializer(), export)
        val zipBytes = zipArchive(jsonStr, mapOf("backgrounds/bg_$layoutId" to mockImageBytes))

        assertTrue(zipBytes.size > 4)
        assertEquals(0x50.toByte(), zipBytes[0])
        assertEquals(0x4B.toByte(), zipBytes[1])

        val (extractedJson, extractedImages) = unzipArchive(zipBytes)
        assertTrue(extractedJson != null)
        assertTrue(extractedImages["backgrounds/bg_$layoutId"]!!.contentEquals(mockImageBytes))

        val parsed = ConfigManager.parseAndVerify(extractedJson!!, extractedImages)
        assertEquals(validChecksum, parsed.checksum)
        assertEquals(1, parsed.profiles.size)
        assertEquals("FullBackupProfile", parsed.profiles[0].name)
    }

    // ── 3. Profile Share (Without Backgrounds) ────────────────────────────────

    @Test
    fun testProfileShareWithoutBackgroundsRoundTrip() =
        runBlocking {
            val export =
                ConfigManager.buildProfileExport(
                    metadata = testMetadata,
                    profile = testProfile,
                    includeBackgrounds = false,
                )

            assertTrue(export.settings.isEmpty())
            assertEquals(1, export.profiles.size)
            assertEquals(testProfile.name, export.profiles[0].name)

            val jsonStr = testJson.encodeToString(MegingiardExport.serializer(), export)
            val parsed = ConfigManager.parseAndVerify(jsonStr)

            assertTrue(parsed.settings.isEmpty())
            assertEquals(export.checksum, parsed.checksum)
            assertEquals(testProfile.id, parsed.profiles[0].id)
            assertEquals(testProfile.name, parsed.profiles[0].name)
        }

    // ── 4. Profile Share (With Backgrounds ZIP Container) ─────────────────────

    @Test
    fun testProfileShareWithBackgroundsRoundTrip() {
        val mockImageBytes = "fake_webp_image_bytes_profile_share".toByteArray(Charsets.UTF_8)
        val layoutId = "layout-profile-share-bg"
        val bgLayout = PadLayout(id = layoutId, name = "SharedLayoutWithBg", backgroundImagePath = "backgrounds/bg_$layoutId")
        val bgProfile = PadProfile(id = "profile-share-bg", name = "SharedProfileWithBg", layouts = listOf(bgLayout))
        val imageHashes = mapOf("bg_$layoutId" to HmacUtil.sha256Hex(mockImageBytes).lowercase())

        val validChecksum = invokeComputeChecksum(emptyMap(), listOf(bgProfile), imageHashes)
        val export =
            MegingiardExport(
                schemaVersion = SCHEMA_VERSION,
                metadata = testMetadata,
                checksum = validChecksum,
                settings = emptyMap(),
                profiles = listOf(bgProfile),
            )

        val jsonStr = testJson.encodeToString(MegingiardExport.serializer(), export)
        val zipBytes = zipArchive(jsonStr, mapOf("backgrounds/bg_$layoutId" to mockImageBytes))

        assertTrue(zipBytes.size > 4)
        assertEquals(0x50.toByte(), zipBytes[0])

        val (extractedJson, extractedImages) = unzipArchive(zipBytes)
        assertTrue(extractedJson != null)
        assertTrue(extractedImages["backgrounds/bg_$layoutId"]!!.contentEquals(mockImageBytes))

        val parsed = ConfigManager.parseAndVerify(extractedJson!!, extractedImages)
        assertTrue(parsed.settings.isEmpty())
        assertEquals(validChecksum, parsed.checksum)
        assertEquals(1, parsed.profiles.size)
        assertEquals("SharedProfileWithBg", parsed.profiles[0].name)
    }

    @Test
    fun testInAppImportCoordinatorStateFlows() {
        val testExport =
            MegingiardExport(
                schemaVersion = 4,
                metadata = testMetadata,
                checksum = "dummy",
                settings = emptyMap(),
                profiles = listOf(testProfile),
            )

        ConfigManager.setInAppParsedImport(testExport)
        assertEquals(testExport, ConfigManager.pendingInAppParsedImport.value)

        ConfigManager.setInAppImportError("Corrupted archive")
        assertEquals("Corrupted archive", ConfigManager.inAppImportError.value)

        ConfigManager.clearInAppPendingImport()
        assertEquals(null, ConfigManager.pendingInAppParsedImport.value)
        assertEquals(null, ConfigManager.inAppImportError.value)
        assertEquals(emptyMap<String, ByteArray>(), ConfigManager.getPendingInAppImages())
    }

    // ── 5. Background Image Restoration Tests ─────────────────────────────────

    @Test
    fun testApplyImportRestoresBackgroundImagesWithExplicitMap() =
        runBlocking {
            assertBackgroundRestoration(
                isProfileImport = false,
                layoutId = "layout-bg-import-1",
                profileName = "ProfileToImport",
                imageKey = "layout-bg-import-1",
            )
        }

    @Test
    fun testApplyProfileImportRestoresBackgroundImagesWithExplicitMap() =
        runBlocking {
            assertBackgroundRestoration(
                isProfileImport = true,
                layoutId = "layout-profile-share-bg-1",
                profileName = "ProfileShareToImport",
                imageKey = "bg_layout-profile-share-bg-1",
            )
        }
}
