package com.stormpanda.megingiard.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PpssppDetectorTest {
    @Test
    fun supportedPackages_containsAllPPSSPPVariants() {
        assertTrue(PpssppDetector.supportedPackages.contains("org.ppsspp.ppsspp"))
        assertTrue(PpssppDetector.supportedPackages.contains("org.ppsspp.ppssppgold"))
        assertTrue(PpssppDetector.supportedPackages.contains("org.ppsspp.ppsspp.debug"))
        assertTrue(PpssppDetector.supportedPackages.contains("org.ppsspp.ppssppgold.debug"))
        assertTrue(PpssppDetector.supportedPackages.contains("org.ppsspp.ppssppdev"))
        assertFalse(PpssppDetector.supportedPackages.contains("com.unsupported.emulator"))
    }

    @Test
    fun systemId_isPsp() {
        assertEquals("psp", PpssppDetector.systemId)
    }

    @Test
    fun detectActiveSession_unsupportedPackage_returnsNull() =
        runBlocking {
            val result = PpssppDetector.detectActiveSession("com.unsupported.emulator")
            assertNull(result)
        }
}
