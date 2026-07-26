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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "InstalledAppsManager"

object InstalledAppsManager {
    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

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

        val apps =
            resolveInfoList
                .filter { resolveInfo ->
                    resolveInfo.activityInfo.packageName != context.packageName
                }.map { resolveInfo ->
                    val label = resolveInfo.loadLabel(packageManager).toString()
                    val packageName = resolveInfo.activityInfo.packageName
                    val activityName = resolveInfo.activityInfo.name
                    val icon = resolveInfo.loadIcon(packageManager)
                    InstalledAppInfo(
                        packageName = packageName,
                        activityName = activityName,
                        label = label,
                        icon = icon,
                    )
                }.sortedBy { it.label.lowercase() }

        _installedApps.value = apps
        AppLog.d(TAG, "Loaded ${apps.size} installed apps for launcher browser")
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
