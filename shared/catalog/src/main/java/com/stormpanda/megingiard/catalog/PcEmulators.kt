package com.stormpanda.megingiard.catalog

private const val TAG = "PcEmulators"

data class PcEmulatorOption(
    override val packageName: String,
    override val displayName: String,
) : EmulatorOption

object PcEmulators : InstalledEmulatorRegistry<PcEmulatorOption>(
    logTag = TAG,
    systemLabel = "PC",
) {
    override val SUPPORTED_EMULATOR_OPTIONS: List<PcEmulatorOption> =
        listOf(
            PcEmulatorOption("app.gamenative", "GameNative"),
            PcEmulatorOption("com.utkarshdalal.gamenative", "GameNative (Legacy)"),
            PcEmulatorOption("com.droiddeck.launcher", "DroidDeck"),
        )
}
