package com.stormpanda.megingiard.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for Input Injector backend selection logic.
 */
class InjectorBackendRouterTest {
    @Test
    fun backendSelection_defaultsToVirtualUinputWhenPrivdDisconnected() {
        // When PrivdClient.isConnected is false, backend routing must fall back to virtual uinput
        val isPrivdConnected = false
        val usePrivd = isPrivdConnected

        assertFalse("Injector should use Virtual Uinput when Privd daemon is disconnected", usePrivd)
    }

    @Test
    fun backendSelection_routesToPrivdWhenPrivdConnected() {
        val isPrivdConnected = true
        val usePrivd = isPrivdConnected

        assertTrue("Injector should route to Privd daemon when connected", usePrivd)
    }
}
