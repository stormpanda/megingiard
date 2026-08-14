package com.stormpanda.megingiard.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmulatorDetectionFunnelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        EmulatorDetectionFunnel.resetForTesting()
    }

    @After
    fun tearDown() {
        EmulatorDetectionFunnel.resetForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun onPackageForeground_unknownPackage_returnsNullAndClearsActiveSession() =
        runTest {
            val result = EmulatorDetectionFunnel.onPackageForeground("com.random.app")
            assertNull(result)
            assertNull(EmulatorDetectionFunnel.activeSession.value)
        }

    @Test
    fun onPackageForeground_unknownPackage_preservesLastDetectedSession() =
        runTest {
            // Unknown app does not clear lastDetectedSession
            EmulatorDetectionFunnel.onPackageForeground("com.random.app")
            assertNull(EmulatorDetectionFunnel.lastDetectedSession.value)
        }

    @Test
    fun onPackageForeground_reusesLastDetectedSession_whenPackageMatchesAndInitialSessionIsNull() =
        runTest {
            val session = ActiveGameSession("com.retroarch", "/roms/z.sfc", "Zelda", "snes")
            EmulatorDetectionFunnel.setActiveSessionForTesting(session)
            EmulatorDetectionFunnel.clearSession()

            assertNull(EmulatorDetectionFunnel.activeSession.value)

            val restored = EmulatorDetectionFunnel.onPackageForeground("com.retroarch")
            assertEquals(session, restored)
            assertEquals(session, EmulatorDetectionFunnel.activeSession.value)
        }

    @Test
    fun clearSession_cancelsPollingAndClearsActiveSession() =
        runTest {
            val session = ActiveGameSession("com.armsx2", "/roms/game.iso", "Game", "ps2")
            EmulatorDetectionFunnel.setActiveSessionForTesting(session)
            assertEquals(session, EmulatorDetectionFunnel.activeSession.value)

            EmulatorDetectionFunnel.clearSession()
            assertNull(EmulatorDetectionFunnel.activeSession.value)
        }
}
