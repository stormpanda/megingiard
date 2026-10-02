package com.stormpanda.megingiard.gamefocus.domain

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.EMULATOR_ID_YUZU
import com.stormpanda.megingiard.catalog.RomLauncher
import com.stormpanda.megingiard.catalog.SwitchEmulators
import java.io.File

private const val TAG = "SwitchLauncher"
private const val EMULATION_ACTIVITY_NAME = "org.yuzu.yuzu_emu.activities.EmulationActivity"
private const val SWITCH_ACTION = "android.nfc.action.TECH_DISCOVERED"

class SwitchLauncher : RomLauncher {
    override val id: String = EMULATOR_ID_YUZU
    override val displayName: String = "Nintendo Switch"

    override suspend fun launchGame(
        context: Context,
        romPath: String,
        systemId: String,
        displayId: Int,
        retroArchCore: String?,
    ): Boolean {
        val targetPackage = resolveTargetPackage(context, retroArchCore)
        if (targetPackage == null) {
            AppLog.e(TAG, "No supported Nintendo Switch emulator is installed on the device")
            return false
        }

        val romFile = File(romPath)
        val romUri = Uri.fromFile(romFile)
        AppLog.i(TAG, "Launching Switch ROM '$romPath' with package '$targetPackage' on display $displayId")

        return try {
            val intent =
                Intent(SWITCH_ACTION).apply {
                    component = ComponentName(targetPackage, EMULATION_ACTIVITY_NAME)
                    data = romUri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            val options =
                ActivityOptions.makeBasic().apply {
                    setLaunchDisplayId(displayId)
                }
            context.startActivity(intent, options.toBundle())
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to launch Switch game in $targetPackage: ${e.message}", e)
            false
        }
    }

    private fun resolveTargetPackage(
        context: Context,
        preferredPackage: String?,
    ): String? {
        val installed = SwitchEmulators.getInstalledEmulators(context)
        if (installed.isEmpty()) return null

        if (preferredPackage != null && installed.any { it.packageName == preferredPackage }) {
            return preferredPackage
        }
        return installed.first().packageName
    }
}
