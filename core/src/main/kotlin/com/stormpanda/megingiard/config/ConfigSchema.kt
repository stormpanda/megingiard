package com.stormpanda.megingiard.config

import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroFolder
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ─────────────────────────────────────────────────────────────────────────────
// Schema version & MIME type
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Schema version — integer, incremented on breaking changes.
 * v1: per-section typed data classes (removed in v2).
 * v2: flat DataStore key/value map grouped by section name, separate macros/macroFolders.
 * v3: macros embedded inside PadProfile.macros; macroFolders removed.
 *     Legacy v2 fields are kept for parsing old exports (migration on import).
 */
const val SCHEMA_VERSION = 3

/** MIME type registered in AndroidManifest for `.mgrd` config files. */
const val MGRD_MIME_TYPE = "application/vnd.megingiard.config+json"

// ─────────────────────────────────────────────────────────────────────────────
// Root wrapper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root container for a Megingiard configuration export file (`.mgrd`).
 *
 * Settings are stored as a grouped key/value map (`section → keyName → value`)
 * where keys match the DataStore preference key names. This means adding a new
 * setting requires zero changes to the config package — only SettingsManager.
 *
 * MacroPad profiles carry their own macros (`PadProfile.macros`).
 * The top-level [macros] and [macroFolders] fields are v2 legacy — only present
 * in older exports. On import, [ConfigManager] migrates them into profiles.
 */
@Serializable
data class MegingiardExport(
    val schemaVersion: Int,
    val metadata: ExportMetadata,
    val checksum: String,
    /** Settings grouped by section: `{"global": {"accent_color": 123, ...}, "mirror": {...}}` */
    val settings: Map<String, Map<String, JsonElement>> = emptyMap(),
    val profiles: List<PadProfile> = emptyList(),
    /** @deprecated v2 legacy — macros now live inside PadProfile.macros. Present only in v2 imports. */
    val macros: List<Macro> = emptyList(),
    /** @deprecated v2 legacy — folders removed. Present only in v2 imports. */
    val macroFolders: List<MacroFolder> = emptyList(),
)

// ─────────────────────────────────────────────────────────────────────────────
// Metadata
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Community-facing metadata attached to every export file.
 * All fields except [exportedAt] and app version info are optional.
 */
@Serializable
data class ExportMetadata(
    /** Free-text author name or handle. Optional. */
    val author: String? = null,

    /** Short human description of what this config is for. Optional. */
    val description: String? = null,

    /** Free-form tag list for community discovery (e.g. `[\"elden-ring\", \"souls\"]`). */
    val tags: List<String> = emptyList(),

    /** ISO-8601 timestamp at UTC, e.g. `\"2026-04-12T14:30:00Z\"`. */
    val exportedAt: String,

    val appVersionName: String,
    val appVersionCode: Int,

    /** `\"\${Build.MANUFACTURER} \${Build.MODEL}\"`, or null if unavailable. */
    val deviceModel: String? = null,
)
