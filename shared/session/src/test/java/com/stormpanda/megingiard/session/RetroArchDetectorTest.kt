package com.stormpanda.megingiard.session

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RetroArchDetectorTest {
    @Before
    fun setUp() {
        ProcessCmdlineProvider.textFileReader = null
    }

    @After
    fun tearDown() {
        ProcessCmdlineProvider.textFileReader = null
    }

    @Test
    fun supportedPackages_containsAllRetroArchVariants() {
        assertTrue(RetroArchDetector.supportedPackages.contains("com.retroarch"))
        assertTrue(RetroArchDetector.supportedPackages.contains("com.retroarch.aarch64"))
        assertTrue(RetroArchDetector.supportedPackages.contains("com.retroarch.ra32"))
        assertTrue(RetroArchDetector.supportedPackages.contains("org.retroarch"))
        assertTrue(RetroArchDetector.supportedPackages.contains("org.retroarch.aarch64"))
        assertTrue(RetroArchDetector.supportedPackages.contains("org.retroarch.ra32"))
        assertFalse(RetroArchDetector.supportedPackages.contains("com.unsupported.app"))
    }

    @Test
    fun systemId_isRetroarch() {
        assertEquals("retroarch", RetroArchDetector.systemId)
    }

    @Test
    fun detectActiveSession_unsupportedPackage_returnsNull() =
        runTest {
            val result = RetroArchDetector.detectActiveSession("com.unsupported.app")
            assertNull(result)
        }

    @Test
    fun detectActiveSession_whenNoLplFileExists_returnsNull() =
        runTest {
            ProcessCmdlineProvider.textFileReader = { null }
            val result = RetroArchDetector.detectActiveSession("com.retroarch")
            assertNull(result)
        }

    @Test
    fun detectActiveSession_whenValidLplFileExists_resolvesSession() =
        runTest {
            val validLpl =
                """
                {
                  "version": "1.5",
                  "default_core_name": "Snes9x",
                  "items": [
                    {
                      "path": "/storage/emulated/0/ROMs/SNES/Super Mario World.sfc",
                      "label": "Super Mario World (USA)",
                      "core_name": "Nintendo - SNES / SFC (Snes9x)",
                      "db_name": "Nintendo - Super Nintendo Entertainment System.lpl"
                    }
                  ]
                }
                """.trimIndent()

            ProcessCmdlineProvider.textFileReader = { validLpl }
            val result = RetroArchDetector.detectActiveSession("com.retroarch")
            assertNotNull(result)
            assertEquals("com.retroarch", result?.packageName)
            assertEquals("Super Mario World (USA)", result?.gameTitle)
            assertEquals("snes", result?.systemId)
            assertEquals("/storage/emulated/0/ROMs/SNES/Super Mario World.sfc", result?.romPath)
        }
}
