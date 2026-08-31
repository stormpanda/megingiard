package com.stormpanda.megingiard.catalog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.rom.cleanRomName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.math.absoluteValue

@Serializable
data class CustomRomFolder(
    val uriString: String,
    val folderPath: String,
    val systemId: String,
    val systemName: String,
    val retroArchCore: String? = null,
)

sealed class AddRomFolderResult {
    data class Success(
        val folder: CustomRomFolder,
    ) : AddRomFolderResult()

    data class Error(
        val message: String,
    ) : AddRomFolderResult()
}

object RomManager {
    private const val TAG = "RomManager"
    private const val FILE_ROM_FOLDERS = "gamefocus_rom_folders.json"
    private const val FILE_ROM_CLEANED_NAMES = "gamefocus_rom_names.json"
    private const val MAX_ZIP_PEEKS = 10

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _romFolders = MutableStateFlow<List<CustomRomFolder>>(emptyList())
    val romFolders: StateFlow<List<CustomRomFolder>> = _romFolders.asStateFlow()

    private val _romApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val romApps: StateFlow<List<InstalledAppInfo>> = _romApps.asStateFlow()

    private val _romCleanedNames = mutableMapOf<String, String>()

    private inline fun <reified T> loadJsonFile(
        context: Context,
        filename: String,
    ): T? {
        val file = File(context.filesDir, filename)
        if (!file.exists()) return null
        return try {
            Json.decodeFromString<T>(file.readText())
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to load $filename: ${e.message}")
            null
        }
    }

    private inline fun <reified T> saveJsonFile(
        context: Context,
        filename: String,
        value: T,
    ) {
        try {
            val file = File(context.filesDir, filename)
            file.writeText(Json.encodeToString(value))
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to save $filename: ${e.message}", e)
        }
    }

    fun loadRomFolders(context: Context) {
        loadJsonFile<List<CustomRomFolder>>(context, FILE_ROM_FOLDERS)?.let { folders ->
            _romFolders.value = folders
            AppLog.d(TAG, "Loaded ${folders.size} ROM folders from disk")
        }

        loadJsonFile<Map<String, String>>(context, FILE_ROM_CLEANED_NAMES)?.let { map ->
            synchronized(_romCleanedNames) {
                _romCleanedNames.clear()
                _romCleanedNames.putAll(map)
            }
            AppLog.d(TAG, "Loaded ${map.size} cleaned ROM names from disk")
        }
    }

    private fun saveRomCleanedNames(context: Context) {
        val content = synchronized(_romCleanedNames) { _romCleanedNames.toMap() }
        saveJsonFile(context, FILE_ROM_CLEANED_NAMES, content)
        AppLog.d(TAG, "Saved cleaned ROM names to disk")
    }

    private fun saveRomFolders(
        context: Context,
        folders: List<CustomRomFolder>,
    ) {
        _romFolders.value = folders
        saveJsonFile(context, FILE_ROM_FOLDERS, folders)
        AppLog.d(TAG, "Saved ${folders.size} ROM folders to disk")
    }

    private fun resolveDocumentFile(
        context: Context,
        uri: Uri,
    ): DocumentFile? {
        if (uri.scheme == "file" || (uri.scheme == null && uri.path?.startsWith("/") == true)) {
            val path = uri.path ?: uri.toString().removePrefix("file://")
            val file = File(path)
            if (file.exists()) return DocumentFile.fromFile(file)
        }
        return try {
            DocumentFile.fromTreeUri(context, uri)
        } catch (e: Exception) {
            val path = uri.path ?: uri.toString().removePrefix("file://")
            val file = File(path)
            if (file.exists()) DocumentFile.fromFile(file) else null
        }
    }

    suspend fun addRomFolder(
        context: Context,
        uri: Uri,
    ): AddRomFolderResult =
        withContext(Dispatchers.IO) {
            // 1. Take persistable URI permission
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to take persistable URI permission: ${e.message}")
            }

            // 2. Scan files to recognize system
            val documentFile = resolveDocumentFile(context, uri)
            if (documentFile == null || !documentFile.exists()) {
                AppLog.w(TAG, "Selected document tree does not exist")
                return@withContext AddRomFolderResult.Error("Folder does not exist or is inaccessible.")
            }

            val files = documentFile.listFiles()
            val systemId = detectSystem(context, files)
            if (systemId == null) {
                AppLog.w(TAG, "Could not automatically recognize any gaming system in folder")
                return@withContext AddRomFolderResult.Error("Could not automatically recognize any gaming system in this folder.")
            }

            val systemDef = SUPPORTED_SYSTEMS.find { it.id == systemId }!!
            val folderPath = documentFile.name ?: uri.path ?: "ROM Folder"

            // 3. Prevent duplicate folders
            val current = _romFolders.value.toMutableList()
            if (current.any { it.uriString == uri.toString() }) {
                return@withContext AddRomFolderResult.Error("This folder has already been added.")
            }

            val newFolder =
                CustomRomFolder(
                    uriString = uri.toString(),
                    folderPath = folderPath,
                    systemId = systemId,
                    systemName = systemDef.displayName,
                )
            current.add(newFolder)
            saveRomFolders(context, current)

