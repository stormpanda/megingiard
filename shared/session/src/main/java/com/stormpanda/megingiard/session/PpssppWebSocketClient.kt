package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.SafPathResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "PpssppWebSocketClient"

private const val WS_OPCODE_TEXT_FIN: Byte = 0x81.toByte()
private const val WS_MASK_BIT = 0x80
private const val WS_LEN_MASK = 0x7F
private const val WS_PAYLOAD_EXT16 = 126
private const val WS_HEADER_LEN = 6
private const val CONNECT_TIMEOUT_MS = 500
private const val SOCKET_TIMEOUT_MS = 1000
private const val MAX_FRAMES_TO_READ = 5
private const val DEFAULT_PPSSPP_PORT = 8080

/**
 * Pure Kotlin client for PPSSPP's native embedded WebSocket Debugger API (`debugger.ppsspp.org`).
 * Operates over `ws://127.0.0.1:8080/debugger` without requiring root or Privileged Mode.
 */
object PpssppWebSocketClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun queryActiveSession(
        packageName: String,
        port: Int = DEFAULT_PPSSPP_PORT,
    ): ActiveGameSession? =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS

                val out = socket.getOutputStream()
                val inp = socket.getInputStream()

                // Step 1: WebSocket Handshake
                val handshakeReq =
                    "GET /debugger HTTP/1.1\r\n" +
                        "Host: 127.0.0.1:$port\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: Upgrade\r\n" +
                        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
                        "Sec-WebSocket-Protocol: debugger.ppsspp.org\r\n" +
                        "Sec-WebSocket-Version: 13\r\n\r\n"

                out.write(handshakeReq.toByteArray(Charsets.UTF_8))
                out.flush()

                val handshakeResp = readHttpHeader(inp)
                if (!handshakeResp.contains("101")) {
                    AppLog.d(TAG, "queryActiveSession: unexpected handshake response - $handshakeResp")
                    return@withContext null
                }

                // Step 2: Send {"event":"game.status"} masked text frame
                val payload = "{\"event\":\"game.status\"}".toByteArray(Charsets.UTF_8)
                val len = payload.size
                val frame = ByteArray(WS_HEADER_LEN + len)
                frame[0] = WS_OPCODE_TEXT_FIN
                frame[1] = (WS_MASK_BIT or len).toByte()
                frame[2] = 0 // Mask key byte 0
                frame[3] = 0 // Mask key byte 1
                frame[4] = 0 // Mask key byte 2
                frame[5] = 0 // Mask key byte 3
                System.arraycopy(payload, 0, frame, WS_HEADER_LEN, len)

                out.write(frame)
                out.flush()

                // Step 3: Read response frame(s)
                val respJsonStr = readWebSocketTextFrame(inp) ?: return@withContext null
                val rootObj = json.parseToJsonElement(respJsonStr).jsonObject
                val gameElement = rootObj["game"]
                if (gameElement == null || gameElement.toString() == "null") return@withContext null

                val gameObj = gameElement.jsonObject
                val discId = gameObj["id"]?.jsonPrimitive?.content
                val title = gameObj["title"]?.jsonPrimitive?.content ?: discId ?: return@withContext null

                val derivedTitle = SafPathResolver.deriveGameTitle(title) ?: title

                ActiveGameSession(
                    packageName = packageName,
                    romPath = null,
                    gameTitle = derivedTitle,
                    systemId = "psp",
                    romIdentifier = discId ?: derivedTitle,
                    coreOrBackend = "PPSSPP",
                    titleId = discId,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d(TAG, "queryActiveSession: websocket query to 127.0.0.1:$port failed - $e")
                null
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }

    private fun readHttpHeader(inp: InputStream): String {
        val sb = StringBuilder()
        val buf = ByteArray(1)
        while (inp.read(buf) != -1) {
            val char = buf[0].toInt().toChar()
            sb.append(char)
            if (sb.endsWith("\r\n\r\n")) break
        }
        return sb.toString()
    }

    private fun readWebSocketTextFrame(inp: InputStream): String? {
        for (i in 0 until MAX_FRAMES_TO_READ) {
            val b0 = inp.read()
            if (b0 == -1) return null
            val b1 = inp.read()
            if (b1 == -1) return null

            var len = b1 and WS_LEN_MASK
            if (len == WS_PAYLOAD_EXT16) {
                val h = inp.read()
                val l = inp.read()
                if (h == -1 || l == -1) return null
                len = (h shl 8) or l
            }

            val payload = ByteArray(len)
            var totalRead = 0
            while (totalRead < len) {
                val n = inp.read(payload, totalRead, len - totalRead)
                if (n == -1) break
                totalRead += n
            }

            val text = String(payload, Charsets.UTF_8)
            if (text.contains("\"event\":\"game.status\"")) {
                return text
            }
        }
        return null
    }
}
