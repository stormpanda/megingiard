package com.stormpanda.megingiard.gamefocus.domain

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.EMULATOR_ID_GAMENATIVE
import com.stormpanda.megingiard.catalog.PcEmulators
import com.stormpanda.megingiard.catalog.RomLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "GameNativeLauncher"
private const val GAMENATIVE_EXTRA_APP_ID = "app_id"
private const val GAMENATIVE_EXTRA_ROM = "ROM"

class GameNativeLauncher : RomLauncher {
    override val id: String = EMULATOR_ID_GAMENATIVE
    override val displayName: String = "GameNative"

    override suspend fun launchGame(
        context: Context,
        romPath: String,
        systemId: String,
        displayId: Int,
        retroArchCore: String?,
        romUri: String?,
    ): Boolean {
        val packageName = resolveTargetPackage(context, retroArchCore)
        if (packageName == null) {
            AppLog.e(
                TAG,
                "No supported PC emulator is installed (checked ${PcEmulators.SUPPORTED_EMULATOR_OPTIONS.joinToString { it.packageName }})",
            )
            return false
        }

        // Try to parse steam app ID
        val appId = withContext(Dispatchers.IO) { parseSteamAppId(romPath) }
        AppLog.i(
            TAG,
            "Launching PC ROM '$romPath' (appId: $appId) with package '$packageName' on display $displayId",
        )

        return try {
            val intent =
                Intent().apply {
                    component = ComponentName(packageName, "$packageName.MainActivity")
                    if (appId != null) {
                        action = "$packageName.LAUNCH_GAME"
                        putExtra(GAMENATIVE_EXTRA_APP_ID, appId)
                    } else {
                        action = Intent.ACTION_MAIN
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        putExtra(GAMENATIVE_EXTRA_ROM, romPath)
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

    private fun resolveTargetPackage(
        context: Context,
        preferredPackage: String?,
    ): String? {
        val installed = PcEmulators.getInstalledEmulators(context, forceRefresh = true)
        if (installed.isEmpty()) return null

        if (preferredPackage != null && installed.any { it.packageName == preferredPackage }) {
            return preferredPackage
        }
        return installed.first().packageName
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
