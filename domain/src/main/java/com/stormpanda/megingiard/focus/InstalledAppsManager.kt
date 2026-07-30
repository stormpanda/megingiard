package com.stormpanda.megingiard.focus

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Display
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.focus.rom.RomLauncherRegistry
import com.stormpanda.megingiard.focus.rom.RomManager
import com.stormpanda.megingiard.focus.rom.SUPPORTED_SYSTEMS
import com.stormpanda.megingiard.ipc.IpcSettingsParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.ipc.observeContentProvider
import com.stormpanda.megingiard.mirror.DisplayDetector
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.steamgriddb.SteamGridDbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "InstalledAppsManager"
private const val FILE_FAVORITES = "gamefocus_favorites.txt"
private const val FILE_HIDDEN = "gamefocus_hidden.txt"
private const val FILE_LAST_USED = "gamefocus_last_used.txt"
private const val FILE_SCRAPED_APPS = "gamefocus_scraped_apps.txt"
private const val DIR_COVERS = "gamefocus_covers"
private const val MAX_RECENT_APPS = 10
private const val INTENT_CATEGORY_GAME = "android.intent.category.GAME"
private const val INTENT_CATEGORY_APP_GAMES = "android.intent.category.APP_GAMES"

object InstalledAppsManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _installedAndroidApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> =
        combine(
            _installedAndroidApps,
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

    private fun loadFavorites(context: Context) {
        val file = File(context.filesDir, FILE_FAVORITES)
        if (file.exists()) {
            try {
                val set =
                    file
                        .readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                _favorites.value = set
                AppLog.d(TAG, "Loaded ${set.size} favorite apps from disk")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load favorites file: ${e.message}")
            }
        }
    }

    fun toggleFavorite(
        context: Context,
        packageName: String,
    ) {
        val current = _favorites.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
            AppLog.i(TAG, "Removed $packageName from favorites")
        } else {
            current.add(packageName)
            AppLog.i(TAG, "Added $packageName to favorites")
        }
        _favorites.value = current
        try {
            val file = File(context.filesDir, FILE_FAVORITES)
            file.writeText(current.joinToString("\n"))
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to persist favorites: ${e.message}", e)
        }
    }

    private fun loadHidden(context: Context) {
        val file = File(context.filesDir, FILE_HIDDEN)
        if (file.exists()) {
            try {
                val set =
                    file
                        .readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                _hiddenApps.value = set
                AppLog.d(TAG, "Loaded ${set.size} hidden apps from disk")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load hidden apps file: ${e.message}")
            }
        }
    }

    fun toggleHidden(
        context: Context,
        packageName: String,
    ) {
        val current = _hiddenApps.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
            AppLog.i(TAG, "Removed $packageName from hidden apps")
        } else {
            current.add(packageName)
            AppLog.i(TAG, "Added $packageName to hidden apps")
        }
        _hiddenApps.value = current
        try {
            val file = File(context.filesDir, FILE_HIDDEN)
            file.writeText(current.joinToString("\n"))
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to persist hidden apps: ${e.message}", e)
        }
    }

    private fun loadLastUsed(context: Context) {
        val file = File(context.filesDir, FILE_LAST_USED)
        if (file.exists()) {
            try {
                val list =
                    file
                        .readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .take(MAX_RECENT_APPS)
                _lastUsed.value = list
                AppLog.d(TAG, "Loaded ${list.size} last used apps from disk")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to load last used file: ${e.message}")
            }
        }
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
        try {
            val file = File(context.filesDir, FILE_LAST_USED)
            file.writeText(trimmed.joinToString("\n"))
            AppLog.i(TAG, "Recorded launch for $packageName (recent count=${trimmed.size})")
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to persist last used apps: ${e.message}", e)
        }
    }

    private fun loadScrapedPackages(context: Context): Set<String> =
        synchronized(scrapedPackages) {
            if (!isScrapedPackagesLoaded) {
                val file = File(context.filesDir, FILE_SCRAPED_APPS)
                if (file.exists()) {
                    try {
                        file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                            scrapedPackages.add(it)
                        }
                        AppLog.d(TAG, "Loaded ${scrapedPackages.size} scraped package records from disk")
                    } catch (e: Exception) {
                        AppLog.w(TAG, "Failed to read scraped packages file: ${e.message}")
                    }
                }
                isScrapedPackagesLoaded = true
            }
            scrapedPackages
        }

    fun markAppAsScraped(
        context: Context,
        packageName: String,
    ) {
        synchronized(scrapedPackages) {
            loadScrapedPackages(context)
            if (scrapedPackages.add(packageName)) {
                try {
                    val file = File(context.filesDir, FILE_SCRAPED_APPS)
                    file.writeText(scrapedPackages.joinToString("\n"))
                    AppLog.i(TAG, "Persisted $packageName to scraped packages registry")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to persist scraped packages file: ${e.message}", e)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun isPackageAGame(
        appInfo: ApplicationInfo,
        gamePackagesFromIntent: Set<String> = emptySet(),
    ): Boolean {
        if (gamePackagesFromIntent.contains(appInfo.packageName)) {
            return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return true
            }
        }
        return (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }

    @Suppress("DEPRECATION")
    fun loadInstalledApps(context: Context) {
        RomManager.loadRomFolders(context)
        RomManager.reloadRomApps(context)

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0L),
                )
            } else {
                packageManager.queryIntentActivities(mainIntent, 0)
            }

        val gameResolveList: List<ResolveInfo> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    gameIntent,
                    PackageManager.ResolveInfoFlags.of(0L),
                ) +
                    packageManager.queryIntentActivities(
                        appGamesIntent,
                        PackageManager.ResolveInfoFlags.of(0L),
                    )
            } else {
                packageManager.queryIntentActivities(gameIntent, 0) + packageManager.queryIntentActivities(appGamesIntent, 0)
            }
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
                    val label = PackageAliasMapper.getTitleForPackage(packageName, rawLabel)
                    val activityName = resolveInfo.activityInfo.name
                    val icon = resolveInfo.loadIcon(packageManager)
                    val isGame = isPackageAGame(appInfo, gamePackagesFromIntent)

                    val cachedCoverFile = File(coversDir, "$packageName.png")
                    val coverPath =
                        if (cachedCoverFile.exists() && cachedCoverFile.length() > 0) {
                            cachedCoverFile.absolutePath
                        } else {
                            null
                        }

                    InstalledAppInfo(
                        packageName = packageName,
                        activityName = activityName,
                        label = label,
                        icon = icon,
                        coverPath = coverPath,
                        isGame = isGame,
                    )
                }.sortedBy { it.label.lowercase() }

        _installedAndroidApps.value = apps
        val gameCount = apps.count { it.isGame }
        AppLog.d(TAG, "Loaded ${apps.size} installed apps ($gameCount games, ${apps.size - gameCount} apps) for launcher browser")

        // Trigger background SteamGridDB cover scraping if API key is configured
        triggerSteamGridDbScraping(context, coversDir)
    }

    fun updateAppCover(
        packageName: String,
        coverPath: String?,
    ) {
        if (packageName.startsWith("rom.")) {
            RomManager.updateRomCover(packageName, coverPath)
            return
        }
        _installedAndroidApps.value =
            _installedAndroidApps.value.map { item ->
                if (item.packageName == packageName) {
                    item.copy(coverPath = coverPath)
                } else {
                    item
                }
            }
        AppLog.i(TAG, "Updated in-memory cover path for $packageName to $coverPath")
    }

    private var isSettingsObserverRegistered = false

    private fun registerSettingsObserverIfNeeded(
        context: Context,
        coversDir: File,
    ) {
        if (isSettingsObserverRegistered) return
        isSettingsObserverRegistered = true
        scope.launch {
            observeContentProvider(
                context,
                MegingiardIpcContract.SETTINGS_URI,
            ) { resolver, uri ->
                IpcSettingsParser.parse(resolver, uri)
            }.collect { config ->
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

        var apiKey = SettingsManager.steamGridDbApiToken.value
        if (apiKey.isBlank()) {
            val ipcConfig = IpcSettingsParser.parse(context.contentResolver)
            apiKey = ipcConfig.steamGridDbApiToken
        }
        if (apiKey.isBlank()) {
            AppLog.d(TAG, "SteamGridDB API key is blank locally and via IPC, skipping cover scraping")
            return
        }

        scope.launch {
            val scrapedSet = synchronized(scrapedPackages) { loadScrapedPackages(context).toSet() }
            val currentApps = installedApps.value
            val missingCovers = currentApps.filter { it.coverPath == null && !scrapedSet.contains(it.packageName) }
            if (missingCovers.isEmpty()) {
                AppLog.d(TAG, "No un-scraped apps missing cover art")
                return@launch
            }

            AppLog.i(TAG, "Starting background SteamGridDB cover scraping for ${missingCovers.size} apps")

            missingCovers.forEach { app ->
                try {
                    val cleanedQuery = SteamGridDbClient.cleanSearchQuery(app.label)
                    AppLog.d(TAG, "Scraping cover art for '${app.label}' (cleaned: '$cleanedQuery')")
                    val searchResult = SteamGridDbClient.searchGames(cleanedQuery, apiKey)
                    if (searchResult.isFailure) {
                        AppLog.w(TAG, "Network error searching SteamGridDB for ${app.label}, will retry next startup")
                        return@forEach
                    }

                    // Search request completed (HTTP success) -> mark as scraped
                    markAppAsScraped(context, app.packageName)

                    val games = searchResult.getOrNull()
                    val gameId = games?.firstOrNull()?.id ?: return@forEach

                    val imagesResult = SteamGridDbClient.fetchImages(gameId, "grids", apiKey)
                    val images = imagesResult.getOrNull()
                    val imageUrl = images?.firstOrNull()?.url ?: return@forEach

                    val tempResult = SteamGridDbClient.downloadImageToTempFile(imageUrl, context.cacheDir)
                    val tempFile = tempResult.getOrNull() ?: return@forEach

                    val targetFile = File(coversDir, "${app.packageName}.png")
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()

                    // Update in-memory state
                    updateAppCover(app.packageName, targetFile.absolutePath)
                    AppLog.i(TAG, "Successfully scraped SteamGridDB cover for ${app.label}")
                } catch (e: Exception) {
                    AppLog.w(TAG, "Failed to scrape cover for ${app.label}: ${e.message}")
                }
            }
        }
    }

    fun launchAppOnPrimaryDisplay(
        context: Context,
        appInfo: InstalledAppInfo,
    ): Boolean = launchAppOnDisplay(context, appInfo, Display.DEFAULT_DISPLAY)

    fun launchAppOnSecondaryDisplay(
        context: Context,
        appInfo: InstalledAppInfo,
    ): Boolean {
        val secondaryDisplay = DisplayDetector.findSecondaryDisplay(context)
        val displayId = secondaryDisplay?.displayId ?: 4 // Fallback to secondary display ID 4 on AYN Thor
        return launchAppOnDisplay(context, appInfo, displayId)
    }

    fun launchAppOnDisplay(
        context: Context,
        appInfo: InstalledAppInfo,
        displayId: Int,
    ): Boolean {
        if (appInfo.isRom) {
            val systemId = appInfo.systemId ?: return false
            val romPath = appInfo.romPath ?: return false
            val systemDef = SUPPORTED_SYSTEMS.find { it.id == systemId } ?: return false
            val launcher = RomLauncherRegistry.getLauncher(systemDef.emulatorId) ?: return false
            val success = launcher.launchGame(context, romPath, systemId, displayId)
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
}
