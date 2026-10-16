package com.stormpanda.megingiard.catalog

import android.content.Context
import android.content.pm.PackageManager
import com.stormpanda.megingiard.AppLog

private const val TAG = "EmulatorRegistry"

interface EmulatorOption {
    val packageName: String
    val displayName: String
}

abstract class InstalledEmulatorRegistry<T : EmulatorOption>(
    private val logTag: String,
    private val systemLabel: String,
) {
    abstract val SUPPORTED_EMULATOR_OPTIONS: List<T>

    @Volatile
    private var cachedInstalled: List<T>? = null

    fun invalidateCache() {
        cachedInstalled = null
        AppLog.d(TAG, "Invalidated $systemLabel emulator cache")
    }

    fun getInstalledEmulators(
        context: Context,
        forceRefresh: Boolean = false,
    ): List<T> {
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
        AppLog.d(logTag, "Discovered ${installed.size} installed $systemLabel emulators: ${installed.map { it.packageName }}")
        cachedInstalled = installed
        return installed
    }
}
