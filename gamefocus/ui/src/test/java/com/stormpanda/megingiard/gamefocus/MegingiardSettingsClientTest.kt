package com.stormpanda.megingiard.gamefocus

import android.content.Context
import android.content.pm.PackageInfo
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.InstalledAppInfo
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SYSTEM_ID_SWITCH
import com.stormpanda.megingiard.catalog.SwitchEmulators
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val TAG = "MegingiardSettingsTest"
private const val TEST_PRIMARY_COLOR = 0xFF00FF00.toInt()
private const val TEST_SECONDARY_COLOR = 0xFF0000FF.toInt()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MegingiardSettingsClientTest {
    @Test
    fun testObserveSteamGridDbApiToken_returnsFlow() =
        runTest {
            AppLog.d(TAG, "testObserveSteamGridDbApiToken_returnsFlow")
            val context: Context = RuntimeEnvironment.getApplication()
            val token = MegingiardSettingsClient.observeSteamGridDbApiToken(context).first()
            assertNotNull(token)
        }

    @Test
    fun testUpdateClientState_doesNotThrow() {
        val context: Context = RuntimeEnvironment.getApplication()
        MegingiardSettingsClient.updateClientState(
            context = context,
            isActive = true,
            focusedPackage = "com.retroarch.aarch64",
            focusedRomPath = "/storage/roms/snes/game.smc",
            hoveredPackage = "com.retroarch.aarch64",
            hoveredLabel = "Super Mario World",
            hoveredRomPath = "/storage/roms/snes/game.smc",
            hoveredSystemId = "snes",
            hoveredPrimaryColor = TEST_PRIMARY_COLOR,
            hoveredSecondaryColor = TEST_SECONDARY_COLOR,
        )

        MegingiardSettingsClient.updateClientState(
            context = context,
            isActive = false,
        )
    }

    @Test
    fun testUpdateClientState_withRomPackage() {
        val context: Context = RuntimeEnvironment.getApplication()
        val romApp =
            InstalledAppInfo(
                packageName = "rom.snes.smw",
                activityName = "",
                label = "Super Mario World",
                systemId = "snes",
                romPath = "/storage/roms/snes/smw.smc",
            )
        RomManager.setRomAppsForTesting(listOf(romApp))

        MegingiardSettingsClient.updateClientState(
            context = context,
            isActive = true,
            focusedPackage = "rom.snes.smw",
            hoveredPackage = "rom.snes.smw",
        )
    }

    @Test
    fun testDispatchClientState_runsIpcBodySynchronouslyWithoutThrowing() {
        val context: Context = RuntimeEnvironment.getApplication()
        MegingiardSettingsClient.dispatchClientState(
            ClientStateRequest(
                appContext = context,
                isActive = true,
                focusedPackage = null,
                focusedRomPath = null,
                focusedRomIdentifier = null,
                hoveredPackage = "com.retroarch.aarch64",
                hoveredLabel = "Super Mario World",
                hoveredRomPath = "/storage/roms/snes/game.smc",
                hoveredRomIdentifier = null,
                hoveredSystemId = "snes",
                hoveredPrimaryColor = TEST_PRIMARY_COLOR,
                hoveredSecondaryColor = null,
            ),
        )
    }

    @Test
    fun testGetActualPackageName_switchRomResolvesInstalledAndPreferredEmulator() {
        val context: Context = RuntimeEnvironment.getApplication()
        val shadowPm = shadowOf(context.packageManager)

        // When no Switch emulator is installed and no preferred package is set, falls back to first supported option
        assertEquals(
            SwitchEmulators.SUPPORTED_EMULATOR_OPTIONS.first().packageName,
            MegingiardSettingsClient.getActualPackageName(context, SYSTEM_ID_SWITCH),
        )

        // Install Sudachi and Citron; without preferred package, picks first installed in SUPPORTED_EMULATOR_OPTIONS order
        shadowPm.installPackage(PackageInfo().apply { packageName = "org.sudachi.sudachi_emu" })
        shadowPm.installPackage(PackageInfo().apply { packageName = "org.citron.citron_emu" })
        assertEquals(
            "org.citron.citron_emu",
            MegingiardSettingsClient.getActualPackageName(context, SYSTEM_ID_SWITCH),
        )

        // With preferred emulatorPackage installed, picks preferred package even if listed later
        assertEquals(
            "org.sudachi.sudachi_emu",
            MegingiardSettingsClient.getActualPackageName(
                context = context,
                systemId = SYSTEM_ID_SWITCH,
                emulatorPackage = "org.sudachi.sudachi_emu",
            ),
        )
    }
}
