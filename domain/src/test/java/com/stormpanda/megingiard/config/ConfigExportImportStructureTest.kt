package com.stormpanda.megingiard.config

import com.stormpanda.megingiard.macropad.PadProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private val testMetadata = com.stormpanda.megingiard.config.ExportMetadata(
        exportedAt = "2025-01-01T00:00:00Z",
        appVersionName = "1.0.0",
        appVersionCode = 1,
    )

    private val testProfile = PadProfile(
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
}
