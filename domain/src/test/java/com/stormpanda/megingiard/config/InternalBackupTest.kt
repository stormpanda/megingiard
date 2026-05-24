package com.stormpanda.megingiard.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Unit tests verifying [InternalBackup] data class serialization/deserialization
 * and checking the sorting, duplicate-filtering, and capping logic used to manage
 * configuration backups in the DataStore.
 */
class InternalBackupTest {

    private val testMetadata = ExportMetadata(
        exportedAt = "2026-05-24T12:00:00Z",
        appVersionName = "2.0.0",
        appVersionCode = 20,
    )

    private val testExport = MegingiardExport(
        schemaVersion = SCHEMA_VERSION,
        metadata = testMetadata,
        checksum = "sha256:dummy",
    )

    @Test
    fun `InternalBackup model supports serialization and deserialization`() {
        val backup = InternalBackup(
            dateString = "2026-05-24",
            timestampMs = 1716584400000L,
            export = testExport
        )

        val json = Json.encodeToString(backup)
        val decoded = Json.decodeFromString<InternalBackup>(json)

        assertEquals(backup.dateString, decoded.dateString)
        assertEquals(backup.timestampMs, decoded.timestampMs)
        assertEquals(backup.export.schemaVersion, decoded.export.schemaVersion)
        assertEquals(backup.export.checksum, decoded.export.checksum)
    }

    @Test
    fun `Backups list keeps last 5 days of usage and is sorted descending`() {
        val newBackup = InternalBackup(
            dateString = "2026-05-24",
            timestampMs = 105L,
            export = testExport
        )

        val existingList = listOf(
            InternalBackup("2026-05-23", 104L, testExport),
            InternalBackup("2026-05-22", 103L, testExport),
            InternalBackup("2026-05-21", 102L, testExport),
            InternalBackup("2026-05-20", 101L, testExport),
            InternalBackup("2026-05-19", 100L, testExport)
        )

        val result = (existingList.filter { it.dateString != newBackup.dateString } + newBackup)
            .sortedByDescending { it.timestampMs }
            .take(5)

        assertEquals(5, result.size)
        // 2026-05-24 should be the first (most recent)
        assertEquals("2026-05-24", result[0].dateString)
        assertEquals("2026-05-23", result[1].dateString)
        assertEquals("2026-05-22", result[2].dateString)
        assertEquals("2026-05-21", result[3].dateString)
        assertEquals("2026-05-20", result[4].dateString)
    }

    @Test
    fun `Backups list overwrites backup for the same day`() {
        val duplicateBackup = InternalBackup(
            dateString = "2026-05-23",
            timestampMs = 110L, // Newer timestamp for the same day
            export = testExport
        )

        val existingList = listOf(
            InternalBackup("2026-05-23", 104L, testExport),
            InternalBackup("2026-05-22", 103L, testExport)
        )

        val result = (existingList.filter { it.dateString != duplicateBackup.dateString } + duplicateBackup)
            .sortedByDescending { it.timestampMs }
            .take(5)

        assertEquals(2, result.size)
        assertEquals("2026-05-23", result[0].dateString)
        assertEquals(110L, result[0].timestampMs) // Timestamp is updated
        assertEquals("2026-05-22", result[1].dateString)
    }
}
