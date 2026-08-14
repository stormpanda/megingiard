package com.stormpanda.megingiard.session

import org.junit.Assert.assertNull
import org.junit.Test

class PpssppWebSocketClientTest {
    @Test
    fun queryActiveSession_serverNotRunning_returnsNull() {
        // Port 59999 should not have a listening server
        val session = PpssppWebSocketClient.queryActiveSession("org.ppsspp.ppsspp", port = 59999)
        assertNull(session)
    }
}
