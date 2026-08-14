package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.session.ProcessCmdlineProvider

private const val TAG = "Pcsx2AndroidDetector"

/**
 * Detector implementation for PCSX2-derived Android PS2 emulators (ARMSX2, AetherSX2, NetherSX2).
 * Reads `recent_games.json` over privileged socket (or fallback paths) to parse active game.
 */
object Pcsx2AndroidDetector : EmulatorDetector {
    override val supportedPackages: Set<String> =
        setOf(
            "com.armsx2",
            "com.armsx2.debug",
            "xyz.aethersx2.android",
            "net.nethersx2.android",
        )

    override val systemId: String = "ps2"

    private fun getCandidatePaths(packageName: String): List<String> =
        listOf(
            "/storage/emulated/0/Android/data/$packageName/files/recent_games.json",
            "/sdcard/Android/data/$packageName/files/recent_games.json",
            "/storage/6914-318F/Android/data/$packageName/files/recent_games.json",
        )

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

        val candidatePaths = getCandidatePaths(packageName)
        for (path in candidatePaths) {
            val jsonContent = ProcessCmdlineProvider.readTextFile(path)
            if (!jsonContent.isNullOrBlank()) {
                val session = Pcsx2AndroidRecentGamesParser.parseMostRecentSession(packageName, jsonContent)
                if (session != null) {
                    AppLog.i(TAG, "Resolved active session via '$path': ${session.gameTitle} (${session.systemId})")
                    return session
                }
            }
        }

        AppLog.d(TAG, "No recent games history could be parsed for $packageName")
        return null
    }
}
