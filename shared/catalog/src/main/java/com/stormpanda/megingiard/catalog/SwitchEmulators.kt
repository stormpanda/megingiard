package com.stormpanda.megingiard.catalog

private const val TAG = "SwitchEmulators"

data class SwitchEmulatorOption(
    override val packageName: String,
    override val displayName: String,
    val logBrand: String = "yuzu",
) : EmulatorOption

object SwitchEmulators : InstalledEmulatorRegistry<SwitchEmulatorOption>(
    logTag = TAG,
    systemLabel = "Switch",
) {
    override val SUPPORTED_EMULATOR_OPTIONS: List<SwitchEmulatorOption> =
        listOf(
            SwitchEmulatorOption("dev.eden.eden_emulator.dualscreen", "Eden DS", "eden"),
            SwitchEmulatorOption("dev.eden.eden_emulator.dualscreen.debug", "Eden DS (Debug)", "eden"),
            SwitchEmulatorOption("dev.eden.eden_emulator.dualscreen.nightly", "Eden DS Nightly", "eden"),
            SwitchEmulatorOption("dev.eden.eden_emulator.dualscreen.nightly.debug", "Eden DS Nightly (Debug)", "eden"),
            SwitchEmulatorOption("dev.eden.eden_emulator", "Eden Emulator", "eden"),
            SwitchEmulatorOption("dev.eden.eden_emulator.debug", "Eden Emulator (Debug)", "eden"),
            SwitchEmulatorOption("dev.eden.eden_emulator.nightly", "Eden Nightly", "eden"),
            SwitchEmulatorOption("dev.eden.eden_emulator.nightly.debug", "Eden Nightly (Debug)", "eden"),
            SwitchEmulatorOption("dev.legacy.eden_emulator", "Eden Legacy", "eden"),
            SwitchEmulatorOption("dev.legacy.eden_emulator.debug", "Eden Legacy (Debug)", "eden"),
            SwitchEmulatorOption("dev.legacy.eden_emulator.nightly", "Eden Legacy Nightly", "eden"),
            SwitchEmulatorOption("dev.legacy.eden_emulator.nightly.debug", "Eden Legacy Nightly (Debug)", "eden"),
            SwitchEmulatorOption("org.citron.citron_emu", "Citron Emulator", "citron"),
            SwitchEmulatorOption("org.citron.citron_emu.debug", "Citron Emulator (Debug)", "citron"),
            SwitchEmulatorOption("org.sudachi.sudachi_emu", "Sudachi Emulator", "sudachi"),
            SwitchEmulatorOption("com.suyu.suyu", "Suyu Emulator", "suyu"),
            SwitchEmulatorOption("org.yuzu.yuzu_emu", "Yuzu Emulator", "yuzu"),
            SwitchEmulatorOption("org.yuzu.yuzu_emu.ea", "Yuzu Early Access", "yuzu"),
        )

    fun getLogBrand(packageName: String): String =
        SUPPORTED_EMULATOR_OPTIONS.firstOrNull { it.packageName == packageName }?.logBrand ?: "yuzu"
}
