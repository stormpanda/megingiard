package com.stormpanda.megingiard.catalog

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
import com.stormpanda.megingiard.ipc.IpcSettingsParser
import com.stormpanda.megingiard.ipc.MegingiardIpcContract
import com.stormpanda.megingiard.ipc.observeContentProvider
import com.stormpanda.megingiard.media.SteamGridDbClient
import com.stormpanda.megingiard.media.SteamGridDbGame
import com.stormpanda.megingiard.media.SteamGridDbImage
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
private const val THOR_SECONDARY_DISPLAY_FALLBACK_ID = 4

object InstalledAppsManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

    private fun loadStringList(
        context: Context,
        filename: String,
        limit: Int = Int.MAX_VALUE,
    ): List<String> {
        val file = File(context.filesDir, filename)
        if (!file.exists()) return emptyList()
        return try {
            file
                .readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(limit)
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
        scope.launch {
            try {
                val file = File(context.filesDir, filename)
                file.writeText(lines.joinToString("\n"))
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to persist $filename: ${e.message}", e)
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
        if (current.contains(item)) {
            current.remove(item)
            AppLog.i(TAG, "Removed $item from $label")
        } else {
            current.add(item)
            AppLog.i(TAG, "Added $item to $label")
        }
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
        scope.launch {
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
                        val isGame = isPackageAGame(appInfo, gamePackagesFromIntent)

                        val cachedCoverFile = File(coversDir, "$packageName.png")
                        val hasCover = cachedCoverFile.exists() && cachedCoverFile.length() > 0
                        val coverPath = if (hasCover) cachedCoverFile.absolutePath else null
                        val coverLastModified = if (hasCover) cachedCoverFile.lastModified() else 0L

                        InstalledAppInfo(
                            packageName = packageName,
                            activityName = activityName,
                            label = label,
                            coverPath = coverPath,
                            isGame = isGame,
                            coverLastModified = coverLastModified,
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
    ) {
        if (packageName.startsWith("rom.")) {
            RomManager.updateRomCover(packageName, coverPath)
            return
        }
        installedAndroidAppsFlow.value =
            installedAndroidAppsFlow.value.map { item ->
                if (item.packageName == packageName) {
                    item.copy(
                        coverPath = coverPath,
                        coverLastModified = System.currentTimeMillis(),
                    )
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
        MegingiardIpcContract.init(context)
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
                    val coverFile = File(coversDir, "${app.packageName}.png")
                    val logosDir = File(context.cacheDir, "gamefocus_logos")
                    val logoFile = File(logosDir, "${app.packageName}.png")

                    val hasCover = coverFile.exists() && coverFile.length() > 0L
                    val hasLogo = logoFile.exists() && logoFile.length() > 0L

                    val isScraped = scrapedSet.contains(app.packageName)
                    if (app.isRom) {
                        !isScraped || !hasCover || !hasLogo
                    } else {
                        !isScraped || !hasCover
                    }
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
                        val imageUrl = images?.firstOrNull()?.url
                        if (imageUrl != null) {
                            val bytes = SteamGridDbClient.downloadImageBytes(imageUrl).getOrNull()
                            if (bytes != null) {
                                coverFile.writeBytes(bytes)
                                updateAppCover(app.packageName, coverFile.absolutePath)
                                AppLog.i(TAG, "Successfully scraped SteamGridDB cover for ${app.label}")
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
            val systemId = appInfo.systemId ?: return false
            val romPath = appInfo.romPath ?: return false
            val systemDef = SUPPORTED_SYSTEMS.find { it.id == systemId } ?: return false
            val launcher = RomLauncherRegistry.getLauncher(systemDef.emulatorId) ?: return false
            val success = launcher.launchGame(context, romPath, systemId, displayId, appInfo.retroArchCore)
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
