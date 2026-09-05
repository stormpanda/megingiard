package com.stormpanda.megingiard.privd

import com.stormpanda.megingiard.security.HmacUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/**
 * End-to-End integration test suite verifying the Privileged Mode Daemon (privd)
 * TCP protocol transport pipeline:
 *
 * 1. Mutual challenge-response HMAC-SHA256 handshake.
 * 2. Strict protocol version negotiation ([PrivdConstants.PRIVD_VERSION]).
 * 3. Command multiplexing and multi-line framing (PING, READ_FILE, LIST_PROCESSES).
 * 4. High-frequency evdev event streaming.
 * 5. Tamper detection and version mismatch rejection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivdProtocolHandshakePipelineE2ETest {
    private val testKey = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    private var testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        PrivdClient.disconnect()
        PrivdClient.setKey(testKey)
    }

    @After
    fun tearDown() {
        PrivdClient.disconnect()
        testScope.cancel()
    }

    @Test
    fun testMutualHmacHandshakeAndCommandPipelineE2E() =
        runBlocking {
            val serverSocket = ServerSocket(0)
            val port = serverSocket.localPort
            PrivdClient.setPortRangeForTesting(port, port)

            val serverJob =
                testScope.launch {
                    val clientSocket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                    val writer = BufferedWriter(OutputStreamWriter(clientSocket.outputStream))

                    // 1. Daemon sends CHAL
                    val serverNonce = ByteArray(16).apply { SecureRandom().nextBytes(this) }
                    val serverNonceHex = HmacUtil.bytesToHex(serverNonce)
                    writer.write("CHAL $serverNonceHex\n")
                    writer.flush()

                    // 2. Client responds with AUTH
                    val authLine = reader.readLine()
                    assertNotNull(authLine)
                    assertTrue(authLine!!.startsWith("AUTH "))
                    val clientAuthHmac = authLine.substring(5)
                    val expectedAuthHmac = HmacUtil.computeHmacHex(testKey, serverNonce)
                    assertTrue(HmacUtil.constantTimeEqualsHex(clientAuthHmac, expectedAuthHmac))

                    // 3. Daemon accepts with OK
                    writer.write("OK\n")
                    writer.flush()

                    // 4. Client challenges Daemon with VERIFY
                    val verifyLine = reader.readLine()
                    assertNotNull(verifyLine)
                    assertTrue(verifyLine!!.startsWith("VERIFY "))
                    val clientNonceHex = verifyLine.substring(7)
                    val clientNonce = HmacUtil.hexToBytes(clientNonceHex)

                    // 5. Daemon proves identity with PROOF
                    val daemonProofHmac = HmacUtil.computeHmacHex(testKey, clientNonce)
                    writer.write("PROOF $daemonProofHmac\n")
                    writer.flush()

                    // 6. Client sends VERSION check
                    val versionLine = reader.readLine()
                    assertNotNull(versionLine)
                    assertEquals("VERSION ${PrivdConstants.PRIVD_VERSION}", versionLine)

                    // 7. Daemon confirms version
                    writer.write("VERSION_OK ${PrivdConstants.PRIVD_VERSION}\n")
                    writer.flush()

                    // Daemon command handling loop
                    while (!clientSocket.isClosed) {
                        val cmd = reader.readLine() ?: break
                        when {
                            cmd == "PING" -> {
                                writer.write("PONG\n")
                                writer.flush()
                            }

                            cmd.startsWith("READ_FILE ") -> {
                                writer.write("READ_BEGIN\n")
                                writer.write("root:x:0:0:root:/root:/bin/sh\n")
                                writer.write("daemon:x:1:1:daemon:/usr/sbin:/bin/sh\n")
                                writer.write("READ_END\n")
                                writer.flush()
                            }

                            cmd == "LIST_PROCESSES" -> {
                                writer.write("PROC_BEGIN\n")
                                writer.write("1 /system/bin/init\n")
                                writer.write("100 /system/bin/surfaceflinger\n")
                                writer.write("PROC_END\n")
                                writer.flush()
                            }

                            cmd == "SUB GAMEPAD" -> {
                                // Stream an evdev event
                                writer.write("EVT 3 0 1024\n")
                                writer.flush()
                            }

                            cmd == "SUB TOUCH" -> {
                                // Stream a touch evdev event
                                writer.write("EVT_TOUCH 3 53 2048\n")
                                writer.flush()
                            }
                        }
                    }
                }

            // Client connects
            val connectResult = PrivdClient.connect()
            assertTrue("Expected PrivdClient to connect and complete mutual handshake", connectResult)
            assertEquals(PrivdConnectionState.CONNECTED, PrivdClient.state.value)

            // Test PING
            val pong = PrivdClient.ping()
            assertTrue("Expected PONG from daemon", pong)

            // Test READ_FILE multi-line framing
            val fileContent = PrivdClient.readTextFile("/etc/passwd")
            assertNotNull(fileContent)
            assertTrue(fileContent!!.contains("root:x:0:0:root"))
            assertTrue(fileContent.contains("daemon:x:1:1:daemon"))

            // Test LIST_PROCESSES multi-line framing
            val procContent = PrivdClient.getRunningProcesses()
            assertNotNull(procContent)
            assertTrue(procContent!!.contains("/system/bin/surfaceflinger"))

            // Test EVDEV streaming
            val touchDeferred =
                async(Dispatchers.Default) {
                    PrivdClient.touchEvdevEvents.first()
                }
            delay(50)
            PrivdClient.subscribeTouch()
            val touchEvent =
                withTimeoutOrNull(2000) {
                    touchDeferred.await()
                }
            assertNotNull("Expected EVT_TOUCH event from daemon", touchEvent)
            assertEquals(3, touchEvent?.type)
            assertEquals(53, touchEvent?.code)
            assertEquals(2048, touchEvent?.value)

            // Cleanup
            PrivdClient.disconnect()
            assertEquals(PrivdConnectionState.DISCONNECTED, PrivdClient.state.value)
            serverJob.cancel()
            serverSocket.close()
        }

    @Test
    fun testVersionMismatchRejectionE2E() =
        runBlocking {
            val serverSocket = ServerSocket(0)
            val port = serverSocket.localPort
            PrivdClient.setPortRangeForTesting(port, port)

            val serverJob =
                testScope.launch {
                    val clientSocket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                    val writer = BufferedWriter(OutputStreamWriter(clientSocket.outputStream))

                    // 1. Daemon sends CHAL
                    val serverNonce = ByteArray(16).apply { SecureRandom().nextBytes(this) }
                    writer.write("CHAL ${HmacUtil.bytesToHex(serverNonce)}\n")
                    writer.flush()

                    // 2. Client AUTH
                    reader.readLine()
                    writer.write("OK\n")
                    writer.flush()

                    // 3. Client VERIFY
                    val verifyLine = reader.readLine()!!
                    val clientNonce = HmacUtil.hexToBytes(verifyLine.substring(7))
                    writer.write("PROOF ${HmacUtil.computeHmacHex(testKey, clientNonce)}\n")
                    writer.flush()

                    // 4. Client VERSION check
                    reader.readLine()

                    // Daemon responds with an OLD incompatible version
                    writer.write("VERSION_MISMATCH 1\n")
                    writer.flush()
                    clientSocket.close()
                }

            val connectResult = PrivdClient.connect()
            assertFalse("PrivdClient must reject daemon on version mismatch", connectResult)
            assertEquals(PrivdConnectionState.DISCONNECTED, PrivdClient.state.value)

            serverJob.cancel()
            serverSocket.close()
        }

    @Test
    fun testInvalidHmacProofRejectionE2E() =
        runBlocking {
            val serverSocket = ServerSocket(0)
            val port = serverSocket.localPort
            PrivdClient.setPortRangeForTesting(port, port)

            val serverJob =
                testScope.launch {
                    val clientSocket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(clientSocket.inputStream))
                    val writer = BufferedWriter(OutputStreamWriter(clientSocket.outputStream))

                    // 1. Daemon sends CHAL
                    val serverNonce = ByteArray(16).apply { SecureRandom().nextBytes(this) }
                    writer.write("CHAL ${HmacUtil.bytesToHex(serverNonce)}\n")
                    writer.flush()

                    // 2. Client AUTH
                    reader.readLine()
                    writer.write("OK\n")
                    writer.flush()

                    // 3. Client VERIFY
                    reader.readLine()

                    // Daemon sends FORGED fake proof (does not know HMAC key)
                    writer.write("PROOF 0000000000000000000000000000000000000000000000000000000000000000\n")
                    writer.flush()
                    clientSocket.close()
                }

            val connectResult = PrivdClient.connect()
            assertFalse("PrivdClient must reject daemon on forged HMAC proof", connectResult)
            assertEquals(PrivdConnectionState.DISCONNECTED, PrivdClient.state.value)

            serverJob.cancel()
            serverSocket.close()
        }
}
