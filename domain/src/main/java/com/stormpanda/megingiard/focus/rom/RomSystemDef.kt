package com.stormpanda.megingiard.focus.rom

import com.stormpanda.megingiard.AppLog

private const val TAG = "RomSystemDef"

/**
 * Definition of a gaming system supported for ROM browsing.
 */
data class RomSystemDef(
    val id: String,
    val displayName: String,
    val extensions: Set<String>,
    val emulatorId: String,
    val retroArchCore: String? = null,
    val retroArchCoreAlternatives: Set<String> = emptySet(),
)

/**
 * The standard registry of recognized gaming systems.
 */
val SUPPORTED_SYSTEMS =
    run {
        AppLog.d(TAG, "Initializing supported systems")
        listOf(
            RomSystemDef(
                id = "snes",
                displayName = "SNES",
                extensions = setOf("sfc", "smc", "snes"),
                emulatorId = "retroarch",
                retroArchCore = "bsnes_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "snes9x_libretro_android.so",
                        "snes9x_2010_libretro_android.so",
                        "snes9x_2005_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "nes",
                displayName = "NES",
                extensions = setOf("nes"),
                emulatorId = "retroarch",
                retroArchCore = "mesen_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "nestopia_libretro_android.so",
                        "fceumm_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "gba",
                displayName = "GBA",
                extensions = setOf("gba"),
                emulatorId = "retroarch",
                retroArchCore = "mgba_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "gpsp_libretro_android.so",
                        "vbam_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "gb",
                displayName = "GB",
                extensions = setOf("gb", "gbc"),
                emulatorId = "retroarch",
                retroArchCore = "sameboy_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "gambatte_libretro_android.so",
                        "gearboy_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "n64",
                displayName = "N64",
                extensions = setOf("n64", "z64", "v64"),
                emulatorId = "retroarch",
                retroArchCore = "mupen64plus_next_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "parallel_n64_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "nds",
                displayName = "Nintendo DS",
                extensions = setOf("nds"),
                emulatorId = "retroarch",
                retroArchCore = "melonds_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "desmume_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "virtualboy",
                displayName = "Virtual Boy",
                extensions = setOf("vb"),
                emulatorId = "retroarch",
                retroArchCore = "beetle_vb_libretro_android.so",
            ),
            RomSystemDef(
                id = "pokemini",
                displayName = "Pokémon Mini",
                extensions = setOf("min"),
                emulatorId = "retroarch",
                retroArchCore = "pokemini_libretro_android.so",
            ),
            RomSystemDef(
                id = "gamecube",
                displayName = "GameCube/Wii",
                extensions = setOf("iso", "gcm", "wbfs", "rvz"),
                emulatorId = "retroarch",
                retroArchCore = "dolphin_libretro_android.so",
            ),
            RomSystemDef(
                id = "n3ds",
                displayName = "Nintendo 3DS",
                extensions = setOf("3ds", "cia"),
                emulatorId = "retroarch",
                retroArchCore = "citra_libretro_android.so",
            ),
            RomSystemDef(
                id = "sms",
                displayName = "Master System",
                extensions = setOf("sms", "gg"),
                emulatorId = "retroarch",
                retroArchCore = "genesis_plus_gx_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "gearsystem_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "genesis",
                displayName = "Genesis",
                extensions = setOf("md", "smd", "gen", "bin", "cue", "chd"),
                emulatorId = "retroarch",
                retroArchCore = "genesis_plus_gx_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "picodrive_libretro_android.so",
                        "blastem_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "sega32x",
                displayName = "Sega 32X",
                extensions = setOf("32x"),
                emulatorId = "retroarch",
                retroArchCore = "picodrive_libretro_android.so",
            ),
            RomSystemDef(
                id = "saturn",
                displayName = "Sega Saturn",
                extensions = setOf("cue", "bin", "chd"),
                emulatorId = "retroarch",
                retroArchCore = "beetle_saturn_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "yabause_libretro_android.so",
                        "kronos_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "dreamcast",
                displayName = "Dreamcast",
                extensions = setOf("gdi", "cdi", "chd"),
                emulatorId = "retroarch",
                retroArchCore = "flycast_libretro_android.so",
            ),
            RomSystemDef(
                id = "ps1",
                displayName = "PlayStation",
                extensions = setOf("cue", "bin", "chd", "pbp"),
                emulatorId = "retroarch",
                retroArchCore = "beetle_psx_hw_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "swanstation_libretro_android.so",
                        "pcsx_rearmed_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "psp",
                displayName = "PSP",
                extensions = setOf("iso", "cso"),
                emulatorId = "retroarch",
                retroArchCore = "ppsspp_libretro_android.so",
            ),
            RomSystemDef(
                id = "ps2",
                displayName = "PlayStation 2",
                extensions = setOf("iso", "chd"),
                emulatorId = "retroarch",
                retroArchCore = "play_libretro_android.so",
            ),
            RomSystemDef(
                id = "arcade",
                displayName = "Arcade",
                extensions = setOf("zip", "7z"),
                emulatorId = "retroarch",
                retroArchCore = "fbneo_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "mame_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "mame",
                displayName = "MAME",
                extensions = setOf("zip", "7z"),
                emulatorId = "retroarch",
                retroArchCore = "mame_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "mame2003_plus_libretro_android.so",
                        "mame2010_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "ngp",
                displayName = "Neo Geo Pocket",
                extensions = setOf("ngp", "ngc"),
                emulatorId = "retroarch",
                retroArchCore = "mednafen_ngp_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "race_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "atari2600",
                displayName = "Atari 2600",
                extensions = setOf("a26", "bin"),
                emulatorId = "retroarch",
                retroArchCore = "stella_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "stella2014_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "atari5200",
                displayName = "Atari 5200",
                extensions = setOf("a52", "bin"),
                emulatorId = "retroarch",
                retroArchCore = "a5200_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "atari800_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "atari7800",
                displayName = "Atari 7800",
                extensions = setOf("a78", "bin"),
                emulatorId = "retroarch",
                retroArchCore = "prosystem_libretro_android.so",
            ),
            RomSystemDef(
                id = "lynx",
                displayName = "Atari Lynx",
                extensions = setOf("lnx"),
                emulatorId = "retroarch",
                retroArchCore = "handy_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "mednafen_lynx_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "jaguar",
                displayName = "Atari Jaguar",
                extensions = setOf("j64", "jag"),
                emulatorId = "retroarch",
                retroArchCore = "virtualjaguar_libretro_android.so",
            ),
            RomSystemDef(
                id = "dos",
                displayName = "MS-DOS",
                extensions = setOf("exe", "com", "bat", "conf"),
                emulatorId = "retroarch",
                retroArchCore = "dosbox_pure_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "dosbox_svn_libretro_android.so",
                        "dosbox_core_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "msx",
                displayName = "MSX",
                extensions = setOf("rom", "mx1", "mx2"),
                emulatorId = "retroarch",
                retroArchCore = "fmsx_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "bluemsx_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "c64",
                displayName = "Commodore 64",
                extensions = setOf("d64", "g64", "prg", "t64"),
                emulatorId = "retroarch",
                retroArchCore = "vice_x64sc_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "vice_x64_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "amiga",
                displayName = "Amiga",
                extensions = setOf("adf", "dms", "ipf", "lha"),
                emulatorId = "retroarch",
                retroArchCore = "puae_libretro_android.so",
            ),
            RomSystemDef(
                id = "zxspectrum",
                displayName = "ZX Spectrum",
                extensions = setOf("tzx", "tap", "z80", "scl", "trd"),
                emulatorId = "retroarch",
                retroArchCore = "fuse_libretro_android.so",
            ),
            RomSystemDef(
                id = "pce",
                displayName = "PC Engine",
                extensions = setOf("pce", "sgx", "cue"),
                emulatorId = "retroarch",
                retroArchCore = "mednafen_pce_fast_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "mednafen_pce_libretro_android.so",
                        "supergrafx_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "pcfx",
                displayName = "PC-FX",
                extensions = setOf("cue", "bin"),
                emulatorId = "retroarch",
                retroArchCore = "mednafen_pcfx_libretro_android.so",
            ),
            RomSystemDef(
                id = "colecovision",
                displayName = "ColecoVision",
                extensions = setOf("col", "bin"),
                emulatorId = "retroarch",
                retroArchCore = "gearcoleco_libretro_android.so",
                retroArchCoreAlternatives =
                    setOf(
                        "bluemsx_libretro_android.so",
                    ),
            ),
            RomSystemDef(
                id = "vectrex",
                displayName = "Vectrex",
                extensions = setOf("vec", "bin"),
                emulatorId = "retroarch",
                retroArchCore = "vecx_libretro_android.so",
            ),
            RomSystemDef(
                id = "wswan",
                displayName = "WonderSwan",
                extensions = setOf("ws", "wsc"),
                emulatorId = "retroarch",
                retroArchCore = "mednafen_wswan_libretro_android.so",
            ),
            RomSystemDef(
                id = "neogeocd",
                displayName = "Neo Geo CD",
                extensions = setOf("cue", "chd"),
                emulatorId = "retroarch",
                retroArchCore = "neocd_libretro_android.so",
            ),
            RomSystemDef(
                id = "scummvm",
                displayName = "ScummVM",
                extensions = setOf("scummvm"),
                emulatorId = "retroarch",
                retroArchCore = "scummvm_libretro_android.so",
            ),
            RomSystemDef(
                id = "pc",
                displayName = "PC",
                extensions = setOf("steam", "steamappid"),
                emulatorId = "gamenative",
            ),
        )
    }
