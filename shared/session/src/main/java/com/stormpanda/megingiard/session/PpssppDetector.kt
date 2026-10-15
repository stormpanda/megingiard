package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.SafPathResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "PpssppDetector"
private const val DEFAULT_REMOTE_DEBUGGER_PORT = 8080
private const val TITLE_MATCH_RATIO = 0.5
private const val MIN_WORD_LEN_FOR_MATCH = 3

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

    var webSocketPort: Int = DEFAULT_REMOTE_DEBUGGER_PORT
    private val configuredPackages = mutableSetOf<String>()

    override suspend fun detectActiveSession(
        packageName: String,
        focusedRomPath: String?,
        focusedRomIdentifier: String?,
    ): ActiveGameSession? =
        withContext(Dispatchers.IO) {
            if (!supportedPackages.contains(packageName)) return@withContext null

            // Native PPSSPP WebSocket Debugger Server (Unprivileged, 100% Standalone)
            val wsSession = PpssppWebSocketClient.queryActiveSession(packageName, port = webSocketPort)
            if (wsSession != null) {
                val iniResolvedPath = readRecentPathFromIni(packageName, wsSession.gameTitle)
                val finalRomPath = iniResolvedPath?.takeIf { it.startsWith("/") && File(it).exists() }
                val finalRomIdentifier = iniResolvedPath?.let { File(it).name } ?: wsSession.romIdentifier
                val resolvedSession =
                    wsSession.copy(
                        romPath = finalRomPath,
                        romIdentifier = finalRomIdentifier,
                    )
                AppLog.i(
                    TAG,
                    "Resolved active session via native WebSocket debugger: ${resolvedSession.gameTitle} (romPath='${resolvedSession.romPath}', romIdentifier='${resolvedSession.romIdentifier}')",
                )
                return@withContext resolvedSession
            }

            // Auto-configure ppsspp.ini once per package if WebSocket debugger is not reachable
            if (synchronized(configuredPackages) { configuredPackages.add(packageName) }) {
                ensureRemoteDebuggerEnabled(packageName)
            }

            AppLog.d(TAG, "No active session could be resolved for $packageName")
            return@withContext null
        }

    fun readRecentPathFromIni(
        packageName: String,
        activeTitle: String? = null,
    ): String? {
        for (file in findPpssppIniFiles(packageName)) {
            if (file.canRead()) {
                try {
                    val fileNameLines =
                        file.readLines().filter { line ->
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
                                    "readRecentPathFromIni: resolved matching ROM path from '${file.path}': '$resolvedPath' for active title '$activeTitle'",
                                )
                                return resolvedPath ?: fileName
                            } else {
                                AppLog.d(
                                    TAG,
                                    "readRecentPathFromIni: rejected stale candidate '$fileName' (does not match active title '$activeTitle')",
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLog.d(TAG, "readRecentPathFromIni: failed to read '${file.path}' - $e")
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

        val activeWords = activeTitle.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= MIN_WORD_LEN_FOR_MATCH }
        if (activeWords.isNotEmpty()) {
            val matchCount = activeWords.count { normCandidate.contains(it) }
            return matchCount >= (activeWords.size * TITLE_MATCH_RATIO).toInt().coerceAtLeast(1)
        }
        return false
    }

    private fun ensureRemoteDebuggerEnabled(packageName: String) {
        for (file in findPpssppIniFiles(packageName)) {
            if (file.canWrite()) {
                try {
                    val content = file.readText()
                    var updated = content
                    if (updated.contains("RemoteDebuggerOnStartup = False")) {
                        updated = updated.replace("RemoteDebuggerOnStartup = False", "RemoteDebuggerOnStartup = True")
                    }
                    if (updated.contains("RemoteISOPort = 0")) {
                        updated = updated.replace("RemoteISOPort = 0", "RemoteISOPort = $DEFAULT_REMOTE_DEBUGGER_PORT")
                    }
                    if (updated != content) {
                        file.writeText(updated)
                        AppLog.i(TAG, "Auto-configured RemoteDebuggerOnStartup in '${file.path}'")
                    }
                } catch (e: Exception) {
                    AppLog.d(TAG, "ensureRemoteDebuggerEnabled: failed to write '${file.path}' - $e")
                }
            }
        }
    }

    private fun findPpssppIniFiles(packageName: String): List<File> {
        val relativeSubPaths =
            listOf(
                "PSP/SYSTEM/ppsspp.ini",
                "ppsspp/PSP/SYSTEM/ppsspp.ini",
                "Android/data/$packageName/files/PSP/SYSTEM/ppsspp.ini",
            )
        return SafPathResolver
            .getStorageVolumeRoots()
            .flatMap { root ->
                relativeSubPaths.map { subPath -> File("$root/$subPath") }
            }.filter { it.exists() }
    }
}
