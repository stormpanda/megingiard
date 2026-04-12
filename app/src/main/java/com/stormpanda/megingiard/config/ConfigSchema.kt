package com.stormpanda.megingiard.config

import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroFolder
import com.stormpanda.megingiard.macropad.PadProfile
import kotlinx.serialization.Serializable

// ─────────────────────────────────────────────────────────────────────────────
// Schema version & MIME type
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Current schema version (SemVer).
 * - Increment PATCH for non-breaking additions (new optional fields).
 * - Increment MINOR for backward-compatible structural additions.
 * - Increment MAJOR for breaking changes (remove or rename existing fields).
 */
internal const val SCHEMA_VERSION = "1.0.0"

/** MIME type registered in AndroidManifest for `.mgrd` config files. */
const val MGRD_MIME_TYPE = "application/vnd.megingiard.config+json"

// ─────────────────────────────────────────────────────────────────────────────
// ExportType — declares the intent of the export
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Declares which sections were populated in a [MegingiardExport].
 * Allows partial imports without a full app-configuration snapshot.
 */
@Serializable
enum class ExportType {
    /** All sections present — can be applied as a complete App Profile. */
    FULL,

    /**
     * Only [ExportSections.macropad] profiles (+ any macros they reference) are present.
     * Used for sharing pad layouts with the community.
     */
    MACROPAD_PROFILES,

    /**
     * Only [ExportSections.macropad] macros and macroFolders are present.
     * Used for sharing a macro library.
     */
    MACROS,

    /**
     * One or more tool-specific setting sections are present.
     * [ExportSections.global] may or may not be populated.
     */
    TOOL_SETTINGS,
}

// ─────────────────────────────────────────────────────────────────────────────
// Root wrapper
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root container for a Megingiard configuration export file (`.mgrd`).
 *
 * @param schemaVersion SemVer string used for forward-compatible migration on import.
 * @param type          Which sections were populated (see [ExportType]).
 * @param metadata      Community-facing info (author, description, tags).
 * @param checksum      `"sha256:<lowercase-hex>"` computed over the canonical JSON
 *                      of [sections] via [ChecksumUtil]. Verified on import.
 * @param sections      The actual configuration payload; each sub-section is
 *                      independently nullable — absent sections are not applied.
 */
@Serializable
data class MegingiardExport(
    val schemaVersion: String,
    val type: ExportType,
    val metadata: ExportMetadata,
    val checksum: String,
    val sections: ExportSections,
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

    /** Free-form tag list for community discovery (e.g. `["elden-ring", "souls"]`). */
    val tags: List<String> = emptyList(),

    /** ISO-8601 timestamp at UTC, e.g. `"2026-04-12T14:30:00Z"`. */
    val exportedAt: String,

    val appVersionName: String,
    val appVersionCode: Int,

    /** `"${Build.MANUFACTURER} ${Build.MODEL}"`, or null if unavailable. */
    val deviceModel: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Sections — all nullable for partial import support
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Payload sections — each is independently nullable.
 * On import, only non-null sections are applied; absent sections are left untouched.
 */
@Serializable
data class ExportSections(
    val global: GlobalSettingsSection? = null,
    val mirror: MirrorSettingsSection? = null,
    val touchpad: TouchpadSettingsSection? = null,
    val keyboard: KeyboardSettingsSection? = null,
    val macropad: MacroPadSection? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// Per-section data classes
// ─────────────────────────────────────────────────────────────────────────────

/** App-wide settings independent of any specific tool. */
@Serializable
data class GlobalSettingsSection(
    /** Serialized [com.stormpanda.megingiard.AppMode] names of enabled tools. */
    val enabledTools: List<String>,
    /** Serialized [com.stormpanda.megingiard.AppMode] names in carousel display order. */
    val toolOrder: List<String>,
    val overlayTimeoutMs: Long,
    val overlayAtBottom: Boolean,
    /** Packed ARGB color as `"#AARRGGBB"` uppercase hex. */
    val accentColor: String,
    /** [com.stormpanda.megingiard.ui.ThemeMode] name. */
    val themeMode: String,
    /** [com.stormpanda.megingiard.settings.AppLanguage] name. */
    val appLanguage: String,
    /** [com.stormpanda.megingiard.AppLog.Level] name. */
    val logLevel: String,
    val rememberLastTool: Boolean,
)

/** Mirror tool settings. */
@Serializable
data class MirrorSettingsSection(
    val autoStartCapture: Boolean,
    val rememberViewport: Boolean,
    val rememberLock: Boolean,
    val rememberProjection: Boolean,
    val pinchWhileProjecting: Boolean,
    /** Saved viewport scale; null when [rememberViewport] is false. */
    val savedScale: Float? = null,
    val savedOffsetX: Float? = null,
    val savedOffsetY: Float? = null,
    val savedLocked: Boolean? = null,
    val savedProjection: Boolean? = null,
)

/** Touchpad tool settings. */
@Serializable
data class TouchpadSettingsSection(
    val useMouse: Boolean,
    val tapToClick: Boolean,
    val twoFingerTap: Boolean,
)

/** Keyboard tool settings. */
@Serializable
data class KeyboardSettingsSection(
    /** [com.stormpanda.megingiard.keyboard.KbLayout] name. */
    val layout: String,
    val trackpointEnabled: Boolean,
    /** [com.stormpanda.megingiard.keyboard.KbMouseBtnPos] name. */
    val mouseBtnPos: String,
    val repeatEnabled: Boolean,
    val fullscreen: Boolean,
)

/** MacroPad tool — settings, pad profiles, and the global macro library. */
@Serializable
data class MacroPadSection(
    val settings: MacroPadSettingsSection,
    /** UUID of the profile that should be active after import. May not match after UUID remapping. */
    val activeProfileId: String? = null,
    /** All pad profiles in this export. Reuses the existing [PadProfile] serialized format. */
    val profiles: List<PadProfile> = emptyList(),
    /** All macros in this export. Reuses the existing [Macro] serialized format. */
    val macros: List<Macro> = emptyList(),
    val macroFolders: List<MacroFolder> = emptyList(),
)

/** MacroPad ambient display and vignette settings. */
@Serializable
data class MacroPadSettingsSection(
    val ambientEnabled: Boolean,
    val ambientDim: Float,
    val ambientPreview: Boolean,
    val ambientApplyTheme: Boolean,
    val vignetteEnabled: Boolean,
    /** [com.stormpanda.megingiard.settings.VignetteShape] name. */
    val vignetteShape: String,
    val vignetteVisibleArea: Float,
    val vignetteTransition: Float,
    val vignetteOpacity: Float,
    /** Packed ARGB color as `"#AARRGGBB"` uppercase hex. */
    val vignetteColor: String,
)
