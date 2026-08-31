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
import com.stormpanda.megingiard.security.HmacUtil
import com.stormpanda.megingiard.settings.SettingsManager
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "ConfigManager"

/** Safety cap — reject files larger than 10 MB to prevent OOM. */
private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

/** Minimum schema version accepted on import. Currently only v3 is supported. */
private const val MIN_SUPPORTED_SCHEMA = 3

private val exportJson =
    Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

private val importJson = Json { ignoreUnknownKeys = true }

/** Deterministic codec for checksum computation — no pretty-print, defaults encoded. */
private val checksumJson =
    Json {
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

    /** Discriminates backup exports from profile-share exports. */
    sealed interface ExportKind {
        val metadata: ExportMetadata
        val includeBackgrounds: Boolean

        /** Full-app backup: all settings + all profiles. */
        data class Backup(
            override val metadata: ExportMetadata,
            override val includeBackgrounds: Boolean = true,
        ) : ExportKind

        /** Single-profile share: no settings, one profile. */
        data class ProfileShare(
            override val metadata: ExportMetadata,
            val profile: PadProfile,
            override val includeBackgrounds: Boolean = true,
        ) : ExportKind
    }

    /** Discriminates backup restores from profile-only imports. */
    enum class ImportMode { BACKUP_RESTORE, PROFILE_SHARE }

    /**
     * Emits a one-shot export command to MainActivity.
     * Uses [BufferOverflow.DROP_OLDEST] so a second rapid call overrides a pending one.
     */
    private val _exportRequest =
        MutableSharedFlow<ExportKind>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val exportRequest: SharedFlow<ExportKind> = _exportRequest.asSharedFlow()

    /** Suggested default filename for the "create document" picker. */
    private val _exportFilename = MutableStateFlow("")
    val exportFilename: StateFlow<String> = _exportFilename.asStateFlow()

    fun requestExport(
        metadata: ExportMetadata,
        filename: String,
        includeBackgrounds: Boolean = true,
    ) {
        AppLog.d(TAG, "requestExport filename=$filename includeBackgrounds=$includeBackgrounds")
        _exportFilename.value = filename
        _exportRequest.tryEmit(ExportKind.Backup(metadata, includeBackgrounds))
    }

    fun requestProfileExport(
        metadata: ExportMetadata,
        profile: PadProfile,
        filename: String,
        includeBackgrounds: Boolean = true,
    ) {
        AppLog.d(TAG, "requestProfileExport profile=${profile.name} filename=$filename includeBackgrounds=$includeBackgrounds")
        _exportFilename.value = filename
        _exportRequest.tryEmit(ExportKind.ProfileShare(metadata, profile, includeBackgrounds))
    }

    // ── Coordinator SharedFlows (import from Settings) ────────────────────────

    /**
     * Emits a one-shot import command to MainActivity (open file picker).
     * Uses [BufferOverflow.DROP_OLDEST] — a second call while the first is processing is a no-op.
     */
    private val _importRequest =
        MutableSharedFlow<ImportMode>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val importRequest: SharedFlow<ImportMode> = _importRequest.asSharedFlow()

    fun requestImport(mode: ImportMode = ImportMode.BACKUP_RESTORE) {
        AppLog.d(TAG, "requestImport mode=$mode")
        _importRequest.tryEmit(mode)
    }

    // ── Coordinator StateFlows (import from external ACTION_VIEW intent) ────────

    /** Non-null while MainAppScreen should parse an externally-opened .mgrd URI. */
    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

    /** Non-null while MainAppScreen should show IncomingImportDialog for an external import. */
    private val _pendingParsedImport = MutableStateFlow<MegingiardExport?>(null)
    val pendingParsedImport: StateFlow<MegingiardExport?> = _pendingParsedImport.asStateFlow()

    /** Extracted background image binaries from pending external import ZIP package. */
    private val _pendingImportImages = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

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
        _pendingImportImages.value = emptyMap()
    }

    // ── Coordinator StateFlows (import from in-app Settings picker) ───────────

    /** Non-null while MainAppScreen should parse an in-app Settings .mgrd URI. */
    private val _pendingInAppUri = MutableStateFlow<Uri?>(null)
    val pendingInAppUri: StateFlow<Uri?> = _pendingInAppUri.asStateFlow()

    /** Non-null while GlobalSettingsScreen should show ImportPreviewDialog. */
    private val _pendingInAppParsedImport = MutableStateFlow<MegingiardExport?>(null)
    val pendingInAppParsedImport: StateFlow<MegingiardExport?> = _pendingInAppParsedImport.asStateFlow()

    /** Extracted background image binaries from pending in-app import ZIP package. */
    private val _pendingInAppImportImages = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    /** Tracks whether the pending in-app import is a full backup or a profile share. */
    private val _pendingInAppImportMode = MutableStateFlow(ImportMode.BACKUP_RESTORE)
    val pendingInAppImportMode: StateFlow<ImportMode> = _pendingInAppImportMode.asStateFlow()

    fun getPendingInAppImageCount(): Int = _pendingInAppImportImages.value.size

    fun getPendingInAppImages(): Map<String, ByteArray> = _pendingInAppImportImages.value

    fun getPendingImages(): Map<String, ByteArray> = _pendingImportImages.value

    /** Called by MainActivity when the in-app file picker returns a .mgrd URI. */
    fun setPendingInAppUri(
        uri: Uri,
        mode: ImportMode = ImportMode.BACKUP_RESTORE,
    ) {
        AppLog.i(TAG, "setPendingInAppUri: $uri mode=$mode")
        _pendingInAppImportMode.value = mode
        _pendingInAppUri.value = uri
    }

    /** Called by MainAppScreen after successfully parsing an in-app import URI. */
    fun setInAppParsedImport(export: MegingiardExport) {
        AppLog.d(TAG, "setInAppParsedImport")
        _pendingInAppParsedImport.value = export
        _pendingInAppUri.value = null
    }

    private val _inAppImportError = MutableStateFlow<String?>(null)
    val inAppImportError: StateFlow<String?> = _inAppImportError.asStateFlow()

    fun setInAppImportError(error: String?) {
        AppLog.d(TAG, "setInAppImportError: $error")
        _inAppImportError.value = error
    }

    /** Clears in-app import state — call after confirm or dismiss of ImportPreviewDialog. */
    fun clearInAppPendingImport() {
        AppLog.d(TAG, "clearInAppPendingImport")
        _pendingInAppUri.value = null
        _pendingInAppParsedImport.value = null
        _pendingInAppImportImages.value = emptyMap()
        _inAppImportError.value = null
        _pendingInAppImportMode.value = ImportMode.BACKUP_RESTORE
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
        data class Success(
            val kind: ExportKind? = null,
        ) : ExportResult

        data class Failure(
            val message: String?,
        ) : ExportResult
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Builds a export from the current app state.
     * Macros are embedded inside each PadProfile — no top-level macros or folders.
     */
    suspend fun buildExport(
        metadata: ExportMetadata,
        context: Context? = null,
        includeBackgrounds: Boolean = true,
    ): MegingiardExport {
        AppLog.i(TAG, "buildExport: author=${metadata.author} includeBackgrounds=$includeBackgrounds")
        val settings = SettingsManager.exportGroupedSettings()
        val profiles = MacroPadState.profiles.value

        val imageHashes =
            if (context != null && includeBackgrounds) {
                collectImageHashes(context, profiles)
            } else {
                emptyMap()
            }

        val checksum = computeChecksum(settings, profiles, imageHashes)
        AppLog.d(TAG, "buildExport: checksum=$checksum profiles=${profiles.size} imageHashes=${imageHashes.size}")

        return MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = metadata,
            checksum = checksum,
            settings = settings,
            profiles = profiles,
        )
    }

    /**
     * Builds a profile-only export containing a single [profile].
     * Settings are always empty so importing this file never overwrites app settings.
     */
    suspend fun buildProfileExport(
        metadata: ExportMetadata,
        profile: PadProfile,
        context: Context? = null,
        includeBackgrounds: Boolean = true,
    ): MegingiardExport {
        AppLog.i(TAG, "buildProfileExport: profile=${profile.name} includeBackgrounds=$includeBackgrounds")
        val profiles = listOf(profile)
        val imageHashes =
            if (context != null && includeBackgrounds) {
                collectImageHashes(context, profiles)
            } else {
                emptyMap()
            }
        val checksum = computeChecksum(emptyMap(), profiles, imageHashes)
        AppLog.d(TAG, "buildProfileExport: checksum=$checksum imageHashes=${imageHashes.size}")
        return MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = metadata,
            checksum = checksum,
            settings = emptyMap(),
            profiles = profiles,
        )
    }

    private fun resolveLayoutBackgroundFile(
        context: Context,
        bgPath: String?,
        layoutId: String,
    ): File? {
        if (bgPath.isNullOrEmpty()) return null
        val fileByPath = File(context.filesDir, bgPath)
        if (fileByPath.exists() && fileByPath.isFile) return fileByPath
        val fileByLayoutId = File(File(context.filesDir, "backgrounds"), "bg_$layoutId")
        if (fileByLayoutId.exists() && fileByLayoutId.isFile) return fileByLayoutId
        return null
    }

    private fun collectLayoutBackgroundFiles(
        context: Context,
        profiles: List<PadProfile>,
    ): Map<String, File> =
        buildMap {
            for (profile in profiles) {
                for (layout in profile.layouts) {
                    val file = resolveLayoutBackgroundFile(context, layout.backgroundImagePath, layout.id)
                    if (file != null) {
                        put("bg_${layout.id}", file)
                    }
                }
            }
        }

    private fun collectImageHashes(
        context: Context,
        profiles: List<PadProfile>,
    ): Map<String, String> =
        collectLayoutBackgroundFiles(context, profiles).mapValues { (_, file) ->
            HmacUtil.sha256Hex(file.readBytes()).lowercase()
        }

    /** Creates pre-filled [ExportMetadata] with the app version and device info. */
    fun defaultMetadata(context: Context): ExportMetadata {
        val packageInfo =
            runCatching {
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

    /** Serializes [export] as JSON (or ZIP archive if [includeBackgrounds] is true) and writes it to [uri]. */
    fun writeToUri(
        context: Context,
        uri: Uri,
        export: MegingiardExport,
        includeBackgrounds: Boolean = true,
    ) {
        AppLog.i(TAG, "writeToUri: uri=$uri includeBackgrounds=$includeBackgrounds")
        val json = exportJson.encodeToString(export)
        val imageFilesToBundle =
            if (includeBackgrounds) {
                collectLayoutBackgroundFiles(context, export.profiles)
            } else {
                emptyMap()
            }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            if (imageFilesToBundle.isNotEmpty()) {
                ZipOutputStream(out).use { zip ->
                    // 1. Write config.json
                    zip.putNextEntry(ZipEntry("config.json"))
                    zip.write(json.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    // 2. Write background image entries
                    for ((entryName, file) in imageFilesToBundle) {
                        zip.putNextEntry(ZipEntry("backgrounds/$entryName"))
                        zip.write(file.readBytes())
                        zip.closeEntry()
                    }
                }
                AppLog.i(TAG, "Wrote ZIP package with config.json and ${imageFilesToBundle.size} image(s)")
            } else {
                out.write(json.toByteArray(Charsets.UTF_8))
                AppLog.i(TAG, "Wrote plain JSON config export")
            }
        } ?: error("Could not open output stream for URI: $uri")
    }

    // ── Import ───────────────────────────────────────────────────────────────

    /**
     * Suspend wrapper: reads and parses [uri] on [Dispatchers.IO].
     * Logs success/failure and returns a [Result].
     */
    suspend fun parseImportUri(
        context: Context,
        uri: Uri,
        isInApp: Boolean = false,
    ): Result<MegingiardExport> {
        AppLog.i(TAG, "parseImportUri uri=$uri isInApp=$isInApp")
        return withContext(Dispatchers.IO) {
            readFromUri(context, uri, isInApp).also { result ->
                if (result.isSuccess) {
                    AppLog.i(TAG, "parseImportUri succeeded")
                } else {
                    AppLog.w(TAG, "parseImportUri failed: ${result.exceptionOrNull()}")
                }
            }
        }
    }

    /**
     * Reads a `.mgrd` file from [uri], verifies checksum and schema version.
     */
    fun readFromUri(
        context: Context,
        uri: Uri,
        isInApp: Boolean = false,
    ): Result<MegingiardExport> =
        runCatching {
            AppLog.i(TAG, "readFromUri: uri=$uri isInApp=$isInApp")
            // Reject oversized files before allocating: check declared size if available.
            context.contentResolver
                .query(
                    uri,
                    arrayOf(OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val declared = cursor.getLong(0)
                        check(declared <= MAX_FILE_SIZE_BYTES) {
                            "File too large: $declared bytes (max $MAX_FILE_SIZE_BYTES)"
                        }
                    }
                }
            val bytes =
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val read = input.readBytes()
                    check(read.size <= MAX_FILE_SIZE_BYTES) {
                        "File too large: ${read.size} bytes (max $MAX_FILE_SIZE_BYTES)"
                    }
                    read
                } ?: error("Could not open input stream for URI: $uri")

            val isZip =
                bytes.size >= 4 &&
                    bytes[0] == 0x50.toByte() &&
                    bytes[1] == 0x4B.toByte() &&
                    bytes[2] == 0x03.toByte() &&
                    bytes[3] == 0x04.toByte()

            if (isZip) {
                var jsonText: String? = null
                val imagesMap = mutableMapOf<String, ByteArray>()
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (!entry.isDirectory) {
                            val entryBytes = zip.readBytes()
                            if (entryName == "config.json" || entryName.endsWith(".json")) {
                                jsonText = entryBytes.toString(Charsets.UTF_8)
                            } else if (entryName.startsWith("backgrounds/") || entryName.startsWith("bg_")) {
                                val key = entryName.removePrefix("backgrounds/").removePrefix("bg_")
                                imagesMap[key] = entryBytes
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val json = jsonText ?: error("Invalid .mgrd package — missing config.json")
                val export = parseAndVerify(json, imagesMap)
                if (isInApp) {
                    _pendingInAppImportImages.value = imagesMap
                } else {
                    _pendingImportImages.value = imagesMap
                }
                export
            } else {
                val json = bytes.toString(Charsets.UTF_8)
                if (isInApp) {
                    _pendingInAppImportImages.value = emptyMap()
                } else {
                    _pendingImportImages.value = emptyMap()
                }
                parseAndVerify(json)
            }
        }

    /**
     * Parses a JSON string, validates the schema version, verifies checksum.
     */
    internal fun parseAndVerify(
        json: String,
        extractedImages: Map<String, ByteArray> = emptyMap(),
    ): MegingiardExport {
        val export = importJson.decodeFromString<MegingiardExport>(json)
        if (export.schemaVersion < MIN_SUPPORTED_SCHEMA || export.schemaVersion > SCHEMA_VERSION) {
            error("Unsupported schema version ${export.schemaVersion} — expected $MIN_SUPPORTED_SCHEMA..$SCHEMA_VERSION")
        }
        if (!verifyChecksum(export, extractedImages, json)) {
            error("Checksum mismatch — the file may be corrupted or tampered")
        }
        AppLog.i(TAG, "parseAndVerify: OK schema=${export.schemaVersion}")
        return export
    }

    /**
     * Applies all settings and macropad data from [export] to the running app state.
     * Settings are awaited so callers know the DataStore write completed before showing success.
     * MacroPad profiles get new UUIDs to avoid collisions; names are kept as-is.
     */
    suspend fun applyImport(
        context: Context,
        export: MegingiardExport,
        images: Map<String, ByteArray>? = null,
    ) {
        AppLog.i(TAG, "applyImport: schema=${export.schemaVersion}")
        val resolvedImages = images ?: _pendingInAppImportImages.value.ifEmpty { _pendingImportImages.value }
        try {
            if (export.settings.isNotEmpty()) {
                SettingsManager.importGroupedSettingsAwait(export.settings)
            }
            if (export.profiles.isNotEmpty()) {
                importMacroPadData(context, export.profiles, resolvedImages)
            }
        } finally {
            clearPendingImport()
            clearInAppPendingImport()
        }
    }

    /**
     * Imports only the profiles from [export]; never touches app settings.
     * Throws [IllegalStateException] if [export] contains no profiles.
     */
    suspend fun applyProfileImport(
        context: Context,
        export: MegingiardExport,
        images: Map<String, ByteArray>? = null,
    ) {
        AppLog.i(TAG, "applyProfileImport: schema=${export.schemaVersion} profiles=${export.profiles.size}")
        check(export.profiles.isNotEmpty()) { "The file does not contain any profiles" }
        val resolvedImages = images ?: _pendingInAppImportImages.value.ifEmpty { _pendingImportImages.value }
        try {
            importMacroPadData(context, export.profiles, resolvedImages)
        } finally {
            clearPendingImport()
            clearInAppPendingImport()
        }
    }

    // ── MacroPad import with UUID remapping ─────────────────────────────────

    /**
     * Imports profiles with new UUIDs so they don't collide with existing ones.
     */
    private fun importMacroPadData(
        context: Context,
        profiles: List<PadProfile>,
        images: Map<String, ByteArray> = emptyMap(),
    ) {
        AppLog.d(TAG, "importMacroPadData: ${profiles.size} profiles (images map size=${images.size})")
        val backgroundsDir = File(context.filesDir, "backgrounds")
        if (!backgroundsDir.exists() &&
            (images.isNotEmpty() || profiles.any { p -> p.layouts.any { !it.backgroundImagePath.isNullOrEmpty() } })
        ) {
            backgroundsDir.mkdirs()
        }

        for (profile in profiles) {
            val newProfileId = UUID.randomUUID().toString()

            // Build macro ID remap: old → new for all macros in this profile
            val macroIdMap = mutableMapOf<String, String>()

            val remappedMacros =
                profile.macros.map { macro ->
                    val newId = UUID.randomUUID().toString()
                    macroIdMap[macro.id] = newId
                    macro.copy(id = newId)
                }

            // Remap macro references in button actions and handle background image copying/extraction
            val remappedLayouts =
                profile.layouts.map { layout ->
                    val oldLayoutId = layout.id
                    val newLayoutId = UUID.randomUUID().toString()

                    val bgKeyFromPath = layout.backgroundImagePath?.substringAfterLast("/")?.removePrefix("bg_")
                    val imageBytes =
                        images[oldLayoutId]
                            ?: images["bg_$oldLayoutId"]
                            ?: images["backgrounds/bg_$oldLayoutId"]
                            ?: bgKeyFromPath?.let { key -> images[key] ?: images["bg_$key"] ?: images["backgrounds/bg_$key"] }

                    val newBgPath: String? =
                        if (imageBytes != null) {
                            val destFile = File(backgroundsDir, "bg_$newLayoutId")
                            runCatching {
                                destFile.writeBytes(imageBytes)
                                "backgrounds/bg_$newLayoutId"
                            }.getOrElse { e ->
                                AppLog.e(TAG, "Failed to write extracted background for layout $newLayoutId", e)
                                null
                            }
                        } else if (!layout.backgroundImagePath.isNullOrEmpty()) {
                            val bgPath = layout.backgroundImagePath!!
                            val srcFile = File(context.filesDir, bgPath)
                            val filesDirCanonical = context.filesDir.canonicalPath
                            val isSafePath =
                                !bgPath.contains("..") &&
                                    runCatching { srcFile.canonicalPath.startsWith(filesDirCanonical) }.getOrDefault(false)
                            val srcFileFallback = File(backgroundsDir, "bg_$oldLayoutId")
                            val existingSrc =
                                if (isSafePath && srcFile.exists()) {
                                    srcFile
                                } else if (srcFileFallback.exists()) {
                                    srcFileFallback
                                } else {
                                    null
                                }
                            if (existingSrc != null) {
                                val destFile = File(backgroundsDir, "bg_$newLayoutId")
                                runCatching {
                                    existingSrc.copyTo(destFile, overwrite = true)
                                    "backgrounds/bg_$newLayoutId"
                                }.getOrElse { e ->
                                    AppLog.e(TAG, "Failed to copy local background for layout $newLayoutId", e)
                                    null
                                }
                            } else {
                                null
                            }
                        } else {
                            null
                        }

                    layout.copy(
                        id = newLayoutId,
                        backgroundImagePath = newBgPath,
                        buttons =
                            layout.buttons.map { button ->
                                button.copy(
                                    id = UUID.randomUUID().toString(),
                                    action = remapMacroAction(button.action, macroIdMap),
                                )
                            },
                    )
                }

            // Preserve the source profile's active layout selection when possible.
            val layoutIdMap = profile.layouts.zip(remappedLayouts).associate { (old, new) -> old.id to new.id }
            val preservedActiveLayoutId =
                layoutIdMap[profile.activeLayoutId]
                    ?: remappedLayouts.firstOrNull()?.id
                    ?: profile.activeLayoutId

            val importedProfile =
                profile.copy(
                    id = newProfileId,
                    name = profile.name,
                    layouts = remappedLayouts,
                    macros = remappedMacros,
                    activeLayoutId = preservedActiveLayoutId,
                )
            MacroPadState.addProfile(importedProfile)
            AppLog.d(TAG, "imported profile '${profile.name}' → $newProfileId (${remappedMacros.size} macros)")
        }
    }

    private fun remapMacroAction(
        action: PadAction,
        macroIdMap: Map<String, String>,
    ): PadAction {
        if (action !is PadAction.Macro) return action
        val newMacroId = macroIdMap[action.macroId] ?: action.macroId
        return PadAction.Macro(macroId = newMacroId)
    }

    // ── Checksum ─────────────────────────────────────────────────────────────

    /**
     * Computes a v3/v4 checksum over settings, profiles, and optional image hashes.
     */
    private fun computeChecksum(
        settings: Map<String, Map<String, JsonElement>>,
        profiles: List<PadProfile>,
        imageHashes: Map<String, String> = emptyMap(),
    ): String {
        val payload = checksumJson.encodeToString(ChecksumPayload(settings, profiles, imageHashes))
        val hex = HmacUtil.sha256Hex(payload.toByteArray(Charsets.UTF_8)).lowercase()
        return "sha256:$hex"
    }

    private fun verifyChecksum(
        export: MegingiardExport,
        extractedImages: Map<String, ByteArray> = emptyMap(),
        rawJson: String? = null,
    ): Boolean {
        val imageHashes =
            extractedImages
                .mapKeys { (k, _) ->
                    if (k.startsWith("bg_")) k else "bg_${k.removePrefix("backgrounds/").removePrefix("bg_")}"
                }.mapValues { (_, bytes) ->
                    HmacUtil
                        .sha256Hex(bytes)
                        .lowercase()
                }

        if (!rawJson.isNullOrEmpty()) {
            runCatching {
                val rootObj = importJson.parseToJsonElement(rawJson).jsonObject
                val rawSettings = rootObj["settings"] ?: JsonObject(emptyMap())
                val rawProfiles = rootObj["profiles"] ?: JsonArray(emptyList())

                val rawPayloadNoImages =
                    checksumJson.encodeToString(RawChecksumPayload(rawSettings, rawProfiles, emptyMap()))
                val hexNoImages = HmacUtil.sha256Hex(rawPayloadNoImages.toByteArray(Charsets.UTF_8)).lowercase()
                if ("sha256:$hexNoImages" == export.checksum) return true

                if (imageHashes.isNotEmpty()) {
                    val rawPayloadWithImages =
                        checksumJson.encodeToString(RawChecksumPayload(rawSettings, rawProfiles, imageHashes))
                    val hexWithImages = HmacUtil.sha256Hex(rawPayloadWithImages.toByteArray(Charsets.UTF_8)).lowercase()
                    if ("sha256:$hexWithImages" == export.checksum) return true
                }
            }.onFailure { e ->
                AppLog.w(TAG, "Raw JSON checksum verification failed: ${e.message}")
            }
        }

        val expectedWithoutImages = computeChecksum(export.settings, export.profiles, emptyMap())
        if (expectedWithoutImages == export.checksum) return true

        val expectedWithImages = computeChecksum(export.settings, export.profiles, imageHashes)
        return expectedWithImages == export.checksum
    }

    @Serializable
    private data class ChecksumPayload(
        val settings: Map<String, Map<String, JsonElement>>,
        val profiles: List<PadProfile>,
        val imageHashes: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class RawChecksumPayload(
        val settings: JsonElement,
        val profiles: JsonElement,
        val imageHashes: Map<String, String> = emptyMap(),
    )
}
