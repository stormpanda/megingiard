package com.stormpanda.megingiard.focus.rom

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.stormpanda.megingiard.AppLog
import java.io.File

private const val TAG = "GameNativeLauncher"

class GameNativeLauncher : RomLauncher {
    override val id: String = "gamenative"
    override val displayName: String = "GameNative"

    override fun launchGame(
        context: Context,
        romPath: String,
        systemId: String,
        displayId: Int,
        retroArchCore: String?,
    ): Boolean {
        val packageName = "app.gamenative"
        val pm = context.packageManager
        try {
            pm.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            AppLog.e(TAG, "GameNative is not installed (checked app.gamenative)")
            return false
        }

        // Try to parse steam app ID
        val appId = parseSteamAppId(romPath)
        AppLog.i(TAG, "Launching GameNative with ROM path: $romPath, parsed appId: $appId on display $displayId")

        return try {
            val intent =
                Intent().apply {
                    component = ComponentName(packageName, "app.gamenative.MainActivity")
                    if (appId != null) {
                        action = "app.gamenative.LAUNCH_GAME"
                        putExtra("app_id", appId)
                    } else {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        putExtra("ROM", romPath)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(displayId)
                }
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch GameNative: ${e.message}", e)
            false
        }
    }

    private fun parseSteamAppId(romPath: String): Int? {
        val file = File(romPath)
        if (!file.exists()) return null

        // 1. Try parsing filename if it's purely digits (e.g. 620.steam)
        val nameWithoutExt = file.nameWithoutExtension
        nameWithoutExt.toIntOrNull()?.let { return it }

        // 2. Try reading file content (e.g., if .steam or .steamappid contains just the app ID)
        try {
            val content = file.readText().trim()
            content.toIntOrNull()?.let { return it }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }
}
