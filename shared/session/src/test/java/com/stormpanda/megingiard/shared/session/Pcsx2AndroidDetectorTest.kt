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
}
