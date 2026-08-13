package com.stormpanda.megingiard.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.stormpanda.megingiard.macropad.PadProfile
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests for the [ConfigManager.ExportKind] sealed interface and
 * [ConfigManager.ImportMode] enum introduced with the per-profile
 * export/import feature.
 *
 * These tests are purely structural — they verify that the discriminator
 * types have the correct shape and carry the expected data. No Android APIs
 * or coroutines are involved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigExportImportStructureTest {
    private val testJson = Json { encodeDefaults = true }
    private val testMetadata =
        com.stormpanda.megingiard.config.ExportMetadata(
            exportedAt = "2025-01-01T00:00:00Z",
            appVersionName = "1.0.0",
            appVersionCode = 1,
        )

    private val testProfile =
        PadProfile(
            id = "profile-uuid-test",
            name = "SharedProfile",
        )

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
        val export =
            MegingiardExport(
                schemaVersion = 4,
                metadata = testMetadata,
                checksum = "placeholder",
                settings = emptyMap(),
                profiles = listOf(testProfile),
            )
        // Compute valid checksum
        val checksumMethod =
            ConfigManager::class.java.getDeclaredMethod(
                "computeChecksum",
                Map::class.java,
                List::class.java,
                Map::class.java,
            )
        checksumMethod.isAccessible = true
        val validChecksum =
            checksumMethod.invoke(
                ConfigManager,
                emptyMap<String, Map<String, Any>>(),
                listOf(testProfile),
                emptyMap<String, String>(),
            ) as String

        val validExport = export.copy(checksum = validChecksum)
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
        val hex =
            com.stormpanda.megingiard.security.HmacUtil
                .sha256Hex(payload.toByteArray(Charsets.UTF_8))
                .lowercase()
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
                val tempFile = File.createTempFile("datastore_test", ".preferences_pb")
                tempFile.deleteOnExit()
                val testDataStore =
                    PreferenceDataStoreFactory.create(
                        produceFile = { tempFile },
                    )

                val smStore = SettingsManager::class.java.getDeclaredField("dataStore")
                smStore.isAccessible = true
                smStore.set(SettingsManager, testDataStore)

                val smInit = SettingsManager::class.java.getDeclaredField("initialized")
                smInit.isAccessible = true
                smInit.set(SettingsManager, true)

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
                val tempFile = File.createTempFile("datastore_full_nobg", ".preferences_pb")
                tempFile.deleteOnExit()
                val testDataStore =
                    PreferenceDataStoreFactory.create(
                        produceFile = { tempFile },
                    )

                val smStore = SettingsManager::class.java.getDeclaredField("dataStore")
                smStore.isAccessible = true
                smStore.set(SettingsManager, testDataStore)

                val smInit = SettingsManager::class.java.getDeclaredField("initialized")
                smInit.isAccessible = true
                smInit.set(SettingsManager, true)

                // Set initial settings
                val initialSettings =
                    mapOf(
                        "global" to
                            mapOf(
                                "accent_color" to kotlinx.serialization.json.JsonPrimitive(-16743169),
                                "overlay_at_bottom" to kotlinx.serialization.json.JsonPrimitive(true),
                            ),
                    )
                SettingsManager.importGroupedSettingsAwait(initialSettings)

                // Build full export snapshot without backgrounds
                val export = ConfigManager.buildExport(metadata = testMetadata, includeBackgrounds = false)

                // Serialize to plain JSON string & parse back
                val jsonStr = testJson.encodeToString(MegingiardExport.serializer(), export)
                val parsed = ConfigManager.parseAndVerify(jsonStr)

                // Structural assertions
                assertEquals(export.schemaVersion, parsed.schemaVersion)
                assertEquals(export.checksum, parsed.checksum)
                assertEquals(export.metadata.appVersionName, parsed.metadata.appVersionName)
                assertTrue(parsed.settings.containsKey("global"))

                // Re-hydrate settings into DataStore
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

        val bgLayout =
            com.stormpanda.megingiard.macropad.PadLayout(
                id = layoutId,
                name = "LayoutWithBg",
                backgroundImagePath = "backgrounds/bg_$layoutId",
            )
        val bgProfile =
            PadProfile(
                id = "profile-full-bg",
                name = "FullBackupProfile",
                layouts = listOf(bgLayout),
            )

        val settingsMap = mapOf("global" to mapOf("accent_color" to kotlinx.serialization.json.JsonPrimitive(-16743169)))

        val imageHash =
            com.stormpanda.megingiard.security.HmacUtil
                .sha256Hex(mockImageBytes)
                .lowercase()
        val imageHashes = mapOf("bg_$layoutId" to imageHash)

        // Compute valid checksum
        val checksumMethod =
            ConfigManager::class.java.getDeclaredMethod(
                "computeChecksum",
                Map::class.java,
                List::class.java,
                Map::class.java,
            )
        checksumMethod.isAccessible = true
        val validChecksum =
            checksumMethod.invoke(
                ConfigManager,
                settingsMap,
                listOf(bgProfile),
                imageHashes,
            ) as String

        val export =
            MegingiardExport(
                schemaVersion = SCHEMA_VERSION,
                metadata = testMetadata,
                checksum = validChecksum,
                settings = settingsMap,
                profiles = listOf(bgProfile),
            )

        val jsonStr = testJson.encodeToString(MegingiardExport.serializer(), export)

        // Package into ZIP container stream
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("config.json"))
            zos.write(jsonStr.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("backgrounds/bg_$layoutId"))
            zos.write(mockImageBytes)
            zos.closeEntry()
        }

        val zipBytes = baos.toByteArray()
        assertTrue(zipBytes.size > 4)
        assertEquals(0x50.toByte(), zipBytes[0]) // 'P'
        assertEquals(0x4B.toByte(), zipBytes[1]) // 'K'

        // Parse ZIP container back
        var extractedJson: String? = null
        val extractedImages = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zis ->
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

        assertTrue(extractedJson != null)
        assertTrue(extractedImages.containsKey("backgrounds/bg_$layoutId"))
        assertTrue(extractedImages["backgrounds/bg_$layoutId"]!!.contentEquals(mockImageBytes))

        // Verify parsed config & checksum
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

            // Settings MUST be empty for profile-share exports
            assertTrue(export.settings.isEmpty())
            assertEquals(1, export.profiles.size)
            assertEquals(testProfile.name, export.profiles[0].name)

            // Serialize to plain JSON string & parse back
            val jsonStr = testJson.encodeToString(MegingiardExport.serializer(), export)
            val parsed = ConfigManager.parseAndVerify(jsonStr)

            // Assert settings map remains empty and profile is intact
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

        val bgLayout =
            com.stormpanda.megingiard.macropad.PadLayout(
                id = layoutId,
                name = "SharedLayoutWithBg",
                backgroundImagePath = "backgrounds/bg_$layoutId",
            )
        val bgProfile =
            PadProfile(
                id = "profile-share-bg",
                name = "SharedProfileWithBg",
                layouts = listOf(bgLayout),
            )

        val imageHash =
            com.stormpanda.megingiard.security.HmacUtil
                .sha256Hex(mockImageBytes)
                .lowercase()
        val imageHashes = mapOf("bg_$layoutId" to imageHash)

        val checksumMethod =
            ConfigManager::class.java.getDeclaredMethod(
                "computeChecksum",
                Map::class.java,
                List::class.java,
                Map::class.java,
            )
        checksumMethod.isAccessible = true
        val validChecksum =
            checksumMethod.invoke(
                ConfigManager,
                emptyMap<String, Map<String, Any>>(),
                listOf(bgProfile),
                imageHashes,
            ) as String

        val export =
            MegingiardExport(
                schemaVersion = SCHEMA_VERSION,
                metadata = testMetadata,
                checksum = validChecksum,
                settings = emptyMap(),
                profiles = listOf(bgProfile),
            )

        val jsonStr = testJson.encodeToString(MegingiardExport.serializer(), export)

        // Package into ZIP container stream
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("config.json"))
            zos.write(jsonStr.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(java.util.zip.ZipEntry("backgrounds/bg_$layoutId"))
            zos.write(mockImageBytes)
            zos.closeEntry()
        }

        val zipBytes = baos.toByteArray()
        assertTrue(zipBytes.size > 4)
        assertEquals(0x50.toByte(), zipBytes[0])

        // Parse ZIP container back
        var extractedJson: String? = null
        val extractedImages = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zis ->
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

        assertTrue(extractedJson != null)
        assertTrue(extractedImages.containsKey("backgrounds/bg_$layoutId"))
        assertTrue(extractedImages["backgrounds/bg_$layoutId"]!!.contentEquals(mockImageBytes))

        // Verify parsed profile-share config & checksum
        val parsed = ConfigManager.parseAndVerify(extractedJson!!, extractedImages)
        assertTrue(parsed.settings.isEmpty())
        assertEquals(validChecksum, parsed.checksum)
        assertEquals(1, parsed.profiles.size)
        assertEquals("SharedProfileWithBg", parsed.profiles[0].name)
    }
}
