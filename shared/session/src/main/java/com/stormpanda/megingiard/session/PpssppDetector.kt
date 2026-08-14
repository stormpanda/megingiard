package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog

private const val TAG = "PpssppDetector"

/**
 * Detector implementation for standalone PPSSPP (PlayStation Portable) emulator instances.
 * Resolves active game sessions in real-time by inspecting logcat boot events (`[BOOT] Booted <path>`)
 * streamed exclusively via the privileged daemon (`LOGCAT:PPSSPP`).
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

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

        val logcatContent = ProcessCmdlineProvider.readTextFile("LOGCAT:PPSSPP")
        if (!logcatContent.isNullOrBlank()) {
            val session = PpssppLogcatParser.parseLatestBootedSession(packageName, logcatContent)
            if (session != null) {
                AppLog.i(TAG, "Resolved active session via Privd logcat stream: ${session.gameTitle} (${session.systemId})")
                return session
            }
        }

        AppLog.d(TAG, "No active session could be resolved via Privd logcat stream for $packageName")
        return null
    }
}
