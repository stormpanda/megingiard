package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import java.io.File

private const val TAG = "PpssppDetector"

/**
 * Detector implementation for standalone PPSSPP (PlayStation Portable) emulator instances.
 * Dynamically resolves active game sessions in real-time using PPSSPP's native embedded
 * WebSocket Debugger API (`ws://127.0.0.1:8080/debugger`) without requiring Privileged Mode,
 * and falls back to Privd logcat streaming (`LOGCAT:PPSSPP`).
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

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

        // Auto-configure ppsspp.ini to enable RemoteDebuggerOnStartup if needed
        ensureRemoteDebuggerEnabled(packageName)

        // Strategy 1: Native PPSSPP WebSocket Debugger Server (Unprivileged, 100% Standalone)
        val wsSession = PpssppWebSocketClient.queryActiveSession(packageName, port = webSocketPort)
        if (wsSession != null) {
            AppLog.i(TAG, "Resolved active session via native WebSocket debugger: ${wsSession.gameTitle} (${wsSession.systemId})")
            return wsSession
        }

        // Strategy 2: Fall back to Privd daemon logcat stream
        val logcatContent = ProcessCmdlineProvider.readTextFile("LOGCAT:PPSSPP")
        if (!logcatContent.isNullOrBlank()) {
            val session = PpssppLogcatParser.parseLatestBootedSession(packageName, logcatContent)
            if (session != null) {
                AppLog.i(TAG, "Resolved active session via Privd logcat stream: ${session.gameTitle} (${session.systemId})")
                return session
            }
        }

        AppLog.d(TAG, "No active session could be resolved for $packageName")
        return null
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
                    if (content.contains("RemoteDebuggerOnStartup = False")) {
                        var updated = content.replace("RemoteDebuggerOnStartup = False", "RemoteDebuggerOnStartup = True")
                        if (updated.contains("RemoteISOPort = 0")) {
                            updated = updated.replace("RemoteISOPort = 0", "RemoteISOPort = 8080")
                        }
                        file.writeText(updated)
                        AppLog.i(TAG, "Auto-enabled RemoteDebuggerOnStartup in '$path'")
                    }
                } catch (e: Exception) {
                    AppLog.d(TAG, "ensureRemoteDebuggerEnabled: failed to write '$path' - $e")
                }
            }
        }
    }
}
