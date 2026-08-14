package com.stormpanda.megingiard.macropad

import android.content.Context
import android.content.Intent
import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AppPickerUtils"

/**
 * Lightweight representation of an installed launcher application.
 */
data class InstalledAppItem(
    val appName: String,
    val packageName: String,
)

/**
 * Queries installed launcher applications on [Dispatchers.IO].
 * Returns a sorted, distinct list of [InstalledAppItem] without allocating Bitmaps upfront.
 */
suspend fun queryInstalledLauncherApps(context: Context): List<InstalledAppItem> =
    withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val intent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            resolveInfos
                .mapNotNull { info ->
                    val pkg = info.activityInfo.packageName
                    val label = info.loadLabel(pm).toString()
                    if (pkg.isBlank()) null else InstalledAppItem(appName = label, packageName = pkg)
                }.distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to query installed launcher apps: ${e.message}")
            emptyList()
        }
    }
