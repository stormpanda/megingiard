package com.stormpanda.megingiard.focus.rom

/**
 * Definition of a gaming system supported for ROM browsing.
 */
data class RomSystemDef(
    val id: String,
    val displayName: String,
    val extensions: Set<String>,
    val emulatorId: String,
    val retroArchCore: String? = null,
)

/**
 * The standard registry of recognized gaming systems.
 */
val SUPPORTED_SYSTEMS =
    listOf(
        RomSystemDef(
            id = "snes",
            displayName = "SNES",
            extensions = setOf("sfc", "smc", "snes"),
            emulatorId = "retroarch",
            retroArchCore = "snes9x_libretro_android.so",
        ),
        RomSystemDef(
            id = "nes",
            displayName = "NES",
            extensions = setOf("nes"),
            emulatorId = "retroarch",
            retroArchCore = "nestopia_libretro_android.so",
        ),
        RomSystemDef(
            id = "gba",
            displayName = "GBA",
            extensions = setOf("gba"),
            emulatorId = "retroarch",
            retroArchCore = "mgba_libretro_android.so",
        ),
        RomSystemDef(
            id = "gb",
            displayName = "GB",
            extensions = setOf("gb", "gbc"),
            emulatorId = "retroarch",
            retroArchCore = "gambatte_libretro_android.so",
        ),
        RomSystemDef(
            id = "n64",
            displayName = "N64",
            extensions = setOf("n64", "z64", "v64"),
            emulatorId = "retroarch",
            retroArchCore = "mupen64plus_next_libretro_android.so",
        ),
        RomSystemDef(
            id = "genesis",
            displayName = "Genesis",
            extensions = setOf("md", "smd", "gen"),
            emulatorId = "retroarch",
            retroArchCore = "genesis_plus_gx_libretro_android.so",
        ),
        RomSystemDef(
            id = "pc",
            displayName = "PC",
            extensions = setOf("exe", "lnk", "desktop", "steam"),
            emulatorId = "gamenative",
        ),
    )
