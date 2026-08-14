package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "PpssppWebSocketClient"

/**
 * Pure Kotlin client for PPSSPP's native embedded WebSocket Debugger API (`debugger.ppsspp.org`).
 * Operates over `ws://127.0.0.1:8080/debugger` without requiring root or Privileged Mode.
 */
object PpssppWebSocketClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun queryActiveSession(
        packageName: String,
        port: Int = 8080,
    ): ActiveGameSession? =
        withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress("127.0.0.1", port), 500)
                socket.soTimeout = 1000

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
                val frame = ByteArray(6 + len)
                frame[0] = 0x81.toByte()
                frame[1] = (0x80 or len).toByte()
                frame[2] = 0 // Mask key byte 0
                frame[3] = 0 // Mask key byte 1
                frame[4] = 0 // Mask key byte 2
                frame[5] = 0 // Mask key byte 3
                System.arraycopy(payload, 0, frame, 6, len)

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
                // Query PPSSPP embedded HTTP webserver (GET http://127.0.0.1:port/) to fetch exact filename
                // populated directly by PPSSPP's internal g_recentFiles memory structure.
                // Strict rule: NO synthetic fallback names ever (if unresolved, romPath is null).
                val exactRomFilename = queryExactRomFilename(port)

                ActiveGameSession(
                    packageName = packageName,
                    romPath = exactRomFilename,
                    gameTitle = derivedTitle,
                    systemId = "psp",
                    coreOrBackend = "PPSSPP",
                )
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

    /**
     * Connects to PPSSPP's embedded HTTP webserver on port [port] (`GET / HTTP/1.1`)
     * and parses the exact running game's filename from PPSSPP's internal `g_recentFiles` array.
     */
    fun queryExactRomFilename(port: Int = 8080): String? {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress("127.0.0.1", port), 500)
            socket.soTimeout = 1000

            val out = socket.getOutputStream()
            val inp = socket.getInputStream()

            val httpReq =
                "GET / HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:$port\r\n" +
                    "User-Agent: Megingiard\r\n" +
                    "Connection: close\r\n\r\n"

            out.write(httpReq.toByteArray(Charsets.UTF_8))
            out.flush()

            val header = readHttpHeader(inp)
            AppLog.d(TAG, "queryExactRomFilename: HTTP header response:\n$header")
            if (!header.contains("200")) {
                AppLog.d(TAG, "queryExactRomFilename: non-200 response header - $header")
                return null
            }

            val body = inp.bufferedReader(Charsets.UTF_8).readText()
            AppLog.i(TAG, "queryExactRomFilename: HTTP 200 response body received (${body.length} bytes):\n$body")
            val resolvedFilename = parseExactRomFilenameFromHttpListing(body)
            AppLog.i(TAG, "queryExactRomFilename: resolved exact ROM filename = '$resolvedFilename'")
            resolvedFilename
        } catch (e: Exception) {
            AppLog.d(TAG, "queryExactRomFilename: failed to query 127.0.0.1:$port/ - $e")
            null
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Parses the line-separated HTTP disc listing returned by PPSSPP's `GET /` endpoint
     * (e.g. `/\n/Tactics Ogre (USA).iso\n/debugger\n`) and returns the exact filename
     * of the primary active game, ignoring system HTTP endpoints like `/debugger` or `/upload`.
     */
    fun parseExactRomFilenameFromHttpListing(body: String): String? {
        val systemEndpoints = setOf("debugger", "upload", "index.html", "favicon.ico")
        val candidateLines =
            body
                .lines()
                .map { it.trim().removePrefix("/").removePrefix("\\") }
                .filter { line ->
                    line.isNotEmpty() && !systemEndpoints.contains(line.lowercase())
                }
        val selected = candidateLines.firstOrNull()
        AppLog.d(TAG, "parseExactRomFilenameFromHttpListing: candidate lines = $candidateLines -> selected: '$selected'")
        return selected?.ifBlank { null }
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
        val b0 = inp.read()
        if (b0 == -1) return null
        val b1 = inp.read()
        if (b1 == -1) return null

        var len = b1 and 0x7F
        if (len == 126) {
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
        return text
    }
}
