package com.stormpanda.megingiard.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ServerSocket

class PpssppWebSocketClientTest {
    @Test
    fun queryActiveSession_serverNotRunning_returnsNull() =
        runBlocking {
            // Port 59999 should not have a listening server
            val session = PpssppWebSocketClient.queryActiveSession("org.ppsspp.ppsspp", port = 59999)
            assertNull(session)
        }

    @Test
    fun queryActiveSession_withMockWebSocketServer_parsesGameSession() =
        runBlocking {
            val server = ServerSocket(0)
            val port = server.localPort
            val job =
                launch(Dispatchers.IO) {
                    val client = server.accept()
                    val inp = client.getInputStream()
                    val out = client.getOutputStream()

                    // Read handshake
                    val buf = ByteArray(1024)
                    inp.read(buf)

                    // Send 101 Switching Protocols
                    out.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n".toByteArray())
                    out.flush()

                    // Read masked frame
                    inp.read(buf)

                    // Send unmasked text frame with game.status
                    val payload = """{"event":"game.status","game":{"id":"ULES00123","title":"WipEout Pure"}}""".toByteArray(Charsets.UTF_8)
                    val header = byteArrayOf(0x81.toByte(), payload.size.toByte())
                    out.write(header + payload)
                    out.flush()

                    client.close()
                    server.close()
                }

            val session = PpssppWebSocketClient.queryActiveSession("org.ppsspp.ppsspp", port = port)
            job.join()

            assertNotNull(session)
            assertEquals("WipEout Pure", session?.gameTitle)
            assertEquals("psp", session?.systemId)
            assertEquals("ULES00123", session?.titleId)
        }
}
