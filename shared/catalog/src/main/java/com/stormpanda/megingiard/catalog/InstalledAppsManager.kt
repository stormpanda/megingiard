package com.stormpanda.megingiard.catalog

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import android.view.Display
import androidx.annotation.VisibleForTesting
import androidx.core.util.AtomicFile
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.ipc.IpcSettingsParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.ipc.observeContentProvider
import com.stormpanda.megingiard.media.SteamGridDbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "InstalledAppsManager"
private const val FILE_FAVORITES = "gamefocus_favorites.txt"
private const val FILE_HIDDEN = "gamefocus_hidden.txt"
private const val FILE_LAST_USED = "gamefocus_last_used.txt"
private const val FILE_SCRAPED_APPS = "gamefocus_scraped_apps.txt"
private const val FILE_APP_NAMES = "gamefocus_app_names.json"
private const val FILE_COVER_IMAGE_IDS = "gamefocus_cover_image_ids.json"
internal const val DIR_COVERS = "gamefocus_covers"
private const val COVER_FILE_EXTENSION = ".png"
private const val ROM_PACKAGE_PREFIX = "rom."
private const val MAX_RECENT_APPS = 10
private const val INTENT_CATEGORY_GAME = "android.intent.category.GAME"
private const val INTENT_CATEGORY_APP_GAMES = "android.intent.category.APP_GAMES"
private const val THOR_SECONDARY_DISPLAY_FALLBACK_ID = 4

object InstalledAppsManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Dedicated scope for metadata writes so callers (e.g. UI click handlers) never block on disk I/O. */
    private val persistSupervisor = SupervisorJob()
    private val persistScope = CoroutineScope(Dispatchers.IO + persistSupervisor)

    /** Serializes AtomicFile writes; each write snapshots the latest in-memory state while holding the lock. */
    private val persistLock = Any()

    /** Custom display names keyed by package name (Android apps and `rom.*` pseudo packages). */
    private val customAppNames = mutableMapOf<String, String>()
    private val coverImageIds = mutableMapOf<String, Int>()

    private val installedAndroidAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> =
        combine(
            installedAndroidAppsFlow,
            RomManager.romApps,
        ) { androidApps, romApps ->
            (androidApps + romApps).sortedBy { it.label.lowercase() }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val scrapedPackages = HashSet<String>()

    @Volatile
    private var isScrapedPackagesLoaded = false

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _hiddenApps = MutableStateFlow<Set<String>>(emptySet())
    val hiddenApps: StateFlow<Set<String>> = _hiddenApps.asStateFlow()

    private val _lastUsed = MutableStateFlow<List<String>>(emptyList())
    val lastUsed: StateFlow<List<String>> = _lastUsed.asStateFlow()

    internal fun resetForTesting() {
        installedAndroidAppsFlow.value = emptyList()
        _favorites.value = emptySet()
        _hiddenApps.value = emptySet()
        _lastUsed.value = emptyList()
        synchronized(scrapedPackages) { scrapedPackages.clear() }
        synchronized(customAppNames) { customAppNames.clear() }
        synchronized(coverImageIds) { coverImageIds.clear() }
        isScrapedPackagesLoaded = false
        isSettingsObserverRegistered = false
    }

    private fun loadCustomAppNames(context: Context) {
        val file = File(context.filesDir, FILE_APP_NAMES)
        if (!file.exists()) return
        val atomicFile = AtomicFile(file)
        try {
            val text = atomicFile.readFully().toString(Charsets.UTF_8)
            val map = Json.decodeFromString<Map<String, String>>(text)
            synchronized(customAppNames) {
                customAppNames.clear()
                customAppNames.putAll(map)
            }
            AppLog.d(TAG, "Loaded ${map.size} custom app names from disk")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to load $FILE_APP_NAMES: ${e.message}")
        }
    }

    private fun loadCoverImageIds(context: Context) {
        val file = File(context.filesDir, FILE_COVER_IMAGE_IDS)
        if (!file.exists()) return
        val atomicFile = AtomicFile(file)
        try {
            val text = atomicFile.readFully().toString(Charsets.UTF_8)
            val map = Json.decodeFromString<Map<String, Int>>(text)
            synchronized(coverImageIds) {
                coverImageIds.clear()
                coverImageIds.putAll(map)
            }
            AppLog.d(TAG, "Loaded ${map.size} cover image IDs from disk")
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to load $FILE_COVER_IMAGE_IDS: ${e.message}")
        }
    }

    private fun saveCoverImageIds(context: Context) {
        synchronized(persistLock) {
            val content = synchronized(coverImageIds) { coverImageIds.toMap() }
            val file = File(context.filesDir, FILE_COVER_IMAGE_IDS)
            val atomicFile = AtomicFile(file)
            var fos: FileOutputStream? = null
            try {
                val text = Json.encodeToString(content)
                fos = atomicFile.startWrite()
                fos.write(text.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(fos)
                AppLog.d(TAG, "Saved cover image IDs to disk")
            } catch (e: IOException) {
                AppLog.w(TAG, "Failed to save $FILE_COVER_IMAGE_IDS: ${e.message}")
                if (fos != null) atomicFile.failWrite(fos)
            }
        }
    }

    fun getCoverImageId(packageName: String): Int? = synchronized(coverImageIds) { coverImageIds[packageName] }

    /** Updates the tracked SteamGridDB image ID in memory and persists it asynchronously. */
    fun setCoverImageId(
        context: Context,
        packageName: String,
        imageId: Int?,
    ) {
        synchronized(coverImageIds) {
            if (imageId != null) {
                coverImageIds[packageName] = imageId
            } else {
                coverImageIds.remove(packageName)
            }
        }
        persistScope.launch { saveCoverImageIds(context) }
    }

    /** Returns the user-defined display name for [packageName], or null if none is set. */
    internal fun getCustomLabel(packageName: String): String? = synchronized(customAppNames) { customAppNames[packageName] }

    private fun saveCustomAppNames(context: Context) {
        synchronized(persistLock) {
            val content = synchronized(customAppNames) { customAppNames.toMap() }
            val file = File(context.filesDir, FILE_APP_NAMES)
            val atomicFile = AtomicFile(file)
            var fos: FileOutputStream? = null
            try {
                val text = Json.encodeToString(content)
                fos = atomicFile.startWrite()
                fos.write(text.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(fos)
                AppLog.d(TAG, "Saved custom app names to disk")
            } catch (e: IOException) {
                AppLog.w(TAG, "Failed to save $FILE_APP_NAMES: ${e.message}")
                if (fos != null) atomicFile.failWrite(fos)
            }
        }
    }

    @VisibleForTesting
    internal suspend fun awaitPendingWritesForTesting() {
        persistSupervisor.children.toList().joinAll()
    }

    @VisibleForTesting
    internal fun reloadPersistedMetadataForTesting(context: Context) {
        loadCustomAppNames(context)
        loadCoverImageIds(context)
    }

    @VisibleForTesting
    internal fun setInstalledAndroidAppsForTesting(apps: List<InstalledAppInfo>) {
        installedAndroidAppsFlow.value = apps
    }

    @VisibleForTesting
    internal fun installedAndroidAppsForTesting(): List<InstalledAppInfo> = installedAndroidAppsFlow.value

    private fun loadStringList(
        context: Context,
        filename: String,
        limit: Int = Int.MAX_VALUE,
    ): List<String> {
        val file = File(context.filesDir, filename)
        if (!file.exists()) return emptyList()
        val atomicFile = AtomicFile(file)
        return try {
            val text = atomicFile.readFully().toString(Charsets.UTF_8)
            text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(limit)
                .toList()
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to load $filename: ${e.message}")
            emptyList()
        }
    }

    private fun loadStringSet(
        context: Context,
        filename: String,
    ): Set<String> = loadStringList(context, filename).toSet()

    private fun persistLines(
        context: Context,
        filename: String,
        lines: Iterable<String>,
    ) {
        val snapshot = lines.toList()
        persistScope.launch {
            synchronized(persistLock) {
                val file = File(context.filesDir, filename)
                val atomicFile = AtomicFile(file)
                var fos: FileOutputStream? = null
                try {
                    fos = atomicFile.startWrite()
                    fos.bufferedWriter(Charsets.UTF_8).use { writer ->
                        for (line in snapshot) {
                            writer.write(line)
                            writer.newLine()
                        }
                    }
                    atomicFile.finishWrite(fos)
                } catch (e: Exception) {
                    if (fos != null) {
                        atomicFile.failWrite(fos)
                    }
                    AppLog.e(TAG, "Failed to persist $filename: ${e.message}", e)
                }
            }
        }
    }

    private fun toggleInSet(
        context: Context,
        filename: String,
        stateFlow: MutableStateFlow<Set<String>>,
        item: String,
        label: String,
    ) {
        val current = stateFlow.value.toMutableSet()
        val added = !current.remove(item)
        if (added) current.add(item)
        AppLog.i(TAG, "${if (added) "Added" else "Removed"} $item ${if (added) "to" else "from"} $label")
        stateFlow.value = current
        persistLines(context, filename, current)
    }

    private fun loadFavorites(context: Context) {
        _favorites.value = loadStringSet(context, FILE_FAVORITES)
        AppLog.d(TAG, "Loaded ${_favorites.value.size} favorite apps from disk")
    }

    fun toggleFavorite(
        context: Context,
        packageName: String,
    ) {
        toggleInSet(context, FILE_FAVORITES, _favorites, packageName, "favorites")
    }

    private fun loadHidden(context: Context) {
        _hiddenApps.value = loadStringSet(context, FILE_HIDDEN)
        AppLog.d(TAG, "Loaded ${_hiddenApps.value.size} hidden apps from disk")
    }

    fun toggleHidden(
        context: Context,
        packageName: String,
    ) {
        toggleInSet(context, FILE_HIDDEN, _hiddenApps, packageName, "hidden apps")
    }

    private fun loadLastUsed(context: Context) {
        _lastUsed.value = loadStringList(context, FILE_LAST_USED, MAX_RECENT_APPS)
        AppLog.d(TAG, "Loaded ${_lastUsed.value.size} last used apps from disk")
    }

    fun recordAppLaunch(
        context: Context,
        packageName: String,
    ) {
        val list = _lastUsed.value.toMutableList()
        list.remove(packageName)
        list.add(0, packageName)
        val trimmed = list.take(MAX_RECENT_APPS)
        _lastUsed.value = trimmed
        persistLines(context, FILE_LAST_USED, trimmed)
        AppLog.i(TAG, "Recorded launch for $packageName (recent count=${trimmed.size})")
    }

    private fun loadScrapedPackages(context: Context): Set<String> =
        synchronized(scrapedPackages) {
            if (!isScrapedPackagesLoaded) {
                scrapedPackages.addAll(loadStringSet(context, FILE_SCRAPED_APPS))
                AppLog.d(TAG, "Loaded ${scrapedPackages.size} scraped package records from disk")
                isScrapedPackagesLoaded = true
            }
            scrapedPackages
        }

    fun markAppAsScraped(
        context: Context,
        packageName: String,
    ) {
        val needsWrite =
            synchronized(scrapedPackages) {
                loadScrapedPackages(context)
                scrapedPackages.add(packageName)
            }
        if (needsWrite) {
            persistLines(context, FILE_SCRAPED_APPS, synchronized(scrapedPackages) { scrapedPackages.toList() })
            AppLog.i(TAG, "Persisted $packageName to scraped packages registry")
        }
    }

    @Suppress("DEPRECATION")
    fun isPackageAGame(
        appInfo: ApplicationInfo,
        gamePackagesFromIntent: Set<String> = emptySet(),
    ): Boolean =
        gamePackagesFromIntent.contains(appInfo.packageName) ||
            appInfo.category == ApplicationInfo.CATEGORY_GAME ||
            (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    @Suppress("DEPRECATION")
    fun loadInstalledApps(context: Context) {
        SwitchEmulators.invalidateCache()
        scope.launch {
            // Custom names and cover image IDs must be loaded before the ROM scan, which reads both.
            loadCustomAppNames(context)
            loadCoverImageIds(context)

            RomManager.loadRomFolders(context)
            RomManager.reloadRomApps(context)

            SystemRoleClassifier.refreshLaunchers(context)

            loadFavorites(context)
            loadHidden(context)
            loadLastUsed(context)

            val packageManager = context.packageManager
            val mainIntent =
                Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

            val gameIntent =
                Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(INTENT_CATEGORY_GAME)
                }
            val appGamesIntent =
                Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(INTENT_CATEGORY_APP_GAMES)
                }

            val resolveInfoList: List<ResolveInfo> =
                packageManager.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0L),
                )

            val gameResolveList: List<ResolveInfo> =
                packageManager.queryIntentActivities(
                    gameIntent,
                    PackageManager.ResolveInfoFlags.of(0L),
                ) +
                    packageManager.queryIntentActivities(
                        appGamesIntent,
                        PackageManager.ResolveInfoFlags.of(0L),
                    )
            val gamePackagesFromIntent = gameResolveList.map { it.activityInfo.packageName }.toSet()

            val coversDir = File(context.cacheDir, DIR_COVERS).apply { mkdirs() }

            val apps =
                resolveInfoList
                    .filter { resolveInfo ->
                        resolveInfo.activityInfo.packageName != context.packageName
                    }.map { resolveInfo ->
                        val packageName = resolveInfo.activityInfo.packageName
                        val appInfo = resolveInfo.activityInfo.applicationInfo
                        val rawLabel =
                            appInfo
                                .loadLabel(packageManager)
                                .toString()
                        val defaultLabel = PackageAliasMapper.getTitleForPackage(packageName, rawLabel)
                        val label = synchronized(customAppNames) { customAppNames[packageName] } ?: defaultLabel
                        val activityName = resolveInfo.activityInfo.name
                        val isGame = isPackageAGame(appInfo, gamePackagesFromIntent)

                        val cachedCoverFile = File(coversDir, "$packageName$COVER_FILE_EXTENSION")
                        val hasCover = cachedCoverFile.exists() && cachedCoverFile.length() > 0
                        val coverPath = if (hasCover) cachedCoverFile.absolutePath else null
                        val coverLastModified = if (hasCover) cachedCoverFile.lastModified() else 0L
                        val coverImageId = if (hasCover) getCoverImageId(packageName) else null

                        InstalledAppInfo(
                            packageName = packageName,
                            activityName = activityName,
                            label = label,
                            coverPath = coverPath,
                            isGame = isGame,
                            coverLastModified = coverLastModified,
                            coverImageId = coverImageId,
                            defaultLabel = defaultLabel,
                        )
                    }.sortedBy { it.label.lowercase() }

            installedAndroidAppsFlow.value = apps
            val gameCount = apps.count { it.isGame }
            AppLog.d(TAG, "Loaded ${apps.size} installed apps ($gameCount games, ${apps.size - gameCount} apps) for launcher browser")

            // Trigger background SteamGridDB cover scraping if API key is configured
            triggerSteamGridDbScraping(context, coversDir)
        }
    }

    fun updateAppCover(
        packageName: String,
        coverPath: String?,
        coverImageId: Int? = null,
    ) {
        if (packageName.startsWith(ROM_PACKAGE_PREFIX)) {
            RomManager.updateRomCover(packageName, coverPath, coverImageId)
            return
        }
        installedAndroidAppsFlow.update { it.withUpdatedCover(packageName, coverPath, coverImageId) }
        AppLog.i(TAG, "Updated in-memory cover path for $packageName to $coverPath (imageId: $coverImageId)")
    }

    /**
     * Sets a custom display name for an Android app or ROM. The in-memory catalog updates
     * immediately; persistence to [FILE_APP_NAMES] happens asynchronously.
     */
    fun updateAppLabel(
        context: Context,
        packageName: String,
        newLabel: String,
    ) {
        synchronized(customAppNames) {
            customAppNames[packageName] = newLabel
        }
        persistScope.launch { saveCustomAppNames(context) }
        applyLabelInMemory(packageName, newLabel)
        AppLog.i(TAG, "Updated custom label for $packageName to '$newLabel'")
    }

    /**
     * Removes the custom display name for [packageName] and restores its [InstalledAppInfo.defaultLabel].
     *
     * @return the restored default label, or null if the package is not in the catalog.
     */
    fun resetAppLabel(
        context: Context,
        packageName: String,
    ): String? {
        val removed = synchronized(customAppNames) { customAppNames.remove(packageName) != null }
        if (removed) {
            persistScope.launch { saveCustomAppNames(context) }
        }
        val source = if (packageName.startsWith(ROM_PACKAGE_PREFIX)) RomManager.romApps.value else installedAndroidAppsFlow.value
        val defaultLabel = source.find { it.packageName == packageName }?.defaultLabel
        if (defaultLabel == null) {
            AppLog.w(TAG, "resetAppLabel: no default label known for $packageName")
            return null
        }
        applyLabelInMemory(packageName, defaultLabel)
        AppLog.i(TAG, "Reset label for $packageName to default '$defaultLabel' (customRemoved=$removed)")
        return defaultLabel
    }

    private fun applyLabelInMemory(
        packageName: String,
        newLabel: String,
    ) {
        if (packageName.startsWith(ROM_PACKAGE_PREFIX)) {
            RomManager.updateRomLabelInMemory(packageName, newLabel)
        } else {
            installedAndroidAppsFlow.update { it.withUpdatedLabel(packageName, newLabel) }
        }
    }

    /** Location of the cached cover image for [packageName]. */
    fun coverFileFor(
        context: Context,
        packageName: String,
    ): File = File(File(context.cacheDir, DIR_COVERS), "$packageName$COVER_FILE_EXTENSION")

    /** Writes [bytes] to [file] atomically so a failed write never leaves a truncated image behind. */
    private fun writeCoverAtomically(
        file: File,
        bytes: ByteArray,
    ): Boolean {
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        var fos: FileOutputStream? = null
        return try {
            fos = atomicFile.startWrite()
            fos.write(bytes)
            atomicFile.finishWrite(fos)
            true
        } catch (e: IOException) {
            if (fos != null) atomicFile.failWrite(fos)
            AppLog.e(TAG, "Failed to write cover ${file.absolutePath}: ${e.message}", e)
            false
        }
    }

    /**
     * Stores user-selected artwork for [packageName], records its SteamGridDB [imageId], publishes the new
     * cover to the catalog, and marks the package as scraped.
     *
     * @param onCoverWritten invoked after the file is written and before the catalog is updated
     *   (e.g. to invalidate palette caches so observers never read a stale palette).
     * @return the absolute cover path on success, or null if the file could not be written.
     */
    suspend fun applyCustomCover(
        context: Context,
        packageName: String,
        bytes: ByteArray,
        imageId: Int,
        onCoverWritten: () -> Unit = {},
    ): String? =
        withContext(Dispatchers.IO) {
            val file = coverFileFor(context, packageName)
            if (!writeCoverAtomically(file, bytes)) return@withContext null
            onCoverWritten()
            setCoverImageId(context, packageName, imageId)
            updateAppCover(packageName, file.absolutePath, imageId)
            markAppAsScraped(context, packageName)
            AppLog.i(TAG, "Applied custom cover for $packageName (imageId=$imageId)")
            file.absolutePath
        }

    /**
     * Deletes any cached cover for [packageName], clears its image ID, restores the default icon,
     * and marks the package as scraped so background scraping does not re-download artwork.
     *
     * @param onCoverRemoved invoked after the file is deleted and before the catalog is updated.
     */
    suspend fun revertToDefaultCover(
        context: Context,
        packageName: String,
        onCoverRemoved: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val file = coverFileFor(context, packageName)
        if (file.exists() && !file.delete()) {
            AppLog.w(TAG, "Failed to delete cover ${file.absolutePath}")
        }
        onCoverRemoved()
        setCoverImageId(context, packageName, null)
        updateAppCover(packageName, null, null)
        markAppAsScraped(context, packageName)
        AppLog.i(TAG, "Reverted $packageName to default icon")
    }

    private var isSettingsObserverRegistered = false

    private fun registerSettingsObserverIfNeeded(
        context: Context,
        coversDir: File,
    ) {
        if (isSettingsObserverRegistered) return
        isSettingsObserverRegistered = true
        MegingiardIpcContract.init(context)
        scope.launch {
            observeContentProvider(
                context,
                MegingiardIpcContract.SETTINGS_URI,
                IpcSettingsParser::parse,
            ).collect { config ->
                if (config.steamGridDbApiToken.isNotBlank()) {
                    AppLog.i(TAG, "SteamGridDB API key updated via IPC ContentObserver -> triggering cover scraping")
                    triggerSteamGridDbScraping(context, coversDir)
                }
            }
        }
    }

    private fun triggerSteamGridDbScraping(
        context: Context,
        coversDir: File,
    ) {
        registerSettingsObserverIfNeeded(context, coversDir)
        MegingiardIpcContract.init(context)

        val ipcConfig = IpcSettingsParser.parse(context.contentResolver)
        var apiKey = ipcConfig.steamGridDbApiToken
        if (apiKey.isBlank()) {
            AppLog.d(TAG, "SteamGridDB API key is blank locally and via IPC, skipping cover scraping")
            return
        }

        scope.launch {
            val scrapedSet = synchronized(scrapedPackages) { loadScrapedPackages(context).toSet() }
            val currentApps = installedApps.value
            val missingCovers =
                currentApps.filter { app ->
                    !scrapedSet.contains(app.packageName)
                }
            if (missingCovers.isEmpty()) {
                AppLog.d(TAG, "No un-scraped apps missing cover art or logo")
                return@launch
            }

            AppLog.i(TAG, "Starting background SteamGridDB cover/logo scraping for ${missingCovers.size} apps")

            missingCovers.forEach { app ->
                try {
                    val cleanedQuery = SteamGridDbClient.cleanSearchQuery(app.label)
                    AppLog.d(TAG, "Scraping cover art/logo for '${app.label}' (cleaned: '$cleanedQuery')")
                    val searchResult = SteamGridDbClient.searchGames(cleanedQuery, apiKey)
                    if (searchResult.isFailure) {
                        AppLog.w(TAG, "Network error searching SteamGridDB for ${app.label}, will retry next startup")
                        return@forEach
                    }

                    // Search request completed (HTTP success) -> mark as scraped
                    markAppAsScraped(context, app.packageName)

                    val games = searchResult.getOrNull()
                    val gameId = games?.firstOrNull()?.id ?: return@forEach

                    // 1. Scrape cover if missing
                    val coverFile = File(coversDir, "${app.packageName}.png")
                    val hasCover = coverFile.exists() && coverFile.length() > 0L
                    if (!hasCover) {
                        val imagesResult = SteamGridDbClient.fetchImages(gameId, "grids", apiKey)
                        val images = imagesResult.getOrNull()
                        val firstImage = images?.firstOrNull()
                        val imageUrl = firstImage?.url
                        if (imageUrl != null) {
                            val bytes = SteamGridDbClient.downloadImageBytes(imageUrl).getOrNull()
                            if (bytes != null && writeCoverAtomically(coverFile, bytes)) {
                                setCoverImageId(context, app.packageName, firstImage.id)
                                updateAppCover(app.packageName, coverFile.absolutePath, firstImage.id)
                                AppLog.i(TAG, "Successfully scraped SteamGridDB cover for ${app.label} (imageId=${firstImage.id})")
                            }
                        }
                    }

                    // 2. Scrape logo if ROM and logo is missing
                    if (app.isRom) {
                        val logosDir = File(context.cacheDir, "gamefocus_logos").apply { mkdirs() }
                        val logoFile = File(logosDir, "${app.packageName}.png")
                        val hasLogo = logoFile.exists() && logoFile.length() > 0L
                        if (!hasLogo) {
                            try {
                                val logosResult = SteamGridDbClient.fetchImages(gameId, "logos", apiKey)
                                val logos = logosResult.getOrNull()
                                val logoUrl = logos?.firstOrNull()?.url
                                if (logoUrl != null) {
                                    val logoBytes = SteamGridDbClient.downloadImageBytes(logoUrl).getOrNull()
                                    if (logoBytes != null) {
                                        logoFile.writeBytes(logoBytes)
                                        AppLog.i(TAG, "Successfully scraped SteamGridDB logo for ROM: ${app.label}")
                                    }
                                }
                            } catch (logoEx: Exception) {
                                AppLog.w(TAG, "Failed to scrape logo for ROM ${app.label}: ${logoEx.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to scrape for ${app.label}: ${e.message}")
                }
            }
        }
    }

    suspend fun launchAppOnPrimaryDisplay(
        context: Context,
        appInfo: InstalledAppInfo,
    ): Boolean = launchAppOnDisplay(context, appInfo, Display.DEFAULT_DISPLAY)

    suspend fun launchAppOnSecondaryDisplay(
        context: Context,
        appInfo: InstalledAppInfo,
    ): Boolean {
        val secondaryDisplay = DisplayDetector.findSecondaryDisplay(context)
        val displayId = secondaryDisplay?.displayId ?: THOR_SECONDARY_DISPLAY_FALLBACK_ID // Fallback to secondary display ID 4 on AYN Thor
        return launchAppOnDisplay(context, appInfo, displayId)
    }

    suspend fun launchAppOnDisplay(
        context: Context,
        appInfo: InstalledAppInfo,
        displayId: Int,
    ): Boolean {
        if (appInfo.isRom) {
            val systemId = appInfo.systemId
            if (systemId == null) {
                AppLog.e(TAG, "Cannot launch ROM '${appInfo.label}': systemId is null")
                return false
            }
            val romPath = appInfo.romPath
            if (romPath == null) {
                AppLog.e(TAG, "Cannot launch ROM '${appInfo.label}': romPath is null")
                return false
            }
            val systemDef = SUPPORTED_SYSTEMS.find { it.id == systemId }
            if (systemDef == null) {
                AppLog.e(TAG, "Cannot launch ROM '${appInfo.label}': unsupported systemId '$systemId'")
                return false
            }
            val launcher = RomLauncherRegistry.getLauncher(systemDef.emulatorId)
            if (launcher == null) {
                AppLog.e(TAG, "Cannot launch ROM '${appInfo.label}': no launcher registered for '${systemDef.emulatorId}'")
                return false
            }
            val targetCoreOrPackage = appInfo.emulatorPackage ?: appInfo.retroArchCore
            val success = launcher.launchGame(context, romPath, systemId, displayId, targetCoreOrPackage, appInfo.romUri)
            if (success) {
                recordAppLaunch(context, appInfo.packageName)
            }
            return success
        }

        return try {
            val intent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(appInfo.packageName, appInfo.activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(displayId)
                }
            context.startActivity(intent, options.toBundle())
            recordAppLaunch(context, appInfo.packageName)
            AppLog.i(TAG, "Successfully launched ${appInfo.label} (${appInfo.packageName}) on display $displayId")
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch app ${appInfo.label} on display $displayId: ${e.message}", e)
            false
        }
    }

    fun openAppInfo(
        context: Context,
        packageName: String,
    ) {
        try {
            val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            AppLog.i(TAG, "Opened native app info for package: $packageName")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to open native app info for package $packageName: ${e.message}", e)
        }
    }

    fun uninstallApp(
        context: Context,
        packageName: String,
    ) {
        try {
            val intent =
                Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            context.startActivity(intent)
            AppLog.i(TAG, "Launched uninstall intent for package: $packageName")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch uninstall intent for package $packageName: ${e.message}", e)
        }
    }
}
