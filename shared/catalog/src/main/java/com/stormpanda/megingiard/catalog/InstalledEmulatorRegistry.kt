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

    companion object {
        fun forSystem(systemId: String?): InstalledEmulatorRegistry<*>? =
            when (systemId) {
                SYSTEM_ID_SWITCH -> SwitchEmulators
                SYSTEM_ID_PC -> PcEmulators
                else -> null
            }

        fun isMultiEmulatorSystem(systemId: String?): Boolean = forSystem(systemId) != null

        fun invalidateAllCaches() {
            SwitchEmulators.invalidateCache()
            PcEmulators.invalidateCache()
            RetroArchEmulators.invalidateCache()
            PpssppEmulators.invalidateCache()
            Pcsx2Emulators.invalidateCache()
        }
    }
}

data class SimpleEmulatorOption(
    override val packageName: String,
    override val displayName: String,
) : EmulatorOption

object RetroArchEmulators : InstalledEmulatorRegistry<SimpleEmulatorOption>(
    logTag = "RetroArchEmulators",
    systemLabel = "RetroArch",
) {
    override val SUPPORTED_EMULATOR_OPTIONS: List<SimpleEmulatorOption> =
        listOf(
            SimpleEmulatorOption("com.retroarch.aarch64", "RetroArch Plus"),
            SimpleEmulatorOption("com.retroarch", "RetroArch"),
            SimpleEmulatorOption("com.retroarch.ra32", "RetroArch (32-bit)"),
            SimpleEmulatorOption("org.retroarch.aarch64", "RetroArch (64-bit)"),
            SimpleEmulatorOption("org.retroarch", "RetroArch (Official)"),
            SimpleEmulatorOption("org.retroarch.ra32", "RetroArch (Official 32-bit)"),
        )
}

object PpssppEmulators : InstalledEmulatorRegistry<SimpleEmulatorOption>(
    logTag = "PpssppEmulators",
    systemLabel = "PSP",
) {
    override val SUPPORTED_EMULATOR_OPTIONS: List<SimpleEmulatorOption> =
        listOf(
            SimpleEmulatorOption("org.ppsspp.ppsspp", "PPSSPP"),
            SimpleEmulatorOption("org.ppsspp.ppssppgold", "PPSSPP Gold"),
            SimpleEmulatorOption("org.ppsspp.ppsspp.debug", "PPSSPP (Debug)"),
            SimpleEmulatorOption("org.ppsspp.ppssppgold.debug", "PPSSPP Gold (Debug)"),
            SimpleEmulatorOption("org.ppsspp.ppssppdev", "PPSSPP Dev"),
        )
}

object Pcsx2Emulators : InstalledEmulatorRegistry<SimpleEmulatorOption>(
    logTag = "Pcsx2Emulators",
    systemLabel = "PS2",
) {
    override val SUPPORTED_EMULATOR_OPTIONS: List<SimpleEmulatorOption> =
        listOf(
            SimpleEmulatorOption("com.armsx2", "ARMSX2"),
            SimpleEmulatorOption("com.armsx2.debug", "ARMSX2 (Debug)"),
            SimpleEmulatorOption("xyz.aethersx2.android", "AetherSX2"),
            SimpleEmulatorOption("net.nethersx2.android", "NetherSX2"),
        )
}
