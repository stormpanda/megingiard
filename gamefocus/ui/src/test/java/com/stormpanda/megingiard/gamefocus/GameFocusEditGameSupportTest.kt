package com.stormpanda.megingiard.gamefocus

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.PcEmulatorOption
import com.stormpanda.megingiard.catalog.SYSTEM_ID_PC
import com.stormpanda.megingiard.catalog.SYSTEM_ID_SWITCH
import com.stormpanda.megingiard.catalog.SwitchEmulatorOption
import com.stormpanda.megingiard.media.SteamGridDbException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TAG = "GFEditGameSupportTest"

class GameFocusEditGameSupportTest {
    @Test
    fun calculateInSampleSize_returnsOneWhenSourceSmallerThanTarget() {
        AppLog.d(TAG, "calculateInSampleSize: small source")
        assertEquals(1, calculateInSampleSize(srcWidth = 100, srcHeight = 150, reqWidth = 200, reqHeight = 300))
    }

    @Test
    fun calculateInSampleSize_returnsLargestPowerOfTwoKeepingTargetSize() {
        AppLog.d(TAG, "calculateInSampleSize: power-of-two downsampling")
        // 600x900 SteamGridDB grid -> 133x200 thumbnail: /4 yields 150x225 (>= target), /8 would undershoot.
        assertEquals(4, calculateInSampleSize(srcWidth = 600, srcHeight = 900, reqWidth = 133, reqHeight = 200))
        assertEquals(2, calculateInSampleSize(srcWidth = 400, srcHeight = 600, reqWidth = 200, reqHeight = 300))
    }

    @Test
    fun calculateInSampleSize_returnsOneForInvalidDimensions() {
        AppLog.d(TAG, "calculateInSampleSize: invalid dimensions")
        assertEquals(1, calculateInSampleSize(srcWidth = 0, srcHeight = 900, reqWidth = 133, reqHeight = 200))
        assertEquals(1, calculateInSampleSize(srcWidth = 600, srcHeight = 900, reqWidth = 0, reqHeight = 200))
    }

    @Test
    fun steamGridDbErrorMessageRes_mapsEachFailureToLocalizedString() {
        AppLog.d(TAG, "steamGridDbErrorMessageRes: mapping")
        assertEquals(R.string.steamgriddb_error_offline, steamGridDbErrorMessageRes(SteamGridDbException.Offline))
        assertEquals(R.string.steamgriddb_error_rate_limited, steamGridDbErrorMessageRes(SteamGridDbException.RateLimited))
        assertEquals(
            R.string.steamgriddb_error_unavailable,
            steamGridDbErrorMessageRes(SteamGridDbException.ServiceUnavailable),
        )
        assertEquals(
            R.string.steamgriddb_error_unauthorized,
            steamGridDbErrorMessageRes(SteamGridDbException.Unauthorized()),
        )
        assertEquals(R.string.steamgriddb_error_generic, steamGridDbErrorMessageRes(SteamGridDbException.ApiError("boom")))
        assertEquals(R.string.steamgriddb_error_generic, steamGridDbErrorMessageRes(IllegalStateException("x")))
        assertEquals(R.string.steamgriddb_error_generic, steamGridDbErrorMessageRes(null))
    }

    @Test
    fun buildRomRunnerChoices_nonRomApp_returnsEmptyList() {
        val androidApp =
            InstalledAppInfo(
                packageName = "com.example.game",
                activityName = "MainActivity",
                label = "Example",
                isRom = false,
            )
        val choices =
            buildRomRunnerChoices(
                appInfo = androidApp,
                installedSwitchEmulators = emptyList(),
                installedPcEmulators = emptyList(),
                formatFolderDefault = { "Folder Default ($it)" },
            )
        assertTrue(choices.isEmpty())
    }

    @Test
    fun buildRomRunnerChoices_pcRom_includesFolderDefaultAndInstalledPcEmulators() {
        val pcRom =
            InstalledAppInfo(
                packageName = "rom.pc.portal_1",
                activityName = "",
                label = "Portal",
                isRom = true,
                systemId = SYSTEM_ID_PC,
                emulatorPackage = "com.droiddeck.launcher",
                defaultEmulatorPackage = "com.droiddeck.launcher",
            )
        val installedPc =
            listOf(
                PcEmulatorOption("app.gamenative", "GameNative"),
                PcEmulatorOption("com.droiddeck.launcher", "DroidDeck"),
            )
        val choices =
            buildRomRunnerChoices(
                appInfo = pcRom,
                installedSwitchEmulators = emptyList(),
                installedPcEmulators = installedPc,
                formatFolderDefault = { "Folder Default ($it)" },
            )
        assertEquals(3, choices.size)
        assertNull(choices[0].overrideValue)
        assertEquals("Folder Default (DroidDeck)", choices[0].displayLabel)
        assertEquals(RomRunnerChoice("app.gamenative", "GameNative"), choices[1])
        assertEquals(RomRunnerChoice("com.droiddeck.launcher", "DroidDeck"), choices[2])
    }

    @Test
    fun buildRomRunnerChoices_switchRom_includesFolderDefaultAndInstalledSwitchEmulators() {
        val switchRom =
            InstalledAppInfo(
                packageName = "rom.switch.celeste_1",
                activityName = "",
                label = "Celeste",
                isRom = true,
                systemId = SYSTEM_ID_SWITCH,
                emulatorPackage = "dev.eden.eden_emulator",
                defaultEmulatorPackage = "dev.eden.eden_emulator",
            )
        val installedSwitch =
            listOf(
                SwitchEmulatorOption("dev.eden.eden_emulator", "Eden Emulator"),
                SwitchEmulatorOption("org.citron.citron_emu", "Citron Emulator"),
            )
        val choices =
            buildRomRunnerChoices(
                appInfo = switchRom,
                installedSwitchEmulators = installedSwitch,
                installedPcEmulators = emptyList(),
                formatFolderDefault = { "Folder Default ($it)" },
            )
        assertEquals(3, choices.size)
        assertEquals(RomRunnerChoice(null, "Folder Default (Eden Emulator)"), choices[0])
        assertEquals(RomRunnerChoice("dev.eden.eden_emulator", "Eden Emulator"), choices[1])
        assertEquals(RomRunnerChoice("org.citron.citron_emu", "Citron Emulator"), choices[2])
    }

    @Test
    fun buildRomRunnerChoices_retroArchRom_includesFolderDefaultAndAllCores() {
        val snesRom =
            InstalledAppInfo(
                packageName = "rom.snes.smw_1",
                activityName = "",
                label = "Super Mario World",
                isRom = true,
                systemId = "snes",
                retroArchCore = "snes9x_libretro_android.so",
                defaultRetroArchCore = "snes9x_libretro_android.so",
            )
        val choices =
            buildRomRunnerChoices(
                appInfo = snesRom,
                installedSwitchEmulators = emptyList(),
                installedPcEmulators = emptyList(),
                formatFolderDefault = { "Folder Default ($it)" },
            )
        assertEquals(5, choices.size)
        assertEquals(RomRunnerChoice(null, "Folder Default (snes9x_libretro_android.so)"), choices[0])
        assertEquals(RomRunnerChoice("bsnes_libretro_android.so", "bsnes_libretro_android.so"), choices[1])
        assertEquals(RomRunnerChoice("snes9x_libretro_android.so", "snes9x_libretro_android.so"), choices[2])
    }
}
