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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "InstalledAppsManager"
private const val FILE_FAVORITES = "gamefocus_favorites.txt"
private const val FILE_HIDDEN = "gamefocus_hidden.txt"
private const val FILE_LAST_USED = "gamefocus_last_used.txt"
private const val FILE_SCRAPED_APPS = "gamefocus_scraped_apps.txt"
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

    /** Serializes AtomicFile writes; each write snapshots the latest in-memory state while holding the lock. */
    private val persistLock = Any()

    /** Custom display names keyed by package name (Android apps and `rom.*` pseudo packages). */
    private val customAppNames = mutableMapOf<String, String>()
    private val coverImageIds = mutableMapOf<String, Int>()
    private val libraryIconRefs = mutableMapOf<String, LibraryIconRef>()

    private val installedAndroidAppsFlow = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> =
        combine(
            installedAndroidAppsFlow,
            RomManager.romApps,
        ) { androidApps, romApps ->
            (androidApps + romApps).sortedBy { it.label.lowercase() }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val artworkOptOutsLock = Any()
    private var artworkOptOuts = ArtworkOptOuts()

    /** Opt-outs are loaded from disk once; later reloads would overwrite unsaved in-memory user choices. */
    @Volatile
    private var isArtworkOptOutsLoaded = false

    private val scrapeAttemptsLock = Any()
    private var scrapeAttempts = ScrapeAttempts()

    @Volatile
    private var isScrapeAttemptsLoaded = false

    /** Single-flight guard for scraping passes; [isRerunRequested] schedules a follow-up pass. */
    private val scrapeMutex = Mutex()
    private val isRerunRequested = AtomicBoolean(false)

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _hiddenApps = MutableStateFlow<Set<String>>(emptySet())
    val hiddenApps: StateFlow<Set<String>> = _hiddenApps.asStateFlow()

    private val _lastUsed = MutableStateFlow<List<String>>(emptyList())
    val lastUsed: StateFlow<List<String>> = _lastUsed.asStateFlow()

    private val userListsLock = Any()

    @Volatile
    private var isUserListsLoaded = false

    private var loadAppsJob: Job? = null

    internal fun resetForTesting() {
        loadAppsJob?.cancel()
        loadAppsJob = null
        installedAndroidAppsFlow.value = emptyList()
        _favorites.value = emptySet()
        _hiddenApps.value = emptySet()
        _lastUsed.value = emptyList()
        synchronized(userListsLock) {
            isUserListsLoaded = false
        }
        synchronized(artworkOptOutsLock) {
            artworkOptOuts = ArtworkOptOuts()
            isArtworkOptOutsLoaded = false
        }
        synchronized(scrapeAttemptsLock) {
            scrapeAttempts = ScrapeAttempts()
            isScrapeAttemptsLoaded = false
        }
        synchronized(customAppNames) {
            customAppNames.clear()
            isCustomAppNamesLoaded = false
        }
        synchronized(coverImageIds) {
            coverImageIds.clear()
            isCoverImageIdsLoaded = false
        }
        synchronized(libraryIconRefs) {
            libraryIconRefs.clear()
            isLibraryIconRefsLoaded = false
        }
        latestSteamGridDbApiKey = ""
    }

    /** Load-once guards (AGENTS.md §7.6): later reloads must never overwrite unsaved in-memory changes. */
    @Volatile
    private var isCustomAppNamesLoaded = false

    @Volatile
    private var isCoverImageIdsLoaded = false

    @Volatile
    private var isLibraryIconRefsLoaded = false

    private fun loadCustomAppNames(context: Context) {
        synchronized(customAppNames) {
            if (isCustomAppNamesLoaded) return
            isCustomAppNamesLoaded = true
            val file = File(context.filesDir, FILE_APP_NAMES)
            if (!file.exists()) return
            try {
                val text = AtomicFile(file).readFully().toString(Charsets.UTF_8)
                val map = Json.decodeFromString<Map<String, String>>(text)
                customAppNames.putAll(map)
                AppLog.d(TAG, "Loaded ${map.size} custom app names from disk")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load $FILE_APP_NAMES: ${e.message}")
            }
        }
    }

    private fun loadCoverImageIds(context: Context) {
        synchronized(coverImageIds) {
            if (isCoverImageIdsLoaded) return
            isCoverImageIdsLoaded = true
            val file = File(context.filesDir, FILE_COVER_IMAGE_IDS)
            if (!file.exists()) return
            try {
                val text = AtomicFile(file).readFully().toString(Charsets.UTF_8)
                val map = Json.decodeFromString<Map<String, Int>>(text)
                coverImageIds.putAll(map)
                AppLog.d(TAG, "Loaded ${map.size} cover image IDs from disk")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load $FILE_COVER_IMAGE_IDS: ${e.message}")
            }
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
            } catch (e: Exception) {
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
        loadCoverImageIds(context)
        synchronized(coverImageIds) {
            if (imageId != null) {
                coverImageIds[packageName] = imageId
            } else {
                coverImageIds.remove(packageName)
            }
        }
        persistScope.launch { saveCoverImageIds(context) }
    }

    private fun loadLibraryIconIds(context: Context) {
        synchronized(libraryIconRefs) {
            if (isLibraryIconRefsLoaded) return
            isLibraryIconRefsLoaded = true
            val file = File(context.filesDir, FILE_LIBRARY_ICON_IDS)
            if (!file.exists()) return
            try {
                val text = AtomicFile(file).readFully().toString(Charsets.UTF_8)
                val map = LibraryIconRef.parseMap(text)
                libraryIconRefs.putAll(map)
                AppLog.d(TAG, "Loaded ${map.size} library icon refs from disk")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load $FILE_LIBRARY_ICON_IDS: ${e.message}")
            }
        }
    }

    private fun saveLibraryIconIds(context: Context) {
        synchronized(persistLock) {
            val content = synchronized(libraryIconRefs) { libraryIconRefs.toMap() }
            val file = File(context.filesDir, FILE_LIBRARY_ICON_IDS)
            val atomicFile = AtomicFile(file)
            var fos: FileOutputStream? = null
            try {
                val text = LibraryIconRef.serializeMap(content)
                fos = atomicFile.startWrite()
                fos.write(text.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(fos)
                AppLog.d(TAG, "Saved library icon refs to disk")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to save $FILE_LIBRARY_ICON_IDS: ${e.message}")
                if (fos != null) atomicFile.failWrite(fos)
            }
        }
    }

    fun getLibraryIconRef(packageName: String): LibraryIconRef? = synchronized(libraryIconRefs) { libraryIconRefs[packageName] }

    fun setLibraryIconRef(
        context: Context,
        packageName: String,
        ref: LibraryIconRef?,
    ) {
        loadLibraryIconIds(context)
        synchronized(libraryIconRefs) {
            if (ref != null) {
                libraryIconRefs[packageName] = ref
            } else {
                libraryIconRefs.remove(packageName)
            }
        }
        persistScope.launch { saveLibraryIconIds(context) }
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
            } catch (e: Exception) {
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
        loadLibraryIconIds(context)
        loadArtworkOptOuts(context)
        loadScrapeAttempts(context)
        ensureUserListsLoaded(context)
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
        linesProvider: () -> Iterable<String>,
    ) {
        persistScope.launch {
            synchronized(persistLock) {
                val snapshot = synchronized(userListsLock) { linesProvider().toList() }
                val file = File(context.filesDir, filename)
                val atomicFile = AtomicFile(file)
                var fos: FileOutputStream? = null
                try {
                    fos = atomicFile.startWrite()
                    val writer = fos.bufferedWriter(Charsets.UTF_8)
                    for (line in snapshot) {
                        writer.write(line)
                        writer.newLine()
                    }
                    writer.flush()
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
        synchronized(userListsLock) {
            val current = stateFlow.value.toMutableSet()
            val added = !current.remove(item)
            if (added) current.add(item)
            AppLog.i(TAG, "${if (added) "Added" else "Removed"} $item ${if (added) "to" else "from"} $label")
            stateFlow.value = current.toSet()
        }
        persistLines(context, filename) { stateFlow.value }
    }

    private fun ensureUserListsLoaded(context: Context) {
        synchronized(userListsLock) {
            if (isUserListsLoaded) return
            isUserListsLoaded = true
            loadFavorites(context)
            loadHidden(context)
            loadLastUsed(context)
        }
    }

    private fun loadFavorites(context: Context) {
        _favorites.value = loadStringSet(context, FILE_FAVORITES)
        AppLog.d(TAG, "Loaded ${_favorites.value.size} favorite apps from disk")
    }

    fun toggleFavorite(
        context: Context,
        packageName: String,
    ) {
        ensureUserListsLoaded(context)
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
        ensureUserListsLoaded(context)
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
        ensureUserListsLoaded(context)
        val trimmed =
            synchronized(userListsLock) {
                val list = _lastUsed.value.toMutableList()
                list.remove(packageName)
                list.add(0, packageName)
                val updated = list.take(MAX_RECENT_APPS)
                _lastUsed.value = updated
                updated
            }
        persistLines(context, FILE_LAST_USED) { _lastUsed.value }
        AppLog.i(TAG, "Recorded launch for $packageName (recent count=${trimmed.size})")
    }

    private fun loadScrapeAttempts(context: Context): ScrapeAttempts =
        synchronized(scrapeAttemptsLock) {
            if (!isScrapeAttemptsLoaded) {
                val attemptsFile = File(context.filesDir, FILE_SCRAPE_ATTEMPTS)
                if (attemptsFile.exists()) {
                    val atomicFile = AtomicFile(attemptsFile)
                    try {
                        val text = atomicFile.readFully().toString(Charsets.UTF_8)
                        scrapeAttempts = Json.decodeFromString<ScrapeAttempts>(text)
                        AppLog.d(
                            TAG,
                            "Loaded scrape attempts from disk (covers=${scrapeAttempts.cover.size}, logos=${scrapeAttempts.logo.size}, icons=${scrapeAttempts.libraryIcon.size})",
                        )
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to load $FILE_SCRAPE_ATTEMPTS: ${e.message}")
                    }
                } else {
                    val legacyFile = File(context.filesDir, FILE_SCRAPED_APPS)
                    if (legacyFile.exists()) {
                        scrapeAttempts = ScrapeAttempts.fromLegacy(loadStringSet(context, FILE_SCRAPED_APPS))
                        persistScope.launch {
                            if (!saveScrapeAttempts(context)) {
                                AppLog.w(TAG, "Keeping legacy $FILE_SCRAPED_APPS because migrated attempts could not be saved")
                                return@launch
                            }
                            try {
                                if (legacyFile.delete()) {
                                    AppLog.d(TAG, "Deleted legacy $FILE_SCRAPED_APPS after migration")
                                }
                            } catch (e: Exception) {
                                AppLog.w(TAG, "Failed to delete legacy $FILE_SCRAPED_APPS: ${e.message}")
                            }
                        }
                    }
                }
                isScrapeAttemptsLoaded = true
            }
            scrapeAttempts
        }

    /** @return true if the current in-memory attempts were written to disk. */
    private fun saveScrapeAttempts(context: Context): Boolean =
        synchronized(persistLock) {
            val content = synchronized(scrapeAttemptsLock) { scrapeAttempts }
            val file = File(context.filesDir, FILE_SCRAPE_ATTEMPTS)
            val atomicFile = AtomicFile(file)
            var fos: FileOutputStream? = null
            try {
                val text = Json.encodeToString(content)
                fos = atomicFile.startWrite()
                fos.write(text.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(fos)
                AppLog.d(TAG, "Saved scrape attempts to disk")
                true
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to save $FILE_SCRAPE_ATTEMPTS: ${e.message}")
                if (fos != null) atomicFile.failWrite(fos)
                false
            }
        }

    fun wasAttempted(
        packageName: String,
        kind: ArtworkKind,
    ): Boolean = synchronized(scrapeAttemptsLock) { scrapeAttempts.wasAttempted(kind, packageName) }

    fun recordScrapeAttempt(
        context: Context,
        packageName: String,
        kind: ArtworkKind,
    ) {
        // Ensure disk state is loaded first so a later lazy load cannot overwrite this record.
        loadScrapeAttempts(context)
        val modified =
            synchronized(scrapeAttemptsLock) {
                val updated = scrapeAttempts.withAttempt(kind, packageName)
                val changed = updated !== scrapeAttempts
                scrapeAttempts = updated
                changed
            }
        if (modified) {
            AppLog.d(TAG, "Recorded $kind scrape attempt for $packageName")
            persistScope.launch { saveScrapeAttempts(context) }
        }
    }

    private fun loadArtworkOptOuts(context: Context): ArtworkOptOuts =
        synchronized(artworkOptOutsLock) {
            if (!isArtworkOptOutsLoaded) {
                val file = File(context.filesDir, FILE_ARTWORK_OPTOUTS)
                if (file.exists()) {
                    val atomicFile = AtomicFile(file)
                    try {
                        val text = atomicFile.readFully().toString(Charsets.UTF_8)
                        artworkOptOuts = Json.decodeFromString<ArtworkOptOuts>(text)
                        AppLog.d(
                            TAG,
                            "Loaded artwork opt-outs (covers=${artworkOptOuts.cover.size}, icons=${artworkOptOuts.libraryIcon.size})",
                        )
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to load $FILE_ARTWORK_OPTOUTS: ${e.message}")
                    }
                }
                isArtworkOptOutsLoaded = true
            }
            artworkOptOuts
        }

    private fun saveArtworkOptOuts(context: Context) {
        synchronized(persistLock) {
            val content = synchronized(artworkOptOutsLock) { artworkOptOuts }
            val file = File(context.filesDir, FILE_ARTWORK_OPTOUTS)
            val atomicFile = AtomicFile(file)
            var fos: FileOutputStream? = null
            try {
                val text = Json.encodeToString(content)
                fos = atomicFile.startWrite()
                fos.write(text.toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(fos)
                AppLog.d(TAG, "Saved artwork opt-outs to disk")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to save $FILE_ARTWORK_OPTOUTS: ${e.message}")
                if (fos != null) atomicFile.failWrite(fos)
            }
        }
    }

    fun isCoverOptedOut(packageName: String): Boolean =
        synchronized(artworkOptOutsLock) { artworkOptOuts.isOptedOut(ArtworkKind.COVER, packageName) }

    fun isLibraryIconOptedOut(packageName: String): Boolean =
        synchronized(artworkOptOutsLock) { artworkOptOuts.isOptedOut(ArtworkKind.LIBRARY_ICON, packageName) }

    fun setCoverOptedOut(
        context: Context,
        packageName: String,
        optedOut: Boolean,
    ) {
        setArtworkOptOut(context, ArtworkKind.COVER, packageName, optedOut)
        updateCoverOptOut(packageName, optedOut)
    }

    fun setLibraryIconOptedOut(
        context: Context,
        packageName: String,
        optedOut: Boolean,
    ) {
        setArtworkOptOut(context, ArtworkKind.LIBRARY_ICON, packageName, optedOut)
        updateLibraryIconOptOut(packageName, optedOut)
    }

    private fun setArtworkOptOut(
        context: Context,
        kind: ArtworkKind,
        packageName: String,
        optedOut: Boolean,
    ) {
        // Ensure disk state is loaded first so a later lazy load cannot overwrite this choice.
        loadArtworkOptOuts(context)
        synchronized(artworkOptOutsLock) {
            artworkOptOuts = artworkOptOuts.withOptOut(kind, packageName, optedOut)
        }
        AppLog.i(TAG, "Set $kind opt-out for $packageName to $optedOut")
        persistScope.launch { saveArtworkOptOuts(context) }
    }

    fun updateCoverOptOut(
        packageName: String,
        optedOut: Boolean,
    ) {
        if (packageName.startsWith(ROM_PACKAGE_PREFIX)) {
            RomManager.updateRomCoverOptOut(packageName, optedOut)
            return
        }
        installedAndroidAppsFlow.update { it.withUpdatedCoverOptOut(packageName, optedOut) }
    }

    fun updateLibraryIconOptOut(
        packageName: String,
        optedOut: Boolean,
    ) {
        if (packageName.startsWith(ROM_PACKAGE_PREFIX)) {
            RomManager.updateRomLibraryIconOptOut(packageName, optedOut)
            return
        }
        installedAndroidAppsFlow.update { it.withUpdatedLibraryIconOptOut(packageName, optedOut) }
    }

    fun updateAppLogo(
        packageName: String,
        lastModified: Long,
    ) {
        if (packageName.startsWith(ROM_PACKAGE_PREFIX)) {
            RomManager.updateRomLogo(packageName, lastModified)
            return
        }
        installedAndroidAppsFlow.update { it.withUpdatedLogo(packageName, lastModified) }
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
                loadArtworkOptOuts(context)
                loadScrapeAttempts(context)

                // Custom names, cover IDs, and library icon IDs must be loaded before the ROM scan, which reads them.
                loadCustomAppNames(context)
                loadCoverImageIds(context)
                loadLibraryIconIds(context)

                RomManager.loadRomFolders(context)
                // Scraping is triggered once below, after the Android app list is published.
                RomManager.reloadRomAppsSuspend(context, triggerScrape = false)

                SystemRoleClassifier.refreshLaunchers(context)

                ensureUserListsLoaded(context)

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

                            val cachedLibraryIconFile = libraryIconFileFor(context, packageName)
                            val hasLibraryIcon = cachedLibraryIconFile.exists() && cachedLibraryIconFile.length() > 0
                            val libraryIconPath = if (hasLibraryIcon) cachedLibraryIconFile.absolutePath else null
                            val libraryIconLastModified = if (hasLibraryIcon) cachedLibraryIconFile.lastModified() else 0L
                            val libraryIconRef = if (hasLibraryIcon) getLibraryIconRef(packageName) else null

                            InstalledAppInfo(
                                packageName = packageName,
                                activityName = activityName,
                                label = label,
                                coverPath = coverPath,
                                isGame = isGame,
                                coverLastModified = coverLastModified,
                                coverImageId = coverImageId,
                                defaultLabel = defaultLabel,
                                libraryIconPath = libraryIconPath,
                                libraryIconImageId = libraryIconRef?.id,
                                libraryIconType = libraryIconRef?.type,
                                libraryIconLastModified = libraryIconLastModified,
                                coverOptedOut = isCoverOptedOut(packageName),
                                libraryIconOptedOut = isLibraryIconOptedOut(packageName),
                                logoLastModified = 0L,
                            )
                        }.sortedBy { it.label.lowercase() }

                installedAndroidAppsFlow.value = apps
                val gameCount = apps.count { it.isGame }
                AppLog.d(TAG, "Loaded ${apps.size} installed apps ($gameCount games, ${apps.size - gameCount} apps) for launcher browser")

                // Trigger background SteamGridDB cover scraping if API key is configured
                triggerSteamGridDbScraping(context)
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

    fun updateAppLibraryIcon(
        packageName: String,
        iconPath: String?,
        imageId: Int? = null,
        type: String? = null,
        lastModified: Long = System.currentTimeMillis(),
    ) {
        if (packageName.startsWith(ROM_PACKAGE_PREFIX)) {
            RomManager.updateRomLibraryIcon(packageName, iconPath, imageId, type, lastModified)
            return
        }
        installedAndroidAppsFlow.update { it.withUpdatedLibraryIcon(packageName, iconPath, imageId, type, lastModified) }
        AppLog.i(
            TAG,
            "Updated in-memory library icon path for $packageName to $iconPath (imageId: $imageId, type: $type, lastModified: $lastModified)",
        )
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
        loadCustomAppNames(context)
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
        loadCustomAppNames(context)
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
            setCoverOptedOut(context, packageName, false)
            recordScrapeAttempt(context, packageName, ArtworkKind.COVER)
            setCoverImageId(context, packageName, imageId)
            updateAppCover(packageName, file.absolutePath, imageId)
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
        setCoverOptedOut(context, packageName, true)
        recordScrapeAttempt(context, packageName, ArtworkKind.COVER)
        setCoverImageId(context, packageName, null)
        updateAppCover(packageName, null, null)
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
            val lastModified = file.lastModified()
            onIconWritten()
            setLibraryIconOptedOut(context, packageName, false)
            recordScrapeAttempt(context, packageName, ArtworkKind.LIBRARY_ICON)
            setLibraryIconRef(context, packageName, LibraryIconRef(id = imageId, type = type))
            updateAppLibraryIcon(packageName, file.absolutePath, imageId, type, lastModified)
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
        setLibraryIconOptedOut(context, packageName, true)
        recordScrapeAttempt(context, packageName, ArtworkKind.LIBRARY_ICON)
        setLibraryIconRef(context, packageName, null)
        updateAppLibraryIcon(packageName, null, null, null, 0L)
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
        scope.launch { runScrapingPasses(context) }
    }

    /**
     * Runs scraping passes single-flight. A request arriving while a pass is active only sets
     * [isRerunRequested]; the active owner re-checks the flag after unlocking, so no request is lost.
     */
    private suspend fun runScrapingPasses(context: Context) {
        while (true) {
            if (!scrapeMutex.tryLock()) {
                AppLog.d(TAG, "Scraping pass already in progress; scheduling follow-up pass")
                isRerunRequested.set(true)
                return
            }
            try {
                do {
                    isRerunRequested.set(false)
                    executeScrapingPass(context)
                } while (isRerunRequested.get())
            } finally {
                scrapeMutex.unlock()
            }
            // A request may have arrived between the last flag check and unlock.
            if (!isRerunRequested.get()) return
            AppLog.d(TAG, "Follow-up scraping pass requested during unlock; restarting")
        }
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

        loadArtworkOptOuts(context)
        loadScrapeAttempts(context)

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
                    runScrapeStep(context, app, ArtworkKind.LOGO) { scrapeLogo(plan, gameId, apiKey) }
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
        updateAppCover(app.packageName, plan.coverFile.absolutePath, firstImage.id)
        AppLog.i(TAG, "Successfully scraped SteamGridDB cover for ${app.label} (imageId=${firstImage.id})")
        return true
    }

    /** @return true when the ROM logo step completed (saved, or no logo available). */
    private suspend fun scrapeLogo(
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
        updateAppLogo(app.packageName, plan.logoFile.lastModified())
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
        val lastModified = plan.libraryIconFile.lastModified()
        setLibraryIconRef(context, app.packageName, LibraryIconRef(id = firstIcon.id, type = IMAGE_TYPE_ICONS))
        updateAppLibraryIcon(app.packageName, plan.libraryIconFile.absolutePath, firstIcon.id, IMAGE_TYPE_ICONS, lastModified)
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
