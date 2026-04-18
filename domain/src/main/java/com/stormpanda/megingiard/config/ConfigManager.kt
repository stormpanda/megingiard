package com.stormpanda.megingiard.config

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.Macro
import com.stormpanda.megingiard.macropad.MacroFolder
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.MacroState
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
 * Replaces ConfigExporter, ConfigImporter, ConfigFileReader, ConfigFileWriter,
 * ChecksumUtil, ConfigImportCoordinator, and ConfigActionCoordinator.
 *
 * Settings are exported/imported as grouped DataStore key/value maps —
 * no intermediate typed data classes. MacroPad data stays typed for UUID remapping.
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
     * Builds a full export from the current app state.
     * Reads all settings from DataStore via [SettingsManager.exportGroupedSettings],
     * snapshots MacroPad data from live StateFlows.
     */
    suspend fun buildExport(metadata: ExportMetadata): MegingiardExport {
        AppLog.i(TAG, "buildExport: author=${metadata.author}")
        val settings = SettingsManager.exportGroupedSettings()
        val profiles = MacroPadState.profiles.value
        val macros = MacroState.macros.value
        val macroFolders = MacroState.folders.value

        val checksum = computeChecksum(settings, profiles, macros, macroFolders)
        AppLog.d(TAG, "buildExport: checksum=$checksum profiles=${profiles.size} macros=${macros.size}")

        return MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = metadata,
            checksum = checksum,
            settings = settings,
            profiles = profiles,
            macros = macros,
            macroFolders = macroFolders,
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
     */
    internal fun parseAndVerify(json: String): MegingiardExport {
        val export = importJson.decodeFromString<MegingiardExport>(json)
        if (export.schemaVersion != SCHEMA_VERSION) {
            error("Unsupported schema version ${export.schemaVersion} — expected $SCHEMA_VERSION")
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
     * MacroPad profiles/macros/folders get new UUIDs and "(Imported)" suffix.
     */
    suspend fun applyImport(export: MegingiardExport) {
        AppLog.i(TAG, "applyImport: schema=${export.schemaVersion}")
        if (export.settings.isNotEmpty()) {
            SettingsManager.importGroupedSettingsAwait(export.settings)
        }
        if (export.profiles.isNotEmpty() || export.macros.isNotEmpty() || export.macroFolders.isNotEmpty()) {
            importMacroPadData(export.profiles, export.macros, export.macroFolders)
        }
    }

    // ── MacroPad import with UUID remapping ─────────────────────────────────

    private fun importMacroPadData(
        profiles: List<PadProfile>,
        macros: List<Macro>,
        macroFolders: List<MacroFolder>,
    ) {
        AppLog.d(TAG, "importMacroPadData: ${profiles.size} profiles, ${macros.size} macros, ${macroFolders.size} folders")

        // Step 1: import folders with new UUIDs
        val folderIdMap = mutableMapOf<String, String>()
        for (folder in macroFolders) {
            val newId = UUID.randomUUID().toString()
            folderIdMap[folder.id] = newId
            MacroState.addFolder(MacroFolder(id = newId, name = importedName(folder.name)))
            AppLog.d(TAG, "imported folder '${folder.name}' → $newId")
        }

        // Step 2: import macros with new UUIDs, remap folder references
        val macroIdMap = mutableMapOf<String, String>()
        for (macro in macros) {
            val newId = UUID.randomUUID().toString()
            macroIdMap[macro.id] = newId
            val newFolderId = macro.folderId?.let { folderIdMap[it] }
            MacroState.addMacro(
                macro.copy(id = newId, name = importedName(macro.name), folderId = newFolderId)
            )
            AppLog.d(TAG, "imported macro '${macro.name}' → $newId")
        }

        // Step 3: import profiles with new UUIDs, remap PadAction.Macro references
        for (profile in profiles) {
            val newProfileId = UUID.randomUUID().toString()
            val remappedButtons = profile.buttons.map { button ->
                button.copy(
                    id = UUID.randomUUID().toString(),
                    action = remapMacroAction(button.action, macroIdMap),
                )
            }
            val importedProfile = profile.copy(
                id = newProfileId,
                name = importedName(profile.name),
                buttons = remappedButtons,
            )
            MacroPadState.addProfile(importedProfile)
            AppLog.d(TAG, "imported profile '${profile.name}' → $newProfileId")
        }
    }

    private fun remapMacroAction(action: PadAction, macroIdMap: Map<String, String>): PadAction {
        if (action !is PadAction.Macro) return action
        val newMacroId = macroIdMap[action.macroId] ?: action.macroId
        return PadAction.Macro(macroId = newMacroId)
    }

    private fun importedName(original: String) = "$original (Imported)"

    // ── Checksum ─────────────────────────────────────────────────────────────

    private fun computeChecksum(
        settings: Map<String, Map<String, JsonElement>>,
        profiles: List<PadProfile>,
        macros: List<Macro>,
        macroFolders: List<MacroFolder>,
    ): String {
        // Build a canonical payload containing all data fields (not metadata)
        val payload = checksumJson.encodeToString(ChecksumPayload(settings, profiles, macros, macroFolders))
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(payload.toByteArray(Charsets.UTF_8))
        val hex = hashBytes.joinToString("") { "%02x".format(it) }
        return "sha256:$hex"
    }

    private fun verifyChecksum(export: MegingiardExport): Boolean {
        val expected = computeChecksum(export.settings, export.profiles, export.macros, export.macroFolders)
        return expected == export.checksum
    }

    @Serializable
    private data class ChecksumPayload(
        val settings: Map<String, Map<String, JsonElement>>,
        val profiles: List<PadProfile>,
        val macros: List<Macro>,
        val macroFolders: List<MacroFolder>,
    )
}
