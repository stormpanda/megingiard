package com.stormpanda.megingiard.config

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.config.LegacyMacroFolder
import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.settings.SettingsManager
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val TAG = "ConfigManager"

/** Safety cap — reject files larger than 10 MB to prevent OOM. */
private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

/** Oldest schema we support importing. */
private const val MIN_SUPPORTED_SCHEMA = 2

private val exportJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private val importJson = Json { ignoreUnknownKeys = true }

/** Deterministic codec for checksum computation — no pretty-print, defaults encoded. */
private val checksumJson = Json {
    prettyPrint = false
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * Unified config export/import manager.
 *
 * Schema v3: macros embedded inside PadProfile.macros; macroFolders removed.
 * v2 imports are migrated: separate macros list is merged into each profile
 * that references them, folders are discarded.
 */
object ConfigManager {

    // ── Coordinator StateFlows (export) ──────────────────────────────────────

    /** Non-null while a "save file" picker should be opened by MainActivity. */
    private val _exportRequest = MutableStateFlow<ExportMetadata?>(null)
    val exportRequest: StateFlow<ExportMetadata?> = _exportRequest.asStateFlow()

    /** Suggested default filename for the "create document" picker. */
    private val _exportFilename = MutableStateFlow("")
    val exportFilename: StateFlow<String> = _exportFilename.asStateFlow()

    fun requestExport(metadata: ExportMetadata, filename: String) {
        AppLog.d(TAG, "requestExport filename=$filename")
        _exportFilename.value = filename
        _exportRequest.value = metadata
    }

    fun clearExportRequest() {
        AppLog.d(TAG, "clearExportRequest")
        _exportRequest.value = null
    }

    // ── Coordinator StateFlows (import from Settings) ────────────────────────

    /** True while an "open file" picker should be opened by MainActivity. */
    private val _importRequested = MutableStateFlow(false)
    val importRequested: StateFlow<Boolean> = _importRequested.asStateFlow()

    fun requestImport() {
        AppLog.d(TAG, "requestImport")
        _importRequested.value = true
    }

    fun clearImportRequest() {
        AppLog.d(TAG, "clearImportRequest")
        _importRequested.value = false
    }

    // ── Coordinator StateFlows (import from external ACTION_VIEW intent) ────────

    /** Non-null while MainAppScreen should parse an externally-opened .mgrd URI. */
    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

    /** Non-null while MainAppScreen should show IncomingImportDialog for an external import. */
    private val _pendingParsedImport = MutableStateFlow<MegingiardExport?>(null)
    val pendingParsedImport: StateFlow<MegingiardExport?> = _pendingParsedImport.asStateFlow()

    /** Called by MainActivity when an ACTION_VIEW intent with a .mgrd URI is received. */
    fun setPendingUri(uri: Uri) {
        AppLog.i(TAG, "setPendingUri: $uri")
        _pendingUri.value = uri
    }

    /** Called by MainAppScreen after successfully parsing an external import URI. */
    fun setParsedImport(export: MegingiardExport) {
        AppLog.d(TAG, "setParsedImport")
        _pendingParsedImport.value = export
        _pendingUri.value = null
    }

    /** Clears external import state — call after confirm or dismiss of IncomingImportDialog. */
    fun clearPendingImport() {
        AppLog.d(TAG, "clearPendingImport")
        _pendingUri.value = null
        _pendingParsedImport.value = null
    }

    // ── Coordinator StateFlows (import from in-app Settings picker) ───────────

    /** Non-null while MainAppScreen should parse an in-app Settings .mgrd URI. */
    private val _pendingInAppUri = MutableStateFlow<Uri?>(null)
    val pendingInAppUri: StateFlow<Uri?> = _pendingInAppUri.asStateFlow()

    /** Non-null while GlobalSettingsScreen should show ImportPreviewDialog. */
    private val _pendingInAppParsedImport = MutableStateFlow<MegingiardExport?>(null)
    val pendingInAppParsedImport: StateFlow<MegingiardExport?> = _pendingInAppParsedImport.asStateFlow()

    /** Called by MainActivity when the in-app file picker returns a .mgrd URI. */
    fun setPendingInAppUri(uri: Uri) {
        AppLog.i(TAG, "setPendingInAppUri: $uri")
        _pendingInAppUri.value = uri
    }

    /** Called by MainAppScreen after successfully parsing an in-app import URI. */
    fun setInAppParsedImport(export: MegingiardExport) {
        AppLog.d(TAG, "setInAppParsedImport")
        _pendingInAppParsedImport.value = export
        _pendingInAppUri.value = null
    }

    /** Clears in-app import state — call after confirm or dismiss of ImportPreviewDialog. */
    fun clearInAppPendingImport() {
        AppLog.d(TAG, "clearInAppPendingImport")
        _pendingInAppUri.value = null
        _pendingInAppParsedImport.value = null
    }

    // ── Export result feedback ────────────────────────────────────────────────

    private val _exportResult = MutableStateFlow<ExportResult?>(null)
    val exportResult: StateFlow<ExportResult?> = _exportResult.asStateFlow()

    fun setExportResult(result: ExportResult) {
        AppLog.d(TAG, "setExportResult $result")
        _exportResult.value = result
    }

    fun clearExportResult() {
        _exportResult.value = null
    }

    sealed interface ExportResult {
        data object Success : ExportResult
        data class Failure(val message: String?) : ExportResult
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Builds a v3 export from the current app state.
     * Macros are embedded inside each PadProfile — no top-level macros or folders.
     */
    suspend fun buildExport(metadata: ExportMetadata): MegingiardExport {
        AppLog.i(TAG, "buildExport: author=${metadata.author}")
        val settings = SettingsManager.exportGroupedSettings()
        val profiles = MacroPadState.profiles.value

        val checksum = computeChecksum(settings, profiles)
        AppLog.d(TAG, "buildExport: checksum=$checksum profiles=${profiles.size}")

        return MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = metadata,
            checksum = checksum,
            settings = settings,
            profiles = profiles,
        )
    }

    /** Creates pre-filled [ExportMetadata] with the app version and device info. */
    fun defaultMetadata(context: Context): ExportMetadata {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        }.getOrNull()
        return ExportMetadata(
            exportedAt = Instant.now().toString(),
            appVersionName = packageInfo?.versionName ?: "unknown",
            appVersionCode = packageInfo?.longVersionCode?.toInt() ?: 0,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    /** Serializes [export] as JSON and writes it to [uri] via SAF. */
    fun writeToUri(context: Context, uri: Uri, export: MegingiardExport) {
        AppLog.i(TAG, "writeToUri: uri=$uri")
        val json = exportJson.encodeToString(export)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open output stream for URI: $uri")
    }

    // ── Import ───────────────────────────────────────────────────────────────

    /**
     * Reads a `.mgrd` file from [uri], verifies checksum and schema version.
     */
    fun readFromUri(context: Context, uri: Uri): Result<MegingiardExport> = runCatching {
        AppLog.i(TAG, "readFromUri: uri=$uri")
        // Reject oversized files before allocating: check declared size if available.
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.SIZE), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val declared = cursor.getLong(0)
                check(declared <= MAX_FILE_SIZE_BYTES) {
                    "File too large: $declared bytes (max $MAX_FILE_SIZE_BYTES)"
                }
            }
        }
        val json = context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            check(bytes.size <= MAX_FILE_SIZE_BYTES) {
                "File too large: ${bytes.size} bytes (max $MAX_FILE_SIZE_BYTES)"
            }
            bytes.toString(Charsets.UTF_8)
        } ?: error("Could not open input stream for URI: $uri")

        parseAndVerify(json)
    }

    /**
     * Parses a JSON string, validates the schema version, verifies checksum.
     * Accepts v2 and v3 formats; v2 is migrated to v3 in [applyImport].
     */
    internal fun parseAndVerify(json: String): MegingiardExport {
        val export = importJson.decodeFromString<MegingiardExport>(json)
        if (export.schemaVersion < MIN_SUPPORTED_SCHEMA || export.schemaVersion > SCHEMA_VERSION) {
            error("Unsupported schema version ${export.schemaVersion} — expected $MIN_SUPPORTED_SCHEMA..$SCHEMA_VERSION")
        }
        if (!verifyChecksum(export)) {
            error("Checksum mismatch — the file may be corrupted or tampered")
        }
        AppLog.i(TAG, "parseAndVerify: OK schema=${export.schemaVersion}")
        return export
    }

    /**
     * Applies all settings and macropad data from [export] to the running app state.
     * Settings are awaited so callers know the DataStore write completed before showing success.
     * MacroPad profiles get new UUIDs and "(Imported)" suffix.
     * v2 imports: separate macros/folders are migrated into profiles.
     */
    suspend fun applyImport(export: MegingiardExport) {
        AppLog.i(TAG, "applyImport: schema=${export.schemaVersion}")
        if (export.settings.isNotEmpty()) {
            SettingsManager.importGroupedSettingsAwait(export.settings)
        }
        if (export.profiles.isNotEmpty() || export.macros.isNotEmpty()) {
            importMacroPadData(export.profiles, export.macros)
        }
    }

    // ── MacroPad import with UUID remapping ─────────────────────────────────

    /**
     * Imports profiles with new UUIDs. For v2 imports, merges separate [legacyMacros]
     * into each profile that references them via PadAction.Macro.
     */
    private fun importMacroPadData(
        profiles: List<PadProfile>,
        legacyMacros: List<Macro>,
    ) {
        AppLog.d(TAG, "importMacroPadData: ${profiles.size} profiles, ${legacyMacros.size} legacyMacros")

        // Build a lookup for legacy macros (v2 imports only)
        val legacyMacroMap = legacyMacros.associateBy { it.id }

        for (profile in profiles) {
            val newProfileId = UUID.randomUUID().toString()

            // Build macro ID remap: old → new for all macros in this profile
            val macroIdMap = mutableMapOf<String, String>()

            // Macros already inside the profile (v3 format)
            val existingMacros = profile.macros.map { macro ->
                val newId = UUID.randomUUID().toString()
                macroIdMap[macro.id] = newId
                macro.copy(id = newId, name = importedName(macro.name))
            }

            // Collect referenced legacy macros not already in the profile (v2 migration).
            // Include both layout buttons and legacy top-level buttons so that v2
            // imports (where buttons haven't been migrated to layouts yet) are covered.
            val referencedIds = (profile.layouts.flatMap { it.buttons } + profile.buttons)
                .mapNotNull { (it.action as? PadAction.Macro)?.macroId }
                .toSet()
            val adoptedLegacy = referencedIds
                .filter { id -> id !in macroIdMap } // not already remapped
                .mapNotNull { legacyMacroMap[it] }
                .map { macro ->
                    val newId = UUID.randomUUID().toString()
                    macroIdMap[macro.id] = newId
                    macro.copy(id = newId, name = importedName(macro.name))
                }

            val allMacros = existingMacros + adoptedLegacy

            // Remap macro references in button actions
            val remappedLayouts = profile.layouts.map { layout ->
                layout.copy(
                    id = UUID.randomUUID().toString(),
                    buttons = layout.buttons.map { button ->
                        button.copy(
                            id = UUID.randomUUID().toString(),
                            action = remapMacroAction(button.action, macroIdMap),
                        )
                    }
                )
            }

            // Also remap legacy buttons field (pre-layout-migration profiles)
            val remappedButtons = profile.buttons.map { button ->
                button.copy(
                    id = UUID.randomUUID().toString(),
                    action = remapMacroAction(button.action, macroIdMap),
                )
            }

            // Preserve the source profile's active layout selection when possible.
            // Build a mapping from old layout IDs to new ones so we can look up the
            // remapped ID for the imported profile's activeLayoutId.
            val layoutIdMap = profile.layouts.zip(remappedLayouts).associate { (old, new) -> old.id to new.id }
            val preservedActiveLayoutId = layoutIdMap[profile.activeLayoutId]
                ?: remappedLayouts.firstOrNull()?.id
                ?: profile.activeLayoutId

            val importedProfile = profile.copy(
                id = newProfileId,
                name = importedName(profile.name),
                layouts = remappedLayouts,
                buttons = remappedButtons,
                macros = allMacros,
                activeLayoutId = preservedActiveLayoutId,
            )
            MacroPadState.addProfile(importedProfile)
            AppLog.d(TAG, "imported profile '${profile.name}' → $newProfileId (${allMacros.size} macros)")
        }
    }

    private fun remapMacroAction(action: PadAction, macroIdMap: Map<String, String>): PadAction {
        if (action !is PadAction.Macro) return action
        val newMacroId = macroIdMap[action.macroId] ?: action.macroId
        return PadAction.Macro(macroId = newMacroId)
    }

    private fun importedName(original: String) = "$original (Imported)"

    // ── Checksum ─────────────────────────────────────────────────────────────

    /**
     * Computes a v3 checksum over settings and profiles.
     */
    private fun computeChecksum(
        settings: Map<String, Map<String, JsonElement>>,
        profiles: List<PadProfile>,
    ): String {
        val payload = checksumJson.encodeToString(ChecksumPayload(settings, profiles))
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        val hex = hashBytes.joinToString("") { "%02x".format(it) }
        return "sha256:$hex"
    }

    /**
     * Computes a v2-compatible checksum (includes macros and macroFolders).
     * Used to verify older exports.
     */
    private fun computeChecksumV2(
        settings: Map<String, Map<String, JsonElement>>,
        profiles: List<PadProfile>,
        macros: List<Macro>,
        macroFolders: List<LegacyMacroFolder>,
    ): String {
        val payload = checksumJson.encodeToString(ChecksumPayloadV2(settings, profiles, macros, macroFolders))
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        val hex = hashBytes.joinToString("") { "%02x".format(it) }
        return "sha256:$hex"
    }

    private fun verifyChecksum(export: MegingiardExport): Boolean {
        // v3 checksum (settings + profiles only)
        val v3 = computeChecksum(export.settings, export.profiles)
        if (v3 == export.checksum) return true
        // v2 fallback (settings + profiles + macros + macroFolders)
        val v2 = computeChecksumV2(export.settings, export.profiles, export.macros, export.macroFolders)
        return v2 == export.checksum
    }

    @Serializable
    private data class ChecksumPayload(
        val settings: Map<String, Map<String, JsonElement>>,
        val profiles: List<PadProfile>,
    )

    @Serializable
    private data class ChecksumPayloadV2(
        val settings: Map<String, Map<String, JsonElement>>,
        val profiles: List<PadProfile>,
        val macros: List<Macro>,
        val macroFolders: List<LegacyMacroFolder>,
    )
}
