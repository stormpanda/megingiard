package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.session.ProcessCmdlineProvider

private const val TAG = "RetroArchDetector"

/**
 * Detector implementation for RetroArch instances.
 * Reads `content_history.lpl` over privileged socket (or fallback paths) to parse active game.
 */
object RetroArchDetector : EmulatorDetector {
    override val supportedPackages: Set<String> =
        setOf(
            "com.retroarch",
            "com.retroarch.aarch64",
            "com.retroarch.ra32",
            "org.retroarch",
            "org.retroarch.aarch64",
            "org.retroarch.ra32",
        )

    override val systemId: String = "retroarch"

    private fun getCandidatePaths(packageName: String): List<String> {
        val relativeSubPaths =
            listOf(
                "RetroArch/playlists/content_history.lpl",
                "RetroArch/playlists/builtin/content_history.lpl",
                "Android/data/$packageName/files/playlists/content_history.lpl",
            )
        return SafPathResolver.getStorageVolumeRoots().flatMap { root ->
            relativeSubPaths.map { subPath -> "$root/$subPath" }
        }
    }

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

        val candidatePaths = getCandidatePaths(packageName)
        for (path in candidatePaths) {
            val jsonContent = ProcessCmdlineProvider.readTextFile(path)
            if (!jsonContent.isNullOrBlank()) {
                val session = RetroArchLplParser.parseMostRecentSession(packageName, jsonContent)
                if (session != null) {
                    AppLog.i(TAG, "Resolved session via LPL file '$path': ${session.gameTitle} (${session.systemId})")
                    return session
                }
            }
        }

        AppLog.d(TAG, "No LPL history file could be parsed for $packageName")
        return null
    }
}
