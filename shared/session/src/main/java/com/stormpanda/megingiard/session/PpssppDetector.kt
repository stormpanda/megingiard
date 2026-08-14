package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PpssppDetector"

/**
 * Detector implementation for standalone PPSSPP (PlayStation Portable) emulator instances.
 * Dynamically resolves active game sessions in real-time using PPSSPP's native embedded
 * WebSocket Debugger API (`ws://127.0.0.1:8080/debugger`) without requiring Privileged Mode.
 */
object PpssppDetector : EmulatorDetector {
    override val supportedPackages: Set<String> =
        setOf(
            "org.ppsspp.ppsspp",
            "org.ppsspp.ppssppgold",
            "org.ppsspp.ppsspp.debug",
            "org.ppsspp.ppssppgold.debug",
            "org.ppsspp.ppssppdev",
        )

    override val systemId: String = "psp"

    var webSocketPort: Int = 8080

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? =
        withContext(Dispatchers.IO) {
            if (!supportedPackages.contains(packageName)) return@withContext null

            // Auto-configure ppsspp.ini to enable RemoteDebuggerOnStartup & RemoteShareOnStartup if needed
            ensureRemoteDebuggerEnabled(packageName)

            // Native PPSSPP WebSocket Debugger Server (Unprivileged, 100% Standalone)
            val wsSession = PpssppWebSocketClient.queryActiveSession(packageName, port = webSocketPort)
            if (wsSession != null) {
                val finalRomPath = wsSession.romPath ?: readRecentFileNameFromIni(packageName, wsSession.gameTitle)
                val resolvedSession = wsSession.copy(romPath = finalRomPath)
                AppLog.i(
                    TAG,
                    "Resolved active session via native WebSocket debugger: ${resolvedSession.gameTitle} (romPath='${resolvedSession.romPath}')",
                )
                return@withContext resolvedSession
            }

            AppLog.d(TAG, "No active session could be resolved for $packageName")
            return@withContext null
        }

    fun readRecentFileNameFromIni(
        packageName: String,
        activeTitle: String? = null,
    ): String? {
        val iniPaths =
            listOf(
                "/storage/6914-318F/ppsspp/PSP/SYSTEM/ppsspp.ini",
                "/storage/emulated/0/PSP/SYSTEM/ppsspp.ini",
                "/storage/emulated/0/ppsspp/PSP/SYSTEM/ppsspp.ini",
                "/sdcard/PSP/SYSTEM/ppsspp.ini",
                "/sdcard/ppsspp/PSP/SYSTEM/ppsspp.ini",
                "/storage/emulated/0/Android/data/$packageName/files/PSP/SYSTEM/ppsspp.ini",
            )

        for (path in iniPaths) {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                try {
                    val lines = file.readLines()
                    val fileNameLines =
                        lines.filter { line ->
                            val trimmed = line.trim()
                            trimmed.startsWith("FileName") && trimmed.contains("=")
                        }

                    for (line in fileNameLines) {
                        val rawUri = line.substringAfter("=").trim()
                        val resolvedPath = SafPathResolver.resolveFilePath(rawUri)
                        val fileName = resolvedPath?.let { File(it).name }
                        if (!fileName.isNullOrBlank()) {
                            if (activeTitle == null || isTitleMatching(fileName, activeTitle)) {
                                AppLog.i(
                                    TAG,
                                    "readRecentFileNameFromIni: resolved matching ROM filename from '$path': '$fileName' for active title '$activeTitle'",
                                )
                                return fileName
                            } else {
                                AppLog.d(
                                    TAG,
                                    "readRecentFileNameFromIni: rejected stale candidate '$fileName' (does not match active title '$activeTitle')",
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.d(TAG, "readRecentFileNameFromIni: failed to read '$path' - $e")
                }
            }
        }
        return null
    }

    fun isTitleMatching(
        candidateFileName: String,
        activeTitle: String,
    ): Boolean {
        if (activeTitle.isBlank() || candidateFileName.isBlank()) return false

        val normActive = activeTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
        val normCandidate = candidateFileName.lowercase().substringBeforeLast('.').replace(Regex("[^a-z0-9]"), "")
        if (normActive.isEmpty() || normCandidate.isEmpty()) return false

        if (normCandidate.contains(normActive) || normActive.contains(normCandidate)) return true

        val activeWords = activeTitle.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 }
        if (activeWords.isNotEmpty()) {
            val matchCount = activeWords.count { normCandidate.contains(it) }
            return matchCount >= (activeWords.size * 0.5).toInt().coerceAtLeast(1)
        }
        return false
    }

    private fun ensureRemoteDebuggerEnabled(packageName: String) {
        val iniPaths =
            listOf(
                "/storage/6914-318F/ppsspp/PSP/SYSTEM/ppsspp.ini",
                "/storage/emulated/0/PSP/SYSTEM/ppsspp.ini",
                "/storage/emulated/0/ppsspp/PSP/SYSTEM/ppsspp.ini",
                "/sdcard/PSP/SYSTEM/ppsspp.ini",
                "/sdcard/ppsspp/PSP/SYSTEM/ppsspp.ini",
                "/storage/emulated/0/Android/data/$packageName/files/PSP/SYSTEM/ppsspp.ini",
            )

        for (path in iniPaths) {
            val file = File(path)
            if (file.exists() && file.canWrite()) {
                try {
                    val content = file.readText()
                    var updated = content
                    if (updated.contains("RemoteDebuggerOnStartup = False")) {
                        updated = updated.replace("RemoteDebuggerOnStartup = False", "RemoteDebuggerOnStartup = True")
                    }
                    if (updated.contains("RemoteISOPort = 0")) {
                        updated = updated.replace("RemoteISOPort = 0", "RemoteISOPort = 8080")
                    }
                    if (updated != content) {
                        file.writeText(updated)
                        AppLog.i(TAG, "Auto-configured RemoteDebuggerOnStartup in '$path'")
                    }
                } catch (e: Exception) {
                    AppLog.d(TAG, "ensureRemoteDebuggerEnabled: failed to write '$path' - $e")
                }
            }
        }
    }
}
