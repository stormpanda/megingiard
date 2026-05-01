package com.stormpanda.megingiard.config

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.MacroPadState
import com.stormpanda.megingiard.macropad.PadAction
import com.stormpanda.megingiard.macropad.PadProfile
import com.stormpanda.megingiard.settings.SettingsManager
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val TAG = "ConfigManager"

/** Safety cap — reject files larger than 10 MB to prevent OOM. */
private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

/** Minimum schema version accepted on import. Currently only v3 is supported. */
private const val MIN_SUPPORTED_SCHEMA = 3

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
 * Schema v3: macros embedded inside PadProfile.macros; flat DataStore key/value
 * map grouped by section.
 */
object ConfigManager {

    // ── Coordinator SharedFlows (export) ──────────────────────────────────────

    /**
     * Emits a one-shot export command to MainActivity.
     * Uses [BufferOverflow.DROP_OLDEST] so a second rapid call overrides a pending one.
     */
    private val _exportRequest = MutableSharedFlow<ExportMetadata>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val exportRequest: SharedFlow<ExportMetadata> = _exportRequest.asSharedFlow()

    /** Suggested default filename for the "create document" picker. */
    private val _exportFilename = MutableStateFlow("")
    val exportFilename: StateFlow<String> = _exportFilename.asStateFlow()

    fun requestExport(metadata: ExportMetadata, filename: String) {
        AppLog.d(TAG, "requestExport filename=$filename")
        _exportFilename.value = filename
        _exportRequest.tryEmit(metadata)
    }

    // ── Coordinator SharedFlows (import from Settings) ────────────────────────

    /**
     * Emits a one-shot import command to MainActivity (open file picker).
     * Uses [BufferOverflow.DROP_OLDEST] — a second call while the first is processing is a no-op.
     */
    private val _importRequest = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val importRequest: SharedFlow<Unit> = _importRequest.asSharedFlow()

    fun requestImport() {
        AppLog.d(TAG, "requestImport")
        _importRequest.tryEmit(Unit)
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
     * Suspend wrapper: reads and parses [uri] on [Dispatchers.IO].
     * Logs success/failure and returns a [Result].
     */
    suspend fun parseImportUri(context: Context, uri: Uri): Result<MegingiardExport> {
        AppLog.i(TAG, "parseImportUri uri=$uri")
        return withContext(Dispatchers.IO) {
            readFromUri(context, uri).also { result ->
                if (result.isSuccess) AppLog.i(TAG, "parseImportUri succeeded")
                else AppLog.w(TAG, "parseImportUri failed: ${result.exceptionOrNull()}")
            }
        }
    }

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
     */
    suspend fun applyImport(export: MegingiardExport) {
        AppLog.i(TAG, "applyImport: schema=${export.schemaVersion}")
        if (export.settings.isNotEmpty()) {
            SettingsManager.importGroupedSettingsAwait(export.settings)
        }
        if (export.profiles.isNotEmpty()) {
            importMacroPadData(export.profiles)
        }
    }

    // ── MacroPad import with UUID remapping ─────────────────────────────────

    /**
     * Imports profiles with new UUIDs so they don't collide with existing ones.
     */
    private fun importMacroPadData(profiles: List<PadProfile>) {
        AppLog.d(TAG, "importMacroPadData: ${profiles.size} profiles")

        for (profile in profiles) {
            val newProfileId = UUID.randomUUID().toString()

            // Build macro ID remap: old → new for all macros in this profile
            val macroIdMap = mutableMapOf<String, String>()

            val remappedMacros = profile.macros.map { macro ->
                val newId = UUID.randomUUID().toString()
                macroIdMap[macro.id] = newId
                macro.copy(id = newId, name = importedName(macro.name))
            }

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

            // Preserve the source profile's active layout selection when possible.
            val layoutIdMap = profile.layouts.zip(remappedLayouts).associate { (old, new) -> old.id to new.id }
            val preservedActiveLayoutId = layoutIdMap[profile.activeLayoutId]
                ?: remappedLayouts.firstOrNull()?.id
                ?: profile.activeLayoutId

            val importedProfile = profile.copy(
                id = newProfileId,
                name = importedName(profile.name),
                layouts = remappedLayouts,
                macros = remappedMacros,
                activeLayoutId = preservedActiveLayoutId,
            )
            MacroPadState.addProfile(importedProfile)
            AppLog.d(TAG, "imported profile '${profile.name}' → $newProfileId (${remappedMacros.size} macros)")
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

    private fun verifyChecksum(export: MegingiardExport): Boolean {
        val expected = computeChecksum(export.settings, export.profiles)
        return expected == export.checksum
    }

    @Serializable
    private data class ChecksumPayload(
        val settings: Map<String, Map<String, JsonElement>>,
        val profiles: List<PadProfile>,
    )
}
