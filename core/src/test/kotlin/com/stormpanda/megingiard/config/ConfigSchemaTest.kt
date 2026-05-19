package com.stormpanda.megingiard.config

import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [MegingiardExport] schema shape and JSON round-trip correctness.
 *
 * These tests validate the static contracts of the config schema (schema version,
 * profile-share export shape, serialization fidelity) without any Android
 * dependencies or coroutines.
 */
class ConfigSchemaTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val testMetadata = ExportMetadata(
        exportedAt = "2025-01-01T00:00:00Z",
        appVersionName = "1.0.0",
        appVersionCode = 1,
        author = "TestAuthor",
    )

    private val testProfile = PadProfile(
        id = "profile-uuid-1",
        name = "TestProfile",
    )

    // ── Schema version ────────────────────────────────────────────────────────

    @Test
    fun `SCHEMA_VERSION is 4`() {
        assertEquals(4, SCHEMA_VERSION)
    }

    // ── Profile-share export shape ────────────────────────────────────────────

    @Test
    fun `profile-share export has empty settings`() {
        val export = MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = testMetadata,
            checksum = "placeholder",
            settings = emptyMap(),
            profiles = listOf(testProfile),
        )
        assertTrue(
            "Profile-share export must have empty settings",
            export.settings.isEmpty(),
        )
    }

    @Test
    fun `profile-share export contains exactly the shared profile`() {
        val export = MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = testMetadata,
            checksum = "placeholder",
            settings = emptyMap(),
            profiles = listOf(testProfile),
        )
        assertEquals(1, export.profiles.size)
        assertEquals(testProfile.id, export.profiles.first().id)
        assertEquals(testProfile.name, export.profiles.first().name)
    }

    // ── JSON round-trip ───────────────────────────────────────────────────────

    @Test
    fun `profile-share export survives JSON round-trip`() {
        val original = MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = testMetadata,
            checksum = "abc123",
            settings = emptyMap(),
            profiles = listOf(testProfile),
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<MegingiardExport>(encoded)

        assertEquals(original.schemaVersion, decoded.schemaVersion)
        assertEquals(original.checksum, decoded.checksum)
        assertTrue(decoded.settings.isEmpty())
        assertEquals(1, decoded.profiles.size)
        assertEquals(testProfile.id, decoded.profiles.first().id)
        assertEquals(testProfile.name, decoded.profiles.first().name)
    }

    @Test
    fun `full backup export round-trip preserves settings and profiles`() {
        val settingsSection = mapOf(
            "global" to mapOf("accent_color" to kotlinx.serialization.json.JsonPrimitive(0xFF2196F3.toInt())),
        )
        val original = MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = testMetadata,
            checksum = "def456",
            settings = settingsSection,
            profiles = listOf(testProfile),
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<MegingiardExport>(encoded)

        assertEquals(1, decoded.settings.size)
        assertTrue(decoded.settings.containsKey("global"))
        assertEquals(1, decoded.profiles.size)
    }

    @Test
    fun `ExportMetadata optional fields default to null`() {
        val meta = ExportMetadata(
            exportedAt = "2025-01-01T00:00:00Z",
            appVersionName = "1.0",
            appVersionCode = 1,
        )
        assertEquals(null, meta.author)
        assertEquals(null, meta.description)
        assertTrue(meta.tags.isEmpty())
    }
}
