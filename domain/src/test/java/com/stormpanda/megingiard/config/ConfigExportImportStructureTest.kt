package com.stormpanda.megingiard.config

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.settings.MacroPadSettings
import com.stormpanda.megingiard.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
class ConfigExportImportStructureTest {
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

                assertTrue(firstPrefs[com.stormpanda.megingiard.settings.KEY_ACCENT_COLOR] is Int)
                assertTrue(firstPrefs[com.stormpanda.megingiard.settings.KEY_OVERLAY_AT_BOTTOM] is Boolean)
                assertTrue(firstPrefs[com.stormpanda.megingiard.settings.KEY_MACROPAD_RECENT_COLORS] is String)
                assertTrue(firstPrefs[com.stormpanda.megingiard.settings.KEY_MACROPAD_AMBIENT_DIM] is Float)
                assertTrue(firstPrefs[com.stormpanda.megingiard.settings.KEY_PRIVD_DEADZONE_LEFT] is Float)

                assertEquals(-6087623, firstPrefs[com.stormpanda.megingiard.settings.KEY_ACCENT_COLOR])
                assertEquals(false, firstPrefs[com.stormpanda.megingiard.settings.KEY_OVERLAY_AT_BOTTOM])
                assertEquals("-1716912067,-421677056,-430776976", firstPrefs[com.stormpanda.megingiard.settings.KEY_MACROPAD_RECENT_COLORS])
                assertEquals(0.0f, firstPrefs[com.stormpanda.megingiard.settings.KEY_MACROPAD_AMBIENT_DIM])
                assertEquals(0.15f, firstPrefs[com.stormpanda.megingiard.settings.KEY_PRIVD_DEADZONE_LEFT])
            } finally {
                Dispatchers.resetMain()
            }
        }
}
