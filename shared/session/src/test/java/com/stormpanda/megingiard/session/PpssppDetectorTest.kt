package com.stormpanda.megingiard.session

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PpssppDetectorTest {
    @Before
    fun setUp() {
        PpssppDetector.webSocketPort = 59999
    }

    @After
    fun tearDown() {
        PpssppDetector.webSocketPort = 8080
    }

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

    @Test
    fun isTitleMatching_matchingTitle_returnsTrue() {
        val candidate = "God of War - Chains of Olympus (Europe, Australia) (En,Fr,De,Es,It).iso"
        val activeTitle = "God of War: Chains of Olympus"
        assertTrue(PpssppDetector.isTitleMatching(candidate, activeTitle))
    }

    @Test
    fun isTitleMatching_staleDifferentGame_returnsFalse() {
        val candidate = "Virtua Tennis - World Tour (Europe).iso"
        val activeTitle = "God of War: Chains of Olympus"
        assertFalse(PpssppDetector.isTitleMatching(candidate, activeTitle))
    }
}
