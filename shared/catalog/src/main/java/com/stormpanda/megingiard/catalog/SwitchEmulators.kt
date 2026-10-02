package com.stormpanda.megingiard.catalog

import android.content.Context
import android.content.pm.PackageManager
import com.stormpanda.megingiard.AppLog

private const val TAG = "SwitchEmulators"

data class SwitchEmulatorOption(
    val packageName: String,
    val displayName: String,
)

object SwitchEmulators {
    val SUPPORTED_EMULATOR_OPTIONS: List<SwitchEmulatorOption> =
        listOf(
            SwitchEmulatorOption("dev.eden.eden_emulator.dualscreen", "Eden DS"),
            SwitchEmulatorOption("dev.eden.eden_emulator.dualscreen.debug", "Eden DS (Debug)"),
            SwitchEmulatorOption("dev.eden.eden_emulator.dualscreen.nightly", "Eden DS Nightly"),
            SwitchEmulatorOption("dev.eden.eden_emulator.dualscreen.nightly.debug", "Eden DS Nightly (Debug)"),
            SwitchEmulatorOption("dev.eden.eden_emulator", "Eden Emulator"),
            SwitchEmulatorOption("dev.eden.eden_emulator.debug", "Eden Emulator (Debug)"),
            SwitchEmulatorOption("dev.eden.eden_emulator.nightly", "Eden Nightly"),
            SwitchEmulatorOption("dev.eden.eden_emulator.nightly.debug", "Eden Nightly (Debug)"),
            SwitchEmulatorOption("dev.legacy.eden_emulator", "Eden Legacy"),
            SwitchEmulatorOption("dev.legacy.eden_emulator.debug", "Eden Legacy (Debug)"),
            SwitchEmulatorOption("dev.legacy.eden_emulator.nightly", "Eden Legacy Nightly"),
            SwitchEmulatorOption("dev.legacy.eden_emulator.nightly.debug", "Eden Legacy Nightly (Debug)"),
            SwitchEmulatorOption("org.citron.citron_emu", "Citron Emulator"),
            SwitchEmulatorOption("org.citron.citron_emu.debug", "Citron Emulator (Debug)"),
            SwitchEmulatorOption("org.sudachi.sudachi_emu", "Sudachi Emulator"),
            SwitchEmulatorOption("com.suyu.suyu", "Suyu Emulator"),
            SwitchEmulatorOption("org.yuzu.yuzu_emu", "Yuzu Emulator"),
            SwitchEmulatorOption("org.yuzu.yuzu_emu.ea", "Yuzu Early Access"),
        )

    @Volatile
    private var cachedInstalled: List<SwitchEmulatorOption>? = null

    fun invalidateCache() {
        cachedInstalled = null
    }

    fun getInstalledEmulators(
        context: Context,
        forceRefresh: Boolean = false,
    ): List<SwitchEmulatorOption> {
        if (!forceRefresh && cachedInstalled != null) {
            return cachedInstalled!!
        }
        val pm = context.packageManager
        val installed =
            SUPPORTED_EMULATOR_OPTIONS.filter { option ->
                try {
                    pm.getPackageInfo(option.packageName, PackageManager.PackageInfoFlags.of(0))
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
        AppLog.d(TAG, "Discovered ${installed.size} installed Switch emulators: ${installed.map { it.packageName }}")
        cachedInstalled = installed
        return installed
    }
}