            // 4. Reload ROM apps
            reloadRomAppsSuspend(context)
            AddRomFolderResult.Success(newFolder)
        }

    fun updateRomFolderCore(
        context: Context,
        folderUri: String,
        coreName: String?,
    ) {
        val current = _romFolders.value.map { if (it.uriString == folderUri) it.copy(retroArchCore = coreName) else it }
        saveRomFolders(context, current)
        reloadRomApps(context)
    }

    fun removeRomFolder(
        context: Context,
        folder: CustomRomFolder,
    ) {
        val current = _romFolders.value.toMutableList()
        current.removeAll { it.uriString == folder.uriString }
        saveRomFolders(context, current)

        val namesChanged =
            synchronized(_romCleanedNames) {
                val keysToRemove =
                    _romCleanedNames.keys.filter { romUriStr ->
                        romUriStr.startsWith(folder.uriString) || romUriStr.contains(folder.uriString)
                    }
                if (keysToRemove.isNotEmpty()) {
                    keysToRemove.forEach { _romCleanedNames.remove(it) }
                    true
                } else {
                    false
                }
            }
        if (namesChanged) {
            saveRomCleanedNames(context)
        }

        // Release persistable URI permission if possible
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: Exception) {
            // Ignore
        }

        reloadRomApps(context)
    }

    fun reloadRomApps(context: Context) {
        scope.launch {
            reloadRomAppsSuspend(context)
        }
    }

    suspend fun reloadRomAppsSuspend(context: Context) =
        withContext(Dispatchers.IO) {
            val coversDir = File(context.cacheDir, "gamefocus_covers").apply { mkdirs() }
            var namesChanged = false
            val allRomApps =
                buildList {
                    for (folder in _romFolders.value) {
                        val uri = Uri.parse(folder.uriString)
                        val documentFile = resolveDocumentFile(context, uri) ?: continue
                        if (!documentFile.exists()) continue

                        val systemDef = SUPPORTED_SYSTEMS.find { it.id == folder.systemId } ?: continue

                        val files = documentFile.listFiles()
                        val isConsoleSystem = folder.systemId != "pc"
                        for (file in files) {
                            if (file.isDirectory) continue
                            val name = file.name ?: continue
                            val ext = name.substringAfterLast('.', "").lowercase()
                            val isMatch = systemDef.extensions.contains(ext) || (isConsoleSystem && ext == "zip")
                            if (isMatch) {
                                val rawLabel = name.substringBeforeLast('.')
                                val romUriStr = file.uri.toString()
                                val romPath = SafPathResolver.resolveFilePath(romUriStr) ?: romUriStr

                                val label =
                                    synchronized(_romCleanedNames) {
                                        _romCleanedNames.getOrPut(romUriStr) {
                                            namesChanged = true
                                            cleanRomName(rawLabel)
                                        }
                                    }

                                val pseudoPackageName =
                                    "rom.${folder.systemId}." +
                                        rawLabel.replace(Regex("[^a-zA-Z0-9_]"), "_") +
                                        "_" + romUriStr.hashCode().absoluteValue

                                val cachedCoverFile = File(coversDir, "$pseudoPackageName.png")
                                val hasCover = cachedCoverFile.exists() && cachedCoverFile.length() > 0
                                val coverPath = if (hasCover) cachedCoverFile.absolutePath else null
                                val coverLastModified = if (hasCover) cachedCoverFile.lastModified() else 0L

                                add(
                                    InstalledAppInfo(
                                        packageName = pseudoPackageName,
                                        activityName = "",
                                        label = label,
                                        coverPath = coverPath,
                                        isGame = true,
                                        isRom = true,
                                        romPath = romPath,
                                        systemId = folder.systemId,
                                        retroArchCore = folder.retroArchCore,
                                        coverLastModified = coverLastModified,
                                    ),
                                )
                            }
                        }
                    }
                }

            _romApps.value = allRomApps.sortedBy { it.label.lowercase() }
            AppLog.d(TAG, "Scanned and loaded ${_romApps.value.size} ROMs across ${_romFolders.value.size} folders")
            if (namesChanged) {
                saveRomCleanedNames(context)
            }
        }

    fun updateRomCover(
        packageName: String,
        coverPath: String?,
    ) {
        _romApps.value = _romApps.value.withUpdatedCover(packageName, coverPath)
        AppLog.i(TAG, "Updated in-memory ROM cover path for $packageName to $coverPath")
    }

    internal fun detectSystem(
        context: Context,
        files: Array<DocumentFile>,
    ): String? {
        val extensionCounts = mutableMapOf<String, Int>()
        var zipPeeks = 0
        for (file in files) {
            val name = file.name ?: continue
            var ext = name.substringAfterLast('.', "").lowercase()
            if (ext == "zip" && zipPeeks < MAX_ZIP_PEEKS) {
                zipPeeks++
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { fis ->
                        ZipInputStream(fis).use { zis ->
                            val entry = zis.nextEntry
                            if (entry != null) {
                                val innerExt = entry.name.substringAfterLast('.', "").lowercase()
                                if (innerExt.isNotEmpty()) {
                                    ext = innerExt
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to peek inside zip file $name: ${e.message}")
                }
            }
            if (ext.isNotEmpty()) {
                extensionCounts[ext] = (extensionCounts[ext] ?: 0) + 1
            }
        }

        return SUPPORTED_SYSTEMS
            .map { system -> system.id to system.extensions.sumOf { ext -> extensionCounts[ext] ?: 0 } }
            .filter { (_, count) -> count > 0 }
            .maxByOrNull { (_, count) -> count }
            ?.first
    }
}
