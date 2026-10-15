package com.stormpanda.megingiard.config

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.macropad.BackgroundScaleMode
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "ConfigManager"

/** Safety cap — reject files larger than 10 MB to prevent OOM. */
private const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024

/** Safety cap — reject extracted archives larger than 20 MB total to prevent ZIP-bomb OOM. */
private const val MAX_TOTAL_ZIP_BYTES = 20 * 1024 * 1024
private const val READ_BUFFER_SIZE = 8192

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

    // ── Coordinator StateFlows (import from external ACTION_VIEW & in-app picker) ──

    /** Request to parse a `.mgrd` URI in [MainAppScreen], tagged by origin. */
    data class PendingUriRequest(
        val uri: Uri,
        val isInApp: Boolean,
    )

    /** Self-contained result of parsing a `.mgrd` JSON or ZIP archive. */
    data class ParsedArchive(
        val export: MegingiardExport,
        val images: Map<String, ByteArray> = emptyMap(),
    )

    /** Non-null while MainAppScreen should parse an incoming .mgrd URI. */
    private val _pendingUriRequest = MutableStateFlow<PendingUriRequest?>(null)
    val pendingUriRequest: StateFlow<PendingUriRequest?> = _pendingUriRequest.asStateFlow()

    /** Non-null while MainAppScreen should show IncomingImportDialog for an external import. */
    private val _pendingParsedImport = MutableStateFlow<ParsedArchive?>(null)
    val pendingParsedImport: StateFlow<ParsedArchive?> = _pendingParsedImport.asStateFlow()

    /** Non-null while GlobalSettingsScreen should show RestoreReviewSubPage. */
    private val _pendingInAppParsedImport = MutableStateFlow<ParsedArchive?>(null)
    val pendingInAppParsedImport: StateFlow<ParsedArchive?> = _pendingInAppParsedImport.asStateFlow()

    /** Tracks whether the pending in-app import is a full backup or a profile share. */
    private val _pendingInAppImportMode = MutableStateFlow(ImportMode.BACKUP_RESTORE)
    val pendingInAppImportMode: StateFlow<ImportMode> = _pendingInAppImportMode.asStateFlow()

    private val _inAppImportError = MutableStateFlow<String?>(null)
    val inAppImportError: StateFlow<String?> = _inAppImportError.asStateFlow()

    fun getPendingInAppImageCount(): Int = _pendingInAppParsedImport.value?.images?.size ?: 0

    /** Called by MainActivity when an ACTION_VIEW intent with a .mgrd URI is received. */
    fun setPendingUri(uri: Uri) {
        AppLog.i(TAG, "setPendingUri: $uri")
        _pendingUriRequest.value = PendingUriRequest(uri, isInApp = false)
    }

    /** Called by MainActivity when the in-app file picker returns a .mgrd URI. */
    fun setPendingInAppUri(
        uri: Uri,
        mode: ImportMode = ImportMode.BACKUP_RESTORE,
    ) {
        AppLog.i(TAG, "setPendingInAppUri: $uri mode=$mode")
        _pendingInAppImportMode.value = mode
        _pendingUriRequest.value = PendingUriRequest(uri, isInApp = true)
    }

    fun setPendingInAppImportMode(mode: ImportMode) {
        AppLog.d(TAG, "setPendingInAppImportMode: $mode")
        _pendingInAppImportMode.value = mode
    }

    /** Called by MainAppScreen after successfully parsing an external import URI. */
    fun setParsedImport(archive: ParsedArchive) {
        AppLog.d(TAG, "setParsedImport")
        _pendingParsedImport.value = archive
        _pendingUriRequest.value = null
    }

    /** Called by MainAppScreen or GlobalSettingsScreen when staging an in-app import for review. */
    fun setInAppParsedImport(
        export: MegingiardExport,
        images: Map<String, ByteArray> = emptyMap(),
    ) {
        setInAppParsedImport(ParsedArchive(export, images))
    }

    fun setInAppParsedImport(archive: ParsedArchive) {
        AppLog.d(TAG, "setInAppParsedImport")
        _pendingInAppParsedImport.value = archive
        _pendingUriRequest.value = null
    }

    fun setInAppImportError(error: String?) {
        AppLog.d(TAG, "setInAppImportError: $error")
        _inAppImportError.value = error
    }

    /** Clears external import state — call after confirm or dismiss of IncomingImportDialog. */
    fun clearPendingImport() {
        AppLog.d(TAG, "clearPendingImport")
        if (_pendingUriRequest.value?.isInApp == false) {
            _pendingUriRequest.value = null
        }
        _pendingParsedImport.value = null
    }

    /** Clears in-app import state — call after confirm or dismiss of RestoreReviewSubPage. */
    fun clearInAppPendingImport() {
        AppLog.d(TAG, "clearInAppPendingImport")
        if (_pendingUriRequest.value?.isInApp == true) {
            _pendingUriRequest.value = null
        }
        _pendingInAppParsedImport.value = null
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
     * Builds an export from the current app state.
     * Macros are embedded inside each PadProfile — no top-level macros or folders.
     */
    suspend fun buildExport(
        metadata: ExportMetadata,
        context: Context? = null,
        includeBackgrounds: Boolean = true,
    ): MegingiardExport {
        AppLog.i(TAG, "buildExport: author=${metadata.author} includeBackgrounds=$includeBackgrounds")
        return buildExportInternal(
            metadata = metadata,
            settings = SettingsManager.exportGroupedSettings(),
            profiles = MacroPadState.profiles.value,
            context = context,
            includeBackgrounds = includeBackgrounds,
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
        return buildExportInternal(
            metadata = metadata,
            settings = emptyMap(),
            profiles = listOf(profile),
            context = context,
            includeBackgrounds = includeBackgrounds,
        )
    }

    private fun buildExportInternal(
        metadata: ExportMetadata,
        settings: Map<String, Map<String, JsonElement>>,
        profiles: List<PadProfile>,
        context: Context?,
        includeBackgrounds: Boolean,
    ): MegingiardExport {
        val imageHashes =
            if (context != null && includeBackgrounds) {
                collectImageHashes(context, profiles)
            } else {
                emptyMap()
            }
        val checksum = computeChecksum(settings, profiles, imageHashes)
        AppLog.d(TAG, "buildExportInternal: checksum=$checksum profiles=${profiles.size} imageHashes=${imageHashes.size}")
        return MegingiardExport(
            schemaVersion = SCHEMA_VERSION,
            metadata = metadata,
            checksum = checksum,
            settings = settings,
            profiles = profiles,
        )
    }

    private fun InputStream.readBytesBounded(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(READ_BUFFER_SIZE)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            check(total <= maxBytes) {
                "File too large: exceeds $maxBytes bytes"
            }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun isSafeFileInDir(
        baseDir: File,
        target: File,
    ): Boolean =
        runCatching {
            val baseCanonical = baseDir.canonicalFile.path
            val targetCanonical = target.canonicalFile.path
            targetCanonical.startsWith("$baseCanonical${File.separator}")
        }.getOrDefault(false)

    private fun resolveLocalMediaFile(
        context: Context,
        dirName: String,
        prefix: String,
        relativePath: String?,
        layoutId: String,
    ): File? {
        if (relativePath.isNullOrEmpty()) return null
        val mediaDir = File(context.filesDir, dirName)
        val fileByPath = File(context.filesDir, relativePath)
        if (!relativePath.contains("..") && isSafeFileInDir(mediaDir, fileByPath) && fileByPath.exists() && fileByPath.isFile) {
            return fileByPath
        }
        val fileByLayoutId = File(mediaDir, "${prefix}_$layoutId")
        if (isSafeFileInDir(mediaDir, fileByLayoutId) && fileByLayoutId.exists() && fileByLayoutId.isFile) {
            return fileByLayoutId
        }
        return null
    }

    private fun collectLayoutBackgroundFiles(
        context: Context,
        profiles: List<PadProfile>,
    ): Map<String, File> =
        buildMap {
            for (profile in profiles) {
                for (layout in profile.layouts) {
                    resolveLocalMediaFile(context, "backgrounds", "bg", layout.backgroundImagePath, layout.id)?.let {
                        put("bg_${layout.id}", it)
                    }
                    resolveLocalMediaFile(context, "masks", "mask", layout.maskImagePath, layout.id)?.let {
                        put("mask_${layout.id}", it)
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
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
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
    ): Result<ParsedArchive> {
        AppLog.i(TAG, "parseImportUri uri=$uri")
        return withContext(Dispatchers.IO) {
            readFromUri(context, uri).also { result ->
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
    ): Result<ParsedArchive> =
        runCatching {
            AppLog.i(TAG, "readFromUri: uri=$uri")
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
                    input.readBytesBounded(MAX_FILE_SIZE_BYTES)
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
                var totalExtractedBytes = 0
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (!entry.isDirectory) {
                            val entryBytes = zip.readBytesBounded(MAX_FILE_SIZE_BYTES)
                            totalExtractedBytes += entryBytes.size
                            check(totalExtractedBytes <= MAX_TOTAL_ZIP_BYTES) {
                                "Archive too large when uncompressed: exceeds $MAX_TOTAL_ZIP_BYTES bytes"
                            }
                            if (entryName == "config.json" || entryName.endsWith(".json")) {
                                jsonText = entryBytes.toString(Charsets.UTF_8)
                            } else if (entryName.startsWith("backgrounds/") ||
                                entryName.startsWith("masks/") ||
                                entryName.startsWith("bg_") ||
                                entryName.startsWith("mask_")
                            ) {
                                val key = entryName.substringAfterLast("/")
                                imagesMap[key] = entryBytes
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val json = jsonText ?: error("Invalid .mgrd package — missing config.json")
                val export = parseAndVerify(json, imagesMap)
                ParsedArchive(export, imagesMap)
            } else {
                val json = bytes.toString(Charsets.UTF_8)
                ParsedArchive(parseAndVerify(json))
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
        val resolvedImages =
            images
                ?: _pendingInAppParsedImport.value?.images?.ifEmpty { null }
                ?: _pendingParsedImport.value?.images.orEmpty()
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
        val resolvedImages =
            images
                ?: _pendingInAppParsedImport.value?.images?.ifEmpty { null }
                ?: _pendingParsedImport.value?.images.orEmpty()
        try {
            importMacroPadData(context, export.profiles, resolvedImages)
        } finally {
            clearPendingImport()
            clearInAppPendingImport()
        }
    }

    // ── MacroPad import with UUID remapping ─────────────────────────────────

    private fun extractOrCopyMedia(
        context: Context,
        images: Map<String, ByteArray>,
        targetDir: File,
        dirPrefix: String,
        prefix: String,
        oldLayoutId: String,
        newLayoutId: String,
        currentPath: String?,
        destPrefix: String = prefix,
    ): String? {
        val imageBytes = images["${prefix}_$oldLayoutId"] ?: if (prefix == "bg") images[oldLayoutId] else null

        if (imageBytes != null) {
            val destFile = File(targetDir, "${destPrefix}_$newLayoutId")
            return runCatching {
                destFile.writeBytes(imageBytes)
                "$dirPrefix/${destPrefix}_$newLayoutId"
            }.getOrElse { e ->
                AppLog.e(TAG, "Failed to write extracted $prefix for layout $newLayoutId", e)
                null
            }
        }

        val existingSrc = resolveLocalMediaFile(context, dirPrefix, prefix, currentPath, oldLayoutId)
        if (existingSrc != null) {
            val destFile = File(targetDir, "${destPrefix}_$newLayoutId")
            return runCatching {
                existingSrc.copyTo(destFile, overwrite = true)
                "$dirPrefix/${destPrefix}_$newLayoutId"
            }.getOrElse { e ->
                AppLog.e(TAG, "Failed to copy local $prefix for layout $newLayoutId", e)
                null
            }
        }
        return null
    }

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
        val masksDir = File(context.filesDir, "masks")
        if (!backgroundsDir.exists()) {
            backgroundsDir.mkdirs()
        }
        if (!masksDir.exists()) {
            masksDir.mkdirs()
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

            // Remap macro references in button actions and handle background/mask image copying/extraction
            val remappedLayouts =
                profile.layouts.map { layout ->
                    val oldLayoutId = layout.id
                    val newLayoutId = UUID.randomUUID().toString()

                    @Suppress("DEPRECATION")
                    val isLegacyMask = layout.useBackgroundImageAsMask

                    val newBgPath: String?
                    val newMaskPath: String?
                    val finalBgScale: Float
                    val finalBgOffsetX: Float
                    val finalBgOffsetY: Float
                    val finalBgDim: Float
                    val finalBgScaleMode: BackgroundScaleMode
                    val finalMaskScale: Float
                    val finalMaskOffsetX: Float
                    val finalMaskOffsetY: Float
                    val finalMaskDim: Float
                    val finalMaskScaleMode: BackgroundScaleMode

                    if (isLegacyMask) {
                        newMaskPath =
                            extractOrCopyMedia(
                                context,
                                images,
                                masksDir,
                                "masks",
                                "bg",
                                oldLayoutId,
                                newLayoutId,
                                layout.backgroundImagePath,
                                destPrefix = "mask",
                            ) ?: extractOrCopyMedia(
                                context,
                                images,
                                masksDir,
                                "masks",
                                "mask",
                                oldLayoutId,
                                newLayoutId,
                                layout.maskImagePath,
                                destPrefix = "mask",
                            )
                        newBgPath = null
                        finalBgScale = 1f
                        finalBgOffsetX = 0f
                        finalBgOffsetY = 0f
                        finalBgDim = 0f
                        finalBgScaleMode = BackgroundScaleMode.FILL
                        finalMaskScale = layout.bgImageScale
                        finalMaskOffsetX = layout.bgImageOffsetX
                        finalMaskOffsetY = layout.bgImageOffsetY
                        finalMaskDim = layout.backgroundImageDim
                        finalMaskScaleMode = layout.bgScaleMode
                    } else {
                        newBgPath =
                            extractOrCopyMedia(
                                context,
                                images,
                                backgroundsDir,
                                "backgrounds",
                                "bg",
                                oldLayoutId,
                                newLayoutId,
                                layout.backgroundImagePath,
                            )
                        newMaskPath =
                            extractOrCopyMedia(
                                context,
                                images,
                                masksDir,
                                "masks",
                                "mask",
                                oldLayoutId,
                                newLayoutId,
                                layout.maskImagePath,
                            )
                        finalBgScale = layout.bgImageScale
                        finalBgOffsetX = layout.bgImageOffsetX
                        finalBgOffsetY = layout.bgImageOffsetY
                        finalBgDim = layout.backgroundImageDim
                        finalBgScaleMode = layout.bgScaleMode
                        finalMaskScale = layout.maskImageScale
                        finalMaskOffsetX = layout.maskImageOffsetX
                        finalMaskOffsetY = layout.maskImageOffsetY
                        finalMaskDim = layout.maskImageDim
                        finalMaskScaleMode = layout.maskScaleMode
                    }

                    layout.copy(
                        id = newLayoutId,
                        backgroundImagePath = newBgPath,
                        useBackgroundImageAsMask = false,
                        bgImageScale = finalBgScale,
                        bgImageOffsetX = finalBgOffsetX,
                        bgImageOffsetY = finalBgOffsetY,
                        backgroundImageDim = finalBgDim,
                        bgScaleMode = finalBgScaleMode,
                        maskImagePath = newMaskPath,
                        maskImageScale = finalMaskScale,
                        maskImageOffsetX = finalMaskOffsetX,
                        maskImageOffsetY = finalMaskOffsetY,
                        maskImageDim = finalMaskDim,
                        maskScaleMode = finalMaskScaleMode,
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
                    val cleanKey = k.substringAfterLast("/")
                    if (cleanKey.startsWith("bg_") || cleanKey.startsWith("mask_")) {
                        cleanKey
                    } else {
                        "bg_$cleanKey"
                    }
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

                val rawPayload =
                    checksumJson.encodeToString(RawChecksumPayload(rawSettings, rawProfiles, imageHashes))
                val hex = HmacUtil.sha256Hex(rawPayload.toByteArray(Charsets.UTF_8)).lowercase()
                if ("sha256:$hex" == export.checksum) return true
            }.onFailure { e ->
                AppLog.w(TAG, "Raw JSON checksum verification failed: ${e.message}")
            }
        }

        return computeChecksum(export.settings, export.profiles, imageHashes) == export.checksum
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
