package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.session.ProcessCmdlineProvider

private const val TAG = "PpssppDetector"

/**
 * Detector implementation for standalone PPSSPP (PlayStation Portable) emulator instances.
 * Reads `ppsspp.ini` over privileged socket (or fallback storage paths) to parse active game.
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

    private fun getCandidateIniPaths(packageName: String): List<String> =
        listOf(
            "/storage/emulated/0/PSP/SYSTEM/ppsspp.ini",
            "/sdcard/PSP/SYSTEM/ppsspp.ini",
            "/storage/emulated/0/Android/data/$packageName/files/PSP/SYSTEM/ppsspp.ini",
            "/sdcard/Android/data/$packageName/files/PSP/SYSTEM/ppsspp.ini",
            "/storage/emulated/0/PPSSPP/PSP/SYSTEM/ppsspp.ini",
            "/sdcard/PPSSPP/PSP/SYSTEM/ppsspp.ini",
        )

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

        val iniPaths = getCandidateIniPaths(packageName)
        for (path in iniPaths) {
            val iniContent = ProcessCmdlineProvider.readTextFile(path)
            if (!iniContent.isNullOrBlank()) {
                val session = PpssppIniParser.parseMostRecentSession(packageName, iniContent)
                if (session != null) {
                    AppLog.i(TAG, "Resolved session via ini file '$path': ${session.gameTitle} (${session.systemId})")
                    return session
                }
            }
        }

        AppLog.d(TAG, "No active session could be parsed from ppsspp.ini for $packageName")
        return null
    }
}
