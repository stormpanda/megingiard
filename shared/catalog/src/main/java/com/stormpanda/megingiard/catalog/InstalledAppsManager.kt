package com.stormpanda.megingiard.catalog

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import android.view.Display
import androidx.annotation.VisibleForTesting
import androidx.core.util.AtomicFile
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.media.SteamGridDbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "InstalledAppsManager"
private const val FILE_FAVORITES = "gamefocus_favorites.txt"
private const val FILE_HIDDEN = "gamefocus_hidden.txt"
private const val FILE_LAST_USED = "gamefocus_last_used.txt"
private const val FILE_ARTWORK_OPTOUTS = "gamefocus_artwork_optouts.json"
private const val FILE_SCRAPE_ATTEMPTS = "gamefocus_scrape_attempts.json"
private const val FILE_APP_NAMES = "gamefocus_app_names.json"
private const val FILE_COVER_IMAGE_IDS = "gamefocus_cover_image_ids.json"
private const val FILE_LIBRARY_ICON_IDS = "gamefocus_library_icon_ids.json"
internal const val DIR_COVERS = "gamefocus_covers"
internal const val DIR_LOGOS = "gamefocus_logos"
internal const val DIR_LIBRARY_ICONS = "gamefocus_library_icons"
internal const val MAX_LIBRARY_ICON_DIMENSION_PX = 256
private const val COVER_FILE_EXTENSION = ".png"
private const val IMAGE_TYPE_GRIDS = "grids"
private const val IMAGE_TYPE_LOGOS = "logos"
private const val IMAGE_TYPE_ICONS = "icons"
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

    /** Guards all in-memory metadata maps, sets, and load-once state. */
    private val stateLock = Any()

    /** Serializes AtomicFile writes; each write snapshots the latest in-memory state while holding [stateLock]. */
    private val persistLock = Any()

    /** Load-once guard (AGENTS.md §7.6): later reloads must never overwrite unsaved in-memory changes. */
    @Volatile
    private var isMetadataLoaded = false

    /** Custom display names keyed by package name (Android apps and `rom.*` pseudo packages). */
    private val customAppNames = mutableMapOf<String, String>()
    private val coverImageIds = mutableMapOf<String, Int>()
    private val libraryIconRefs = mutableMapOf<String, LibraryIconRef>()
    private var artworkOptOuts = ArtworkOptOuts()
    private var scrapeAttempts = ScrapeAttempts()

    private val installedAndroidAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> =
        combine(
            installedAndroidAppsFlow,
            RomManager.romApps,
        ) { androidApps, romApps ->
            (androidApps + romApps).sortedBy { it.label.lowercase() }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Conflated channel so concurrent scrape triggers coalesce into at most one follow-up pass. */
    private val scrapeRequests = Channel<Context>(Channel.CONFLATED)

    init {
        scope.launch {
            for (context in scrapeRequests) {
                executeScrapingPass(context)
            }
        }
    }

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _hiddenApps = MutableStateFlow<Set<String>>(emptySet())
    val hiddenApps: StateFlow<Set<String>> = _hiddenApps.asStateFlow()

    private val _lastUsed = MutableStateFlow<List<String>>(emptyList())
    val lastUsed: StateFlow<List<String>> = _lastUsed.asStateFlow()

    private var loadAppsJob: Job? = null

    internal fun resetForTesting() {
        loadAppsJob?.cancel()
        loadAppsJob = null
        installedAndroidAppsFlow.value = emptyList()
        synchronized(stateLock) {
            _favorites.value = emptySet()
            _hiddenApps.value = emptySet()
            _lastUsed.value = emptyList()
            artworkOptOuts = ArtworkOptOuts()
            scrapeAttempts = ScrapeAttempts()
            customAppNames.clear()
            coverImageIds.clear()
            libraryIconRefs.clear()
            isMetadataLoaded = false
        }
        latestSteamGridDbApiKey = ""
    }

    private fun readAtomicText(
        context: Context,
        filename: String,
    ): String? {
        val file = File(context.filesDir, filename)
        if (!file.exists()) return null
        return try {
            AtomicFile(file).readFully().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to load $filename: ${e.message}")
            null
        }
    }

    private fun persistAtomicText(
        context: Context,
        filename: String,
        snapshotProvider: () -> String,
    ) {
        persistScope.launch {
            synchronized(persistLock) {
                val text = synchronized(stateLock) { snapshotProvider() }
                val file = File(context.filesDir, filename)
                val atomicFile = AtomicFile(file)
                var fos: FileOutputStream? = null
                try {
                    fos = atomicFile.startWrite()
                    fos.write(text.toByteArray(Charsets.UTF_8))
                    atomicFile.finishWrite(fos)
                    AppLog.d(TAG, "Saved $filename to disk")
                } catch (e: Exception) {
                    if (fos != null) atomicFile.failWrite(fos)
                    AppLog.w(TAG, "Failed to save $filename: ${e.message}")
                }
            }
        }
    }

    private fun loadStringList(
        context: Context,
        filename: String,
        limit: Int = Int.MAX_VALUE,
    ): List<String> =
        readAtomicText(context, filename)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(limit)
            ?.toList()
            ?: emptyList()

    private fun ensureMetadataLoaded(context: Context) {
        synchronized(stateLock) {
            if (isMetadataLoaded) return
            isMetadataLoaded = true
            readAtomicText(context, FILE_APP_NAMES)?.let { text ->
                runCatching { Json.decodeFromString<Map<String, String>>(text) }
                    .onSuccess { customAppNames.putAll(it) }
                    .onFailure { AppLog.w(TAG, "Failed to decode $FILE_APP_NAMES: ${it.message}") }
            }
            readAtomicText(context, FILE_COVER_IMAGE_IDS)?.let { text ->
                runCatching { Json.decodeFromString<Map<String, Int>>(text) }
                    .onSuccess { coverImageIds.putAll(it) }
                    .onFailure { AppLog.w(TAG, "Failed to decode $FILE_COVER_IMAGE_IDS: ${it.message}") }
            }
            readAtomicText(context, FILE_LIBRARY_ICON_IDS)?.let { text ->
                runCatching { LibraryIconRef.parseMap(text) }
                    .onSuccess { libraryIconRefs.putAll(it) }
                    .onFailure { AppLog.w(TAG, "Failed to decode $FILE_LIBRARY_ICON_IDS: ${it.message}") }
            }
            readAtomicText(context, FILE_ARTWORK_OPTOUTS)?.let { text ->
                runCatching { Json.decodeFromString<ArtworkOptOuts>(text) }
                    .onSuccess { artworkOptOuts = it }
                    .onFailure { AppLog.w(TAG, "Failed to decode $FILE_ARTWORK_OPTOUTS: ${it.message}") }
            }
            readAtomicText(context, FILE_SCRAPE_ATTEMPTS)?.let { text ->
                runCatching { Json.decodeFromString<ScrapeAttempts>(text) }
                    .onSuccess { scrapeAttempts = it }
                    .onFailure { AppLog.w(TAG, "Failed to decode $FILE_SCRAPE_ATTEMPTS: ${it.message}") }
            }
            _favorites.value = loadStringList(context, FILE_FAVORITES).toSet()
            _hiddenApps.value = loadStringList(context, FILE_HIDDEN).toSet()
            _lastUsed.value = loadStringList(context, FILE_LAST_USED, MAX_RECENT_APPS)
            AppLog.d(
                TAG,
                "Loaded persisted metadata (names=${customAppNames.size}, covers=${coverImageIds.size}, " +
                    "icons=${libraryIconRefs.size}, favorites=${_favorites.value.size}, hidden=${_hiddenApps.value.size})",
            )
        }
    }

    fun getCoverImageId(packageName: String): Int? = synchronized(stateLock) { coverImageIds[packageName] }

    /** Updates the tracked SteamGridDB image ID in memory and persists it asynchronously. */
    fun setCoverImageId(
        context: Context,
        packageName: String,
        imageId: Int?,
    ) {
        ensureMetadataLoaded(context)
        synchronized(stateLock) {
            if (imageId != null) {
                coverImageIds[packageName] = imageId
            } else {
                coverImageIds.remove(packageName)
            }
        }
        persistAtomicText(context, FILE_COVER_IMAGE_IDS) { Json.encodeToString(coverImageIds.toMap()) }
    }

    fun getLibraryIconRef(packageName: String): LibraryIconRef? = synchronized(stateLock) { libraryIconRefs[packageName] }

    fun setLibraryIconRef(
        context: Context,
        packageName: String,
        ref: LibraryIconRef?,
    ) {
        ensureMetadataLoaded(context)
        synchronized(stateLock) {
            if (ref != null) {
                libraryIconRefs[packageName] = ref
            } else {
                libraryIconRefs.remove(packageName)
            }
        }
        persistAtomicText(context, FILE_LIBRARY_ICON_IDS) { LibraryIconRef.serializeMap(libraryIconRefs.toMap()) }
    }

    /** Returns the user-defined display name for [packageName], or null if none is set. */
    internal fun getCustomLabel(packageName: String): String? = synchronized(stateLock) { customAppNames[packageName] }

    @VisibleForTesting
    internal suspend fun awaitPendingWritesForTesting() {
        persistSupervisor.children.toList().joinAll()
    }

    @VisibleForTesting
    internal fun reloadPersistedMetadataForTesting(context: Context) {
        ensureMetadataLoaded(context)
    }

    @VisibleForTesting
    internal fun setInstalledAndroidAppsForTesting(apps: List<InstalledAppInfo>) {
        installedAndroidAppsFlow.value = apps
    }

    @VisibleForTesting
    internal fun installedAndroidAppsForTesting(): List<InstalledAppInfo> = installedAndroidAppsFlow.value

    private fun toggleInSet(
        context: Context,
        filename: String,
        stateFlow: MutableStateFlow<Set<String>>,
        item: String,
        label: String,
    ) {
        ensureMetadataLoaded(context)
        synchronized(stateLock) {
            val current = stateFlow.value.toMutableSet()
            val added = !current.remove(item)
            if (added) current.add(item)
            AppLog.i(TAG, "${if (added) "Added" else "Removed"} $item ${if (added) "to" else "from"} $label")
            stateFlow.value = current.toSet()
        }
        persistAtomicText(context, filename) { stateFlow.value.joinToString(separator = "\n", postfix = "\n") }
    }

    fun toggleFavorite(
        context: Context,
        packageName: String,
    ) {
        toggleInSet(context, FILE_FAVORITES, _favorites, packageName, "favorites")
    }

    fun toggleHidden(
        context: Context,
        packageName: String,
    ) {
        toggleInSet(context, FILE_HIDDEN, _hiddenApps, packageName, "hidden apps")
    }

    fun recordAppLaunch(
        context: Context,
        packageName: String,
    ) {
        ensureMetadataLoaded(context)
        val trimmed =
            synchronized(stateLock) {
                val list = _lastUsed.value.toMutableList()
                list.remove(packageName)
                list.add(0, packageName)
                val updated = list.take(MAX_RECENT_APPS)
                _lastUsed.value = updated
                updated
            }
        persistAtomicText(context, FILE_LAST_USED) { _lastUsed.value.joinToString(separator = "\n", postfix = "\n") }
        AppLog.i(TAG, "Recorded launch for $packageName (recent count=${trimmed.size})")
    }

    fun wasAttempted(
        packageName: String,
        kind: ArtworkKind,
    ): Boolean = synchronized(stateLock) { scrapeAttempts.wasAttempted(kind, packageName) }

    fun recordScrapeAttempt(
        context: Context,
        packageName: String,
        kind: ArtworkKind,
    ) {
        ensureMetadataLoaded(context)
        val modified =
            synchronized(stateLock) {
                val updated = scrapeAttempts.withAttempt(kind, packageName)
                val changed = updated !== scrapeAttempts
                scrapeAttempts = updated
                changed
            }
        if (modified) {
            AppLog.d(TAG, "Recorded $kind scrape attempt for $packageName")
            persistAtomicText(context, FILE_SCRAPE_ATTEMPTS) { Json.encodeToString(scrapeAttempts) }
        }
    }

    fun isCoverOptedOut(packageName: String): Boolean =
        synchronized(stateLock) { artworkOptOuts.isOptedOut(ArtworkKind.COVER, packageName) }

    fun isLibraryIconOptedOut(packageName: String): Boolean =
        synchronized(stateLock) { artworkOptOuts.isOptedOut(ArtworkKind.LIBRARY_ICON, packageName) }

    fun setCoverOptedOut(
        context: Context,
        packageName: String,
        optedOut: Boolean,
    ) {
        setArtworkOptOut(context, ArtworkKind.COVER, packageName, optedOut)
        refreshAppMetadata(context, packageName)
    }

    fun setLibraryIconOptedOut(
        context: Context,
        packageName: String,
        optedOut: Boolean,
    ) {
        setArtworkOptOut(context, ArtworkKind.LIBRARY_ICON, packageName, optedOut)
        refreshAppMetadata(context, packageName)
    }

    private fun setArtworkOptOut(
        context: Context,
        kind: ArtworkKind,
        packageName: String,
        optedOut: Boolean,
    ) {
        ensureMetadataLoaded(context)
        synchronized(stateLock) {
            artworkOptOuts = artworkOptOuts.withOptOut(kind, packageName, optedOut)
        }
        AppLog.i(TAG, "Set $kind opt-out for $packageName to $optedOut")
        persistAtomicText(context, FILE_ARTWORK_OPTOUTS) { Json.encodeToString(artworkOptOuts) }
    }

    /**
     * Populates a raw [InstalledAppInfo] with current in-memory metadata (custom label, cover ID,
     * library icon reference, artwork opt-outs) and disk-cached artwork timestamps/paths.
     */
    internal fun enrichAppInfo(
        rawApp: InstalledAppInfo,
        context: Context,
    ): InstalledAppInfo {
        val packageName = rawApp.packageName
        val defaultLabel = rawApp.defaultLabel ?: rawApp.label
        val label = getCustomLabel(packageName) ?: defaultLabel

        val cachedCoverFile = coverFileFor(context, packageName)
        val hasCover = cachedCoverFile.exists() && cachedCoverFile.length() > 0L
        val coverImageId = if (hasCover) getCoverImageId(packageName) else null

        val cachedLibraryIconFile = libraryIconFileFor(context, packageName)
        val hasLibraryIcon = cachedLibraryIconFile.exists() && cachedLibraryIconFile.length() > 0L
        val libraryIconRef = if (hasLibraryIcon) getLibraryIconRef(packageName) else null

        val cachedLogoFile = logoFileFor(context, packageName)
        val hasLogo = cachedLogoFile.exists() && cachedLogoFile.length() > 0L

        return rawApp.copy(
            label = label,
            defaultLabel = defaultLabel,
            coverPath = if (hasCover) cachedCoverFile.absolutePath else null,
            coverLastModified = if (hasCover) cachedCoverFile.lastModified() else 0L,
            coverImageId = coverImageId,
            libraryIconPath = if (hasLibraryIcon) cachedLibraryIconFile.absolutePath else null,
            libraryIconImageId = libraryIconRef?.id,
            libraryIconType = libraryIconRef?.type,
            libraryIconLastModified = if (hasLibraryIcon) cachedLibraryIconFile.lastModified() else 0L,
            coverOptedOut = isCoverOptedOut(packageName),
            libraryIconOptedOut = isLibraryIconOptedOut(packageName),
            logoLastModified = if (hasLogo) cachedLogoFile.lastModified() else 0L,
        )
    }

    /**
     * Re-applies [enrichAppInfo] to the in-memory catalog entry for [packageName] (Android app or ROM).
     */
    internal fun refreshAppMetadata(
        context: Context,
        packageName: String,
    ) {
        if (packageName.startsWith(ROM_PACKAGE_PREFIX)) {
            RomManager.updateRomApp(packageName) { enrichAppInfo(it, context) }
        } else {
            installedAndroidAppsFlow.update { apps ->
                apps.map { if (it.packageName == packageName) enrichAppInfo(it, context) else it }
            }
        }
        AppLog.d(TAG, "Refreshed in-memory metadata for $packageName")
    }

    /** Location of the cached logo image for ROM [packageName]. */
    fun logoFileFor(
        context: Context,
        packageName: String,
    ): File = File(File(context.cacheDir, DIR_LOGOS), "$packageName$COVER_FILE_EXTENSION")

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
        loadAppsJob?.cancel()
        loadAppsJob =
            scope.launch {
                // Metadata must be loaded before the ROM scan, which reads custom names, cover IDs, and icon refs.
                ensureMetadataLoaded(context)

                RomManager.loadRomFolders(context)
                // Scraping is triggered once below, after the Android app list is published.
                RomManager.reloadRomAppsSuspend(context, triggerScrape = false)

                SystemRoleClassifier.refreshLaunchers(context)

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

                File(context.cacheDir, DIR_COVERS).mkdirs()

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
                            val activityName = resolveInfo.activityInfo.name
                            val isGame = isPackageAGame(appInfo, gamePackagesFromIntent)

                            enrichAppInfo(
                                InstalledAppInfo(
                                    packageName = packageName,
                                    activityName = activityName,
                                    label = defaultLabel,
                                    isGame = isGame,
                                    defaultLabel = defaultLabel,
                                ),
                                context,
                            )
                        }.sortedBy { it.label.lowercase() }

                installedAndroidAppsFlow.value = apps
                val gameCount = apps.count { it.isGame }
                AppLog.d(TAG, "Loaded ${apps.size} installed apps ($gameCount games, ${apps.size - gameCount} apps) for launcher browser")

                // Trigger background SteamGridDB cover scraping if API key is configured
                triggerSteamGridDbScraping(context)
            }
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
        ensureMetadataLoaded(context)
        synchronized(stateLock) {
            customAppNames[packageName] = newLabel
        }
        persistAtomicText(context, FILE_APP_NAMES) { Json.encodeToString(customAppNames.toMap()) }
        refreshAppMetadata(context, packageName)
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
        ensureMetadataLoaded(context)
        val removed = synchronized(stateLock) { customAppNames.remove(packageName) != null }
        if (removed) {
            persistAtomicText(context, FILE_APP_NAMES) { Json.encodeToString(customAppNames.toMap()) }
        }
        val source = if (packageName.startsWith(ROM_PACKAGE_PREFIX)) RomManager.romApps.value else installedAndroidAppsFlow.value
        val defaultLabel = source.find { it.packageName == packageName }?.defaultLabel
        if (defaultLabel == null) {
            AppLog.w(TAG, "resetAppLabel: no default label known for $packageName")
            return null
        }
        refreshAppMetadata(context, packageName)
        AppLog.i(TAG, "Reset label for $packageName to default '$defaultLabel' (customRemoved=$removed)")
        return defaultLabel
    }

    /** Location of the cached cover image for [packageName]. */
    fun coverFileFor(
        context: Context,
        packageName: String,
    ): File = File(File(context.cacheDir, DIR_COVERS), "$packageName$COVER_FILE_EXTENSION")

    /** Writes [bytes] to [file] atomically so a failed write never leaves a truncated image behind. */
    internal fun writeImageAtomically(
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
        } catch (e: Exception) {
            if (fos != null) atomicFile.failWrite(fos)
            AppLog.e(TAG, "Failed to write image ${file.absolutePath}: ${e.message}", e)
            false
        }
    }

    /**
     * Stores user-selected artwork for [packageName], records its SteamGridDB [imageId], publishes the new
     * cover to the catalog, clears opt-out, and records the scrape attempt.
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
            if (!writeImageAtomically(file, bytes)) return@withContext null
            onCoverWritten()
            setArtworkOptOut(context, ArtworkKind.COVER, packageName, false)
            recordScrapeAttempt(context, packageName, ArtworkKind.COVER)
            setCoverImageId(context, packageName, imageId)
            refreshAppMetadata(context, packageName)
            AppLog.i(TAG, "Applied custom cover for $packageName (imageId=$imageId)")
            file.absolutePath
        }

    /**
     * Deletes any cached cover for [packageName], clears its image ID, restores the default icon,
     * marks cover as opted out, and records the attempt.
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
        setArtworkOptOut(context, ArtworkKind.COVER, packageName, true)
        recordScrapeAttempt(context, packageName, ArtworkKind.COVER)
        setCoverImageId(context, packageName, null)
        refreshAppMetadata(context, packageName)
        AppLog.i(TAG, "Reverted $packageName to default icon")
    }

    /**
     * Normalizes downloaded icon [bytes] for disk storage: unpacks Windows ICO containers via
     * [IcoImageExtractor] and downsamples images larger than [maxDimensionPx] to PNG.
     *
     * @return bytes safe to persist, or `null` if the payload cannot be decoded as an image
     *   (callers must not write anything to disk in that case).
     */
    internal fun downsampleIconBytes(
        bytes: ByteArray,
        maxDimensionPx: Int = MAX_LIBRARY_ICON_DIMENSION_PX,
    ): ByteArray? {
        val inputBytes =
            if (IcoImageExtractor.isIco(bytes)) {
                IcoImageExtractor.extractBestImage(bytes) ?: bytes
            } else {
                bytes
            }

        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, boundsOptions)
            val rawW = boundsOptions.outWidth
            val rawH = boundsOptions.outHeight
            if (rawW <= 0 || rawH <= 0) {
                AppLog.w(TAG, "downsampleIconBytes: undecodable image bytes (${bytes.size} bytes)")
                return null
            }

            if (rawW <= maxDimensionPx && rawH <= maxDimensionPx) {
                return inputBytes
            }

            var inSampleSize = 1
            val halfH = rawH / 2
            val halfW = rawW / 2
            while ((halfH / inSampleSize) >= maxDimensionPx && (halfW / inSampleSize) >= maxDimensionPx) {
                inSampleSize *= 2
            }

            val decodeOptions =
                BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            val decoded = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size, decodeOptions) ?: return null

            val finalBitmap =
                if (decoded.width > maxDimensionPx || decoded.height > maxDimensionPx) {
                    val scale = minOf(maxDimensionPx.toFloat() / decoded.width, maxDimensionPx.toFloat() / decoded.height)
                    val scaledW = (decoded.width * scale).toInt().coerceAtLeast(1)
                    val scaledH = (decoded.height * scale).toInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(decoded, scaledW, scaledH, true)
                    if (scaled != decoded) {
                        decoded.recycle()
                    }
                    scaled
                } else {
                    decoded
                }

            val out = ByteArrayOutputStream()
            finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            finalBitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to downsample icon bytes: ${e.message}")
            null
        }
    }

    /** Location of the cached library icon image for [packageName]. */
    fun libraryIconFileFor(
        context: Context,
        packageName: String,
    ): File = File(File(context.cacheDir, DIR_LIBRARY_ICONS), "$packageName$COVER_FILE_EXTENSION")

    /**
     * Stores user-selected library icon artwork for [packageName], records its SteamGridDB [imageId] and [type],
     * and publishes the new icon path to the catalog. Downsamples oversized icons (greater than [MAX_LIBRARY_ICON_DIMENSION_PX])
     * before saving to disk.
     */
    suspend fun applyCustomLibraryIcon(
        context: Context,
        packageName: String,
        bytes: ByteArray,
        imageId: Int,
        type: String,
        onIconWritten: () -> Unit = {},
    ): String? =
        withContext(Dispatchers.IO) {
            val file = libraryIconFileFor(context, packageName)
            val downsampledBytes = downsampleIconBytes(bytes)
            if (downsampledBytes == null) {
                AppLog.w(TAG, "Cannot apply custom library icon for $packageName: image data could not be decoded")
                return@withContext null
            }
            if (!writeImageAtomically(file, downsampledBytes)) return@withContext null
            onIconWritten()
            setArtworkOptOut(context, ArtworkKind.LIBRARY_ICON, packageName, false)
            recordScrapeAttempt(context, packageName, ArtworkKind.LIBRARY_ICON)
            setLibraryIconRef(context, packageName, LibraryIconRef(id = imageId, type = type))
            refreshAppMetadata(context, packageName)
            AppLog.i(TAG, "Applied custom library icon for $packageName (imageId=$imageId, type=$type)")
            file.absolutePath
        }

    /**
     * Deletes any cached library icon for [packageName], clears its image reference, and restores
     * the default library icon in the catalog.
     */
    suspend fun revertToDefaultLibraryIcon(
        context: Context,
        packageName: String,
        onIconRemoved: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val file = libraryIconFileFor(context, packageName)
        if (file.exists() && !file.delete()) {
            AppLog.w(TAG, "Failed to delete library icon ${file.absolutePath}")
        }
        onIconRemoved()
        setArtworkOptOut(context, ArtworkKind.LIBRARY_ICON, packageName, true)
        recordScrapeAttempt(context, packageName, ArtworkKind.LIBRARY_ICON)
        setLibraryIconRef(context, packageName, null)
        refreshAppMetadata(context, packageName)
        AppLog.i(TAG, "Reverted $packageName to default library icon")
    }

    @Volatile
    private var latestSteamGridDbApiKey: String = ""

    fun triggerSteamGridDbScraping(
        context: Context,
        apiKey: String = latestSteamGridDbApiKey,
    ) {
        if (apiKey.isNotBlank()) {
            latestSteamGridDbApiKey = apiKey
        }
        scrapeRequests.trySend(context.applicationContext ?: context)
    }

    private suspend fun executeScrapingPass(context: Context) {
        // Read the token per pass so follow-up passes pick up token changes.
        val apiKey = latestSteamGridDbApiKey
        if (apiKey.isBlank()) {
            AppLog.d(TAG, "SteamGridDB API key is blank, skipping artwork scraping")
            return
        }

        val coversDir = File(context.cacheDir, DIR_COVERS).apply { mkdirs() }
        val logosDir = File(context.cacheDir, DIR_LOGOS).apply { mkdirs() }
        val libraryIconsDir = File(context.cacheDir, DIR_LIBRARY_ICONS).apply { mkdirs() }

        ensureMetadataLoaded(context)

        val currentApps = installedApps.value
        val appsToScrape =
            currentApps.mapNotNull { app ->
                val coverFile = File(coversDir, "${app.packageName}$COVER_FILE_EXTENSION")
                val hasCover = coverFile.exists() && coverFile.length() > 0L
                val scrapeCover =
                    shouldAutoScrape(
                        isOptedOut = isCoverOptedOut(app.packageName),
                        wasAttempted = wasAttempted(app.packageName, ArtworkKind.COVER),
                        hasFile = hasCover,
                    )

                val logoFile = File(logosDir, "${app.packageName}$COVER_FILE_EXTENSION")
                val hasLogo = logoFile.exists() && logoFile.length() > 0L
                val scrapeLogo =
                    app.isRom &&
                        shouldAutoScrape(
                            isOptedOut = false,
                            wasAttempted = wasAttempted(app.packageName, ArtworkKind.LOGO),
                            hasFile = hasLogo,
                        )

                val libraryIconFile = File(libraryIconsDir, "${app.packageName}$COVER_FILE_EXTENSION")
                val hasLibraryIcon = libraryIconFile.exists() && libraryIconFile.length() > 0L
                val isGameOrRom = app.isGame || app.isRom
                val scrapeLibraryIcon =
                    isGameOrRom &&
                        shouldAutoScrape(
                            isOptedOut = isLibraryIconOptedOut(app.packageName),
                            wasAttempted = wasAttempted(app.packageName, ArtworkKind.LIBRARY_ICON),
                            hasFile = hasLibraryIcon,
                        )

                if (scrapeCover || scrapeLogo || scrapeLibraryIcon) {
                    AppScrapePlan(app, scrapeCover, scrapeLogo, scrapeLibraryIcon, coverFile, logoFile, libraryIconFile)
                } else {
                    null
                }
            }

        if (appsToScrape.isEmpty()) {
            AppLog.d(TAG, "No un-scraped apps missing artwork")
            return
        }

        AppLog.i(TAG, "Starting background SteamGridDB scraping pass for ${appsToScrape.size} apps")

        appsToScrape.forEach { plan ->
            val app = plan.app
            try {
                val cleanedQuery = SteamGridDbClient.cleanSearchQuery(app.label)
                AppLog.d(
                    TAG,
                    "Scraping artwork for '${app.label}' (cleaned: '$cleanedQuery', cover=${plan.scrapeCover}, logo=${plan.scrapeLogo}, icon=${plan.scrapeLibraryIcon})",
                )
                val searchResult = SteamGridDbClient.searchGames(cleanedQuery, apiKey)
                if (searchResult.isFailure) {
                    AppLog.w(TAG, "Network error searching SteamGridDB for ${app.label}, will retry on a later pass")
                    return@forEach
                }

                val games = searchResult.getOrNull()
                val gameId = games?.firstOrNull()?.id
                if (gameId == null) {
                    if (plan.scrapeCover) recordScrapeAttempt(context, app.packageName, ArtworkKind.COVER)
                    if (plan.scrapeLogo) recordScrapeAttempt(context, app.packageName, ArtworkKind.LOGO)
                    if (plan.scrapeLibraryIcon) recordScrapeAttempt(context, app.packageName, ArtworkKind.LIBRARY_ICON)
                    AppLog.d(TAG, "No SteamGridDB game match for '${app.label}'")
                    return@forEach
                }

                if (plan.scrapeCover) {
                    runScrapeStep(context, app, ArtworkKind.COVER) { scrapeCover(context, plan, gameId, apiKey) }
                }
                if (plan.scrapeLogo) {
                    runScrapeStep(context, app, ArtworkKind.LOGO) { scrapeLogo(context, plan, gameId, apiKey) }
                }
                if (plan.scrapeLibraryIcon) {
                    runScrapeStep(context, app, ArtworkKind.LIBRARY_ICON) { scrapeLibraryIcon(context, plan, gameId, apiKey) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to scrape for ${app.label}: ${e.message}")
            }
        }
    }

    /**
     * Runs one artwork scrape [step] and records the attempt only when the step completed (HTTP success,
     * including "no artwork available"). Network or write failures are not recorded so a later pass retries.
     */
    private inline fun runScrapeStep(
        context: Context,
        app: InstalledAppInfo,
        kind: ArtworkKind,
        step: () -> Boolean,
    ) {
        val completed =
            try {
                step()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to scrape $kind for ${app.label}: ${e.message}")
                false
            }
        if (completed) {
            recordScrapeAttempt(context, app.packageName, kind)
        } else {
            AppLog.d(TAG, "$kind scrape for ${app.label} incomplete; not recorded so a later pass retries")
        }
    }

    /** @return true when the cover step completed (saved, or no cover available). */
    private suspend fun scrapeCover(
        context: Context,
        plan: AppScrapePlan,
        gameId: Int,
        apiKey: String,
    ): Boolean {
        val app = plan.app
        val imagesResult = SteamGridDbClient.fetchImages(gameId, IMAGE_TYPE_GRIDS, apiKey)
        if (imagesResult.isFailure) {
            AppLog.w(TAG, "Network error fetching covers for ${app.label}")
            return false
        }
        val firstImage = imagesResult.getOrNull()?.firstOrNull()
        if (firstImage == null) {
            AppLog.d(TAG, "No SteamGridDB cover available for ${app.label}")
            return true
        }
        val bytes =
            SteamGridDbClient.downloadImageBytes(firstImage.url).getOrElse { e ->
                AppLog.w(TAG, "Failed to download cover for ${app.label}: ${e.message}")
                return false
            }
        if (!writeImageAtomically(plan.coverFile, bytes)) return false
        setCoverImageId(context, app.packageName, firstImage.id)
        refreshAppMetadata(context, app.packageName)
        AppLog.i(TAG, "Successfully scraped SteamGridDB cover for ${app.label} (imageId=${firstImage.id})")
        return true
    }

    /** @return true when the ROM logo step completed (saved, or no logo available). */
    private suspend fun scrapeLogo(
        context: Context,
        plan: AppScrapePlan,
        gameId: Int,
        apiKey: String,
    ): Boolean {
        val app = plan.app
        val logosResult = SteamGridDbClient.fetchImages(gameId, IMAGE_TYPE_LOGOS, apiKey)
        if (logosResult.isFailure) {
            AppLog.w(TAG, "Network error fetching logos for ROM ${app.label}")
            return false
        }
        val firstLogo = logosResult.getOrNull()?.firstOrNull()
        if (firstLogo == null) {
            AppLog.d(TAG, "No SteamGridDB logo available for ROM ${app.label}")
            return true
        }
        // Logos are stored thumb-sized; the full-size URL is only used when no thumbnail exists.
        val logoUrl = firstLogo.thumb.ifBlank { firstLogo.url }
        val logoBytes =
            SteamGridDbClient.downloadImageBytes(logoUrl).getOrElse { e ->
                AppLog.w(TAG, "Failed to download logo for ROM ${app.label}: ${e.message}")
                return false
            }
        if (!writeImageAtomically(plan.logoFile, logoBytes)) return false
        refreshAppMetadata(context, app.packageName)
        AppLog.i(TAG, "Successfully scraped SteamGridDB logo for ROM: ${app.label}")
        return true
    }

    /** @return true when the library icon step completed (saved, no icon available, or undecodable payload). */
    private suspend fun scrapeLibraryIcon(
        context: Context,
        plan: AppScrapePlan,
        gameId: Int,
        apiKey: String,
    ): Boolean {
        val app = plan.app
        val iconsResult = SteamGridDbClient.fetchImages(gameId, IMAGE_TYPE_ICONS, apiKey)
        if (iconsResult.isFailure) {
            AppLog.w(TAG, "Network error fetching library icons for ${app.label}")
            return false
        }
        val firstIcon = iconsResult.getOrNull()?.firstOrNull()
        if (firstIcon == null) {
            AppLog.d(TAG, "No SteamGridDB library icon available for ${app.label}")
            return true
        }
        // Same source as manual selection: the full image (ICO containers are unpacked by downsampleIconBytes).
        val rawBytes =
            SteamGridDbClient.downloadImageBytes(firstIcon.url).getOrElse { e ->
                AppLog.w(TAG, "Failed to download library icon for ${app.label}: ${e.message}")
                return false
            }
        val downsampledBytes = downsampleIconBytes(rawBytes)
        if (downsampledBytes == null) {
            // Permanently undecodable payload: record the attempt instead of retrying on every pass.
            AppLog.w(TAG, "Library icon for ${app.label} could not be decoded; skipping")
            return true
        }
        if (!writeImageAtomically(plan.libraryIconFile, downsampledBytes)) return false
        setLibraryIconRef(context, app.packageName, LibraryIconRef(id = firstIcon.id, type = IMAGE_TYPE_ICONS))
        refreshAppMetadata(context, app.packageName)
        AppLog.i(TAG, "Successfully scraped SteamGridDB library icon for ${app.label} (imageId=${firstIcon.id})")
        return true
    }

    private data class AppScrapePlan(
        val app: InstalledAppInfo,
        val scrapeCover: Boolean,
        val scrapeLogo: Boolean,
        val scrapeLibraryIcon: Boolean,
        val coverFile: File,
        val logoFile: File,
        val libraryIconFile: File,
    )

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
        displayId: Int = Display.DEFAULT_DISPLAY,
    ) {
        try {
            val intent =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(displayId)
                }
            context.startActivity(intent, options.toBundle())
            AppLog.i(TAG, "Opened native app info for package: $packageName on display $displayId")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to open native app info for package $packageName on display $displayId: ${e.message}", e)
        }
    }

    fun uninstallApp(
        context: Context,
        packageName: String,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ) {
        try {
            val intent =
                Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(displayId)
                }
            context.startActivity(intent, options.toBundle())
            AppLog.i(TAG, "Launched uninstall intent for package: $packageName on display $displayId")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch uninstall intent for package $packageName on display $displayId: ${e.message}", e)
        }
    }
}
