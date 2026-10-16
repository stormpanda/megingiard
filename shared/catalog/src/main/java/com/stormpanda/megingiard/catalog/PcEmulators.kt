package com.stormpanda.megingiard.catalog

import android.content.Context
import android.content.pm.PackageManager
import com.stormpanda.megingiard.AppLog

private const val TAG = "PcEmulators"

data class PcEmulatorOption(
    val packageName: String,
    val displayName: String,
)

object PcEmulators {
    val SUPPORTED_EMULATOR_OPTIONS: List<PcEmulatorOption> =
        listOf(
            PcEmulatorOption("app.gamenative", "GameNative"),
            PcEmulatorOption("com.utkarshdalal.gamenative", "GameNative (Legacy)"),
            PcEmulatorOption("com.droiddeck.launcher", "DroidDeck"),
        )

    @Volatile
    private var cachedInstalled: List<PcEmulatorOption>? = null

    fun invalidateCache() {
        cachedInstalled = null
    }

    fun getInstalledEmulators(
        context: Context,
        forceRefresh: Boolean = false,
    ): List<PcEmulatorOption> {
        val cached = cachedInstalled
        if (!forceRefresh && cached != null) {
            return cached
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
        AppLog.d(TAG, "Discovered ${installed.size} installed PC emulators: ${installed.map { it.packageName }}")
        cachedInstalled = installed
        return installed
    }
}
