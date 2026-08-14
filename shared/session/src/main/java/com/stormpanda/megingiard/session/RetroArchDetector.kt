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

    private val lplPaths =
        listOf(
            "/storage/emulated/0/RetroArch/playlists/content_history.lpl",
            "/storage/emulated/0/RetroArch/playlists/builtin/content_history.lpl",
            "/sdcard/RetroArch/playlists/content_history.lpl",
            "/storage/emulated/0/Android/data/com.retroarch.aarch64/files/playlists/content_history.lpl",
            "/storage/emulated/0/Android/data/com.retroarch/files/playlists/content_history.lpl",
        )

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

        for (path in lplPaths) {
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
