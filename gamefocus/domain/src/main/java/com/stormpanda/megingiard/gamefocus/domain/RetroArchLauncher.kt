package com.stormpanda.megingiard.gamefocus.domain

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.RomLauncher
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "RetroArchLauncher"
private const val RETROARCH_MAIN_ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"
private const val RETROARCH_EXTRA_ROM = "ROM"
private const val RETROARCH_EXTRA_LIBRETRO = "LIBRETRO"
private const val RETROARCH_EXTRA_CONFIGFILE = "CONFIGFILE"

class RetroArchLauncher : RomLauncher {
    override val id: String = "retroarch"
    override val displayName: String = "RetroArch"

    override suspend fun launchGame(
        context: Context,
        romPath: String,
        systemId: String,
        displayId: Int,
        retroArchCore: String?,
    ): Boolean {
        val packageName = getRetroArchPackageName(context)
        if (packageName == null) {
            AppLog.e(TAG, "RetroArch is not installed (checked com.retroarch.aarch64 and com.retroarch)")
            return false
        }

        val systemDef = SUPPORTED_SYSTEMS.find { it.id == systemId }
        val coreName = retroArchCore ?: systemDef?.retroArchCore
        if (coreName == null) {
            AppLog.e(TAG, "No RetroArch core defined for system: $systemId")
            return false
        }

        val corePath = "/data/data/$packageName/cores/$coreName"
        val configFile = withContext(Dispatchers.IO) { resolveConfigFile(packageName) }
        AppLog.i(TAG, "Launching ROM '$romPath' with core '$corePath' and config '$configFile' on display $displayId")

        return try {
            val intent =
                Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(packageName, RETROARCH_MAIN_ACTIVITY)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    putExtra(RETROARCH_EXTRA_ROM, romPath)
                    putExtra(RETROARCH_EXTRA_LIBRETRO, corePath)
                    if (configFile != null) {
                        putExtra(RETROARCH_EXTRA_CONFIGFILE, configFile)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(displayId)
                }
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch RetroArch: ${e.message}", e)
            false
        }
    }

    private fun resolveConfigFile(packageName: String): String? {
        val candidatePaths =
            listOf(
                "/storage/emulated/0/Android/data/$packageName/files/retroarch.cfg",
                "/sdcard/Android/data/$packageName/files/retroarch.cfg",
                "/storage/emulated/0/RetroArch/retroarch.cfg",
                "/sdcard/RetroArch/retroarch.cfg",
            )
        return candidatePaths.firstOrNull { File(it).exists() }
            ?: "/storage/emulated/0/Android/data/$packageName/files/retroarch.cfg"
    }

    private fun getRetroArchPackageName(context: Context): String? {
        val pm = context.packageManager
        return listOf("com.retroarch.aarch64", "com.retroarch").firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }
    }
}
