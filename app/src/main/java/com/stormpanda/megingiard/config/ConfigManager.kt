package com.stormpanda.megingiard.config

import android.content.Context
import android.net.Uri
import android.os.Build
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

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

    // ── Coordinator StateFlows (import from external intent) ─────────────────

    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

    private val _pendingParsedImport = MutableStateFlow<MegingiardExport?>(null)
    val pendingParsedImport: StateFlow<MegingiardExport?> = _pendingParsedImport.asStateFlow()

    /** Called by MainActivity when an ACTION_VIEW intent with a .mgrd URI is received. */
    fun setPendingUri(uri: Uri) {
        AppLog.i(TAG, "setPendingUri: $uri")
        _pendingUri.value = uri
    }

    /** Called by MainAppScreen after successfully parsing the URI's content. */
    fun setParsedImport(export: MegingiardExport) {
        AppLog.d(TAG, "setParsedImport")
        _pendingParsedImport.value = export
        _pendingUri.value = null
    }

    /** Clears both pending URI and parsed import — call after confirm or dismiss. */
    fun clearPendingImport() {
        AppLog.d(TAG, "clearPendingImport")
        _pendingUri.value = null
        _pendingParsedImport.value = null
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
            context.packageManager.getPackageInfo(context.packageName, 0)
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
     * Reads a `.mgrd` file from [uri], auto-detects v1/v2 schema, verifies checksum.
     */
    fun readFromUri(context: Context, uri: Uri): Result<MegingiardExport> = runCatching {
        AppLog.i(TAG, "readFromUri: uri=$uri")
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
     * Parses a JSON string, auto-detects v1/v2, verifies checksum.
     * Exposed for direct use by MainAppScreen when the raw JSON is already available.
     */
    internal fun parseAndVerify(json: String): MegingiardExport {
        val root = importJson.parseToJsonElement(json).jsonObject
        val schemaRaw = root["schemaVersion"]?.jsonPrimitive

        // Detect v1: schemaVersion is a string (e.g. "1.0.0") and has "sections" key
        val isV1 = schemaRaw?.isString == true && "sections" in root
        val export = if (isV1) {
            AppLog.i(TAG, "parseAndVerify: detected v1 schema, converting")
            convertV1(root)
        } else {
            importJson.decodeFromString<MegingiardExport>(json)
        }

        if (!verifyChecksum(export)) {
            error("Checksum mismatch — the file may be corrupted or tampered")
        }
        AppLog.i(TAG, "parseAndVerify: OK schema=${export.schemaVersion}")
        return export
    }

    /**
     * Applies all settings and macropad data from [export] to the running app state.
     * Settings are written to DataStore in bulk; the reactive pipeline auto-updates StateFlows.
     * MacroPad profiles/macros/folders get new UUIDs and "(Imported)" suffix.
     */
    fun applyImport(export: MegingiardExport) {
        AppLog.i(TAG, "applyImport: schema=${export.schemaVersion}")
        if (export.settings.isNotEmpty()) {
            SettingsManager.importGroupedSettings(export.settings)
        }
        if (export.profiles.isNotEmpty() || export.macros.isNotEmpty() || export.macroFolders.isNotEmpty()) {
            importMacroPadData(export.profiles, export.macros, export.macroFolders)
        }
    }

    // ── V1 compatibility ─────────────────────────────────────────────────────

    /**
     * Static mapping from (v1 section, v1 field name) → DataStore key name.
     * Frozen legacy code — only used for importing old v1 `.mgrd` files.
     */
    private val V1_FIELD_MAP: Map<Pair<String, String>, Pair<String, String>> = mapOf(
        // global section → DataStore key
        ("global" to "enabledTools") to ("global" to "enabled_tools"),
        ("global" to "toolOrder") to ("global" to "tool_order"),
        ("global" to "overlayTimeoutMs") to ("global" to "overlay_timeout_ms"),
        ("global" to "overlayAtBottom") to ("global" to "overlay_at_bottom"),
        ("global" to "accentColor") to ("global" to "accent_color"),
        ("global" to "themeMode") to ("global" to "theme_mode"),
        ("global" to "appLanguage") to ("global" to "app_language"),
        ("global" to "logLevel") to ("global" to "log_level"),
        ("global" to "rememberLastTool") to ("global" to "remember_last_tool"),
        // mirror section
        ("mirror" to "autoStartCapture") to ("mirror" to "auto_start_capture"),
        ("mirror" to "pinchWhileProjecting") to ("mirror" to "mirror_pinch_while_projecting"),
        ("mirror" to "rememberViewport") to ("mirror" to "mirror_remember_viewport"),
        ("mirror" to "rememberLock") to ("mirror" to "mirror_remember_lock"),
        ("mirror" to "rememberProjection") to ("mirror" to "mirror_remember_projection"),
        // touchpad section
        ("touchpad" to "useMouse") to ("touchpad" to "touchpad_use_mouse"),
        ("touchpad" to "tapToClick") to ("touchpad" to "touchpad_tap_to_click"),
        ("touchpad" to "twoFingerTap") to ("touchpad" to "touchpad_two_finger_tap"),
        // keyboard section
        ("keyboard" to "layout") to ("keyboard" to "kb_layout"),
        ("keyboard" to "trackpointEnabled") to ("keyboard" to "kb_trackpoint_enabled"),
        ("keyboard" to "mouseBtnPos") to ("keyboard" to "kb_mouse_btn_pos"),
        ("keyboard" to "repeatEnabled") to ("keyboard" to "kb_repeat_enabled"),
        ("keyboard" to "fullscreen") to ("keyboard" to "kb_fullscreen"),
        // macropad settings (nested under macropad.settings in v1)
        ("macropad_settings" to "ambientEnabled") to ("macropad_settings" to "macropad_ambient_enabled"),
        ("macropad_settings" to "ambientDim") to ("macropad_settings" to "macropad_ambient_dim"),
        ("macropad_settings" to "ambientPreview") to ("macropad_settings" to "macropad_ambient_preview"),
        ("macropad_settings" to "ambientApplyTheme") to ("macropad_settings" to "macropad_ambient_apply_theme"),
        ("macropad_settings" to "vignetteEnabled") to ("macropad_settings" to "macropad_ambient_vignette_enabled"),
        ("macropad_settings" to "vignetteShape") to ("macropad_settings" to "macropad_ambient_vignette_shape"),
        ("macropad_settings" to "vignetteVisibleArea") to ("macropad_settings" to "macropad_ambient_vignette_visible_area"),
        ("macropad_settings" to "vignetteTransition") to ("macropad_settings" to "macropad_ambient_vignette_transition"),
        ("macropad_settings" to "vignetteOpacity") to ("macropad_settings" to "macropad_ambient_vignette_opacity"),
        ("macropad_settings" to "vignetteColor") to ("macropad_settings" to "macropad_ambient_vignette_color"),
    )

    /**
     * Converts a v1 JSON structure to v2 [MegingiardExport].
     *
     * V1 had typed per-section data classes under a "sections" key:
     * `{ "sections": { "global": { "enabledTools": [...], ... }, "macropad": { "settings": {...}, "profiles": [...] } } }`
     *
     * V2 uses DataStore key names directly:
     * `{ "settings": { "global": { "enabled_tools": "MIRROR,KEYBOARD", ... } }, "profiles": [...] }`
     */
    private fun convertV1(root: JsonObject): MegingiardExport {
        val metadata = importJson.decodeFromString<ExportMetadata>(root["metadata"].toString())
        val checksum = root["checksum"]?.jsonPrimitive?.contentOrNull ?: ""
        val sections = root["sections"]?.jsonObject ?: JsonObject(emptyMap())

        val v2Settings = mutableMapOf<String, MutableMap<String, JsonElement>>()

        // Convert simple sections (global, mirror, touchpad, keyboard)
        for (v1Section in listOf("global", "mirror", "touchpad", "keyboard")) {
            val obj = sections[v1Section]?.jsonObject ?: continue
            for ((fieldName, value) in obj) {
                val mapping = V1_FIELD_MAP[v1Section to fieldName] ?: continue
                val (v2Section, v2Key) = mapping
                v2Settings.getOrPut(v2Section) { mutableMapOf() }[v2Key] = convertV1Value(v1Section, fieldName, value)
            }
        }

        // Convert macropad settings (nested: sections.macropad.settings)
        val macropadObj = sections["macropad"]?.jsonObject
        val settingsObj = macropadObj?.get("settings")?.jsonObject
        if (settingsObj != null) {
            for ((fieldName, value) in settingsObj) {
                val mapping = V1_FIELD_MAP["macropad_settings" to fieldName] ?: continue
                val (v2Section, v2Key) = mapping
                v2Settings.getOrPut(v2Section) { mutableMapOf() }[v2Key] = convertV1Value("macropad_settings", fieldName, value)
            }
        }

        // Extract typed macropad data
        val profiles: List<PadProfile> = macropadObj?.get("profiles")?.let {
            importJson.decodeFromString(it.toString())
        } ?: emptyList()
        val macros: List<Macro> = macropadObj?.get("macros")?.let {
            importJson.decodeFromString(it.toString())
        } ?: emptyList()
        val macroFolders: List<MacroFolder> = macropadObj?.get("macroFolders")?.let {
            importJson.decodeFromString(it.toString())
        } ?: emptyList()

        return MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = metadata,
            checksum = checksum,
            settings = v2Settings,
            profiles = profiles,
            macros = macros,
            macroFolders = macroFolders,
        )
    }

    /**
     * Converts v1 field values to v2 format.
     * V1 stored lists as JSON arrays (e.g. enabledTools: ["MIRROR","KEYBOARD"]) and
     * colors as hex strings ("#FFCC0000"). V2 uses DataStore format: comma-separated
     * strings and raw ARGB ints.
     */
    private fun convertV1Value(section: String, field: String, value: JsonElement): JsonElement {
        // List fields in v1 → comma-separated string in v2
        if (section == "global" && (field == "enabledTools" || field == "toolOrder")) {
            val items = value.jsonArray.map { it.jsonPrimitive.contentOrNull ?: "" }
            return JsonPrimitive(items.joinToString(","))
        }
        // Hex color strings → ARGB int
        if (field == "accentColor" || field == "vignetteColor") {
            val hex = value.jsonPrimitive.contentOrNull ?: return value
            val argb = hex.removePrefix("#").toLong(16).toInt()
            return JsonPrimitive(argb)
        }
        return value
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

    @kotlinx.serialization.Serializable
    private data class ChecksumPayload(
        val settings: Map<String, Map<String, JsonElement>>,
        val profiles: List<PadProfile>,
        val macros: List<Macro>,
        val macroFolders: List<MacroFolder>,
    )
}
