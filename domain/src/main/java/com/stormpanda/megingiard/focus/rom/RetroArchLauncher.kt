package com.stormpanda.megingiard.focus.rom

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.stormpanda.megingiard.AppLog

private const val TAG = "RetroArchLauncher"

class RetroArchLauncher : RomLauncher {
    override val id: String = "retroarch"
    override val displayName: String = "RetroArch"

    override fun launchGame(
        context: Context,
        romPath: String,
        systemId: String,
        displayId: Int,
    ): Boolean {
        val packageName = getRetroArchPackageName(context)
        if (packageName == null) {
            AppLog.e(TAG, "RetroArch is not installed (checked com.retroarch.aarch64 and com.retroarch)")
            return false
        }

        val systemDef = SUPPORTED_SYSTEMS.find { it.id == systemId }
        val coreName = systemDef?.retroArchCore
        if (coreName == null) {
            AppLog.e(TAG, "No RetroArch core defined for system: $systemId")
            return false
        }

        val corePath = "/data/data/$packageName/cores/$coreName"
        AppLog.i(TAG, "Launching ROM '$romPath' with core '$corePath' on display $displayId")

        return try {
            val intent =
                Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(packageName, "com.retroarch.browser.retroactivity.RetroActivityFuture")
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    putExtra("ROM", romPath)
                    putExtra("LIBRETRO", corePath)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
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
