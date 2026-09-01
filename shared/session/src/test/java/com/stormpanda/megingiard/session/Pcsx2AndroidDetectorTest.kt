package com.stormpanda.megingiard.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Pcsx2AndroidDetectorTest {
    @Test
    fun supportedPackages_containsExpectedPcsx2AndroidVariants() {
        assertTrue(Pcsx2AndroidDetector.supportedPackages.contains("com.armsx2"))
        assertTrue(Pcsx2AndroidDetector.supportedPackages.contains("com.armsx2.debug"))
        assertTrue(Pcsx2AndroidDetector.supportedPackages.contains("xyz.aethersx2.android"))
        assertTrue(Pcsx2AndroidDetector.supportedPackages.contains("net.nethersx2.android"))
        assertFalse(Pcsx2AndroidDetector.supportedPackages.contains("com.unsupported.emulator"))
    }

    @Test
    fun systemId_isPs2() {
        assertEquals("ps2", Pcsx2AndroidDetector.systemId)
    }

    @Test
    fun detectActiveSession_unsupportedPackage_returnsNull() =
        runTest {
            val result = Pcsx2AndroidDetector.detectActiveSession("com.unsupported.emulator")
            assertNull(result)
        }

    @Test
    fun detectActiveSession_whenNoRecentGamesFile_returnsNull() =
        runTest {
            ProcessCmdlineProvider.textFileReader = { null }
            val result = Pcsx2AndroidDetector.detectActiveSession("xyz.aethersx2.android")
            assertNull(result)
        }

    @Test
    fun detectActiveSession_whenRecentGamesFileExists_resolvesSession() =
        runTest {
            val validJson =
                """
                [
                  {
                    "uri": "content://com.android.externalstorage.documents/tree/primary%3APS2/document/primary%3APS2%2FFinal%20Fantasy%20X.iso",
                    "title": "Final Fantasy X",
                    "serial": "SLUS-20312"
                  }
                ]
                """.trimIndent()

            ProcessCmdlineProvider.textFileReader = { validJson }
            val result = Pcsx2AndroidDetector.detectActiveSession("xyz.aethersx2.android")
            assertEquals("xyz.aethersx2.android", result?.packageName)
            assertEquals("Final Fantasy X", result?.gameTitle)
            assertEquals("ps2", result?.systemId)
            assertEquals("/storage/emulated/0/PS2/Final Fantasy X.iso", result?.romPath)
        }
}
