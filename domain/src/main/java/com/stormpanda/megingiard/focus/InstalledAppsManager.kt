package com.stormpanda.megingiard.focus

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.view.Display
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.settings.SettingsManager
import com.stormpanda.megingiard.steamgriddb.SteamGridDbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "InstalledAppsManager"

object InstalledAppsManager {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val scrapedPackages = HashSet<String>()
    private var isScrapedPackagesLoaded = false

    private fun loadScrapedPackages(context: Context): Set<String> {
        if (!isScrapedPackagesLoaded) {
            val file = File(context.filesDir, "gamefocus_scraped_apps.txt")
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
        return scrapedPackages
    }

    fun markAppAsScraped(
        context: Context,
        packageName: String,
    ) {
        synchronized(scrapedPackages) {
            loadScrapedPackages(context)
            if (scrapedPackages.add(packageName)) {
                try {
                    val file = File(context.filesDir, "gamefocus_scraped_apps.txt")
                    file.writeText(scrapedPackages.joinToString("\n"))
                    AppLog.i(TAG, "Persisted $packageName to scraped packages registry")
                } catch (e: Exception) {
                    AppLog.e(TAG, "Failed to persist scraped packages file: ${e.message}", e)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun loadInstalledApps(context: Context) {
        val packageManager = context.packageManager
        val mainIntent =
            Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
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

        val coversDir = File(context.cacheDir, "gamefocus_covers").apply { mkdirs() }

        val apps =
            resolveInfoList
                .filter { resolveInfo ->
                    resolveInfo.activityInfo.packageName != context.packageName
                }.map { resolveInfo ->
                    val label = resolveInfo.loadLabel(packageManager).toString()
                    val packageName = resolveInfo.activityInfo.packageName
                    val activityName = resolveInfo.activityInfo.name
                    val icon = resolveInfo.loadIcon(packageManager)

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
                    )
                }.sortedBy { it.label.lowercase() }

        _installedApps.value = apps
        AppLog.d(TAG, "Loaded ${apps.size} installed apps for launcher browser")

        // Trigger background SteamGridDB cover scraping if API key is configured
        triggerSteamGridDbScraping(context, coversDir)
    }

    fun updateAppCover(
        packageName: String,
        coverPath: String?,
    ) {
        _installedApps.value =
            _installedApps.value.map { item ->
                if (item.packageName == packageName) {
                    item.copy(coverPath = coverPath)
                } else {
                    item
                }
            }
        AppLog.i(TAG, "Updated in-memory cover path for $packageName to $coverPath")
    }

    private fun triggerSteamGridDbScraping(
        context: Context,
        coversDir: File,
    ) {
        val apiKey = SettingsManager.steamGridDbApiToken.value
        if (apiKey.isBlank()) {
            AppLog.d(TAG, "SteamGridDB API key is blank, skipping cover scraping")
            return
        }

        scope.launch {
            val scrapedSet = synchronized(scrapedPackages) { loadScrapedPackages(context).toSet() }
            val currentApps = _installedApps.value
            val missingCovers = currentApps.filter { it.coverPath == null && !scrapedSet.contains(it.packageName) }
            if (missingCovers.isEmpty()) {
                AppLog.d(TAG, "No un-scraped apps missing cover art")
                return@launch
            }

            AppLog.i(TAG, "Starting background SteamGridDB cover scraping for ${missingCovers.size} apps")

            missingCovers.forEach { app ->
                try {
                    val searchResult = SteamGridDbClient.searchGames(app.label, apiKey)
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
    ): Boolean =
        try {
            val intent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName(appInfo.packageName, appInfo.activityName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(Display.DEFAULT_DISPLAY)
                }
            context.startActivity(intent, options.toBundle())
            AppLog.i(TAG, "Successfully launched ${appInfo.label} (${appInfo.packageName}) on primary display")
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch app ${appInfo.label}: ${e.message}", e)
            false
        }
}
