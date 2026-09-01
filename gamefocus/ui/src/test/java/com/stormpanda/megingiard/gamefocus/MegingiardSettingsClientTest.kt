package com.stormpanda.megingiard.gamefocus

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MegingiardSettingsClientTest {
    @Test
    fun testObserveSteamGridDbApiToken_returnsFlow() =
        runTest {
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
            hoveredPrimaryColor = 0xFF00FF00.toInt(),
            hoveredSecondaryColor = 0xFF0000FF.toInt(),
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
            com.stormpanda.megingiard.catalog.InstalledAppInfo(
                packageName = "rom.snes.smw",
                activityName = "",
                label = "Super Mario World",
                systemId = "snes",
                romPath = "/storage/roms/snes/smw.smc",
            )
        com.stormpanda.megingiard.catalog.RomManager
            .setRomAppsForTesting(listOf(romApp))

        MegingiardSettingsClient.updateClientState(
            context = context,
            isActive = true,
            focusedPackage = "rom.snes.smw",
            hoveredPackage = "rom.snes.smw",
        )
    }
}
