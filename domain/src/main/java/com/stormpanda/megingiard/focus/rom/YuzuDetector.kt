package com.stormpanda.megingiard.focus.rom

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import java.io.File
import java.util.Locale

private const val TAG = "YuzuDetector"
private const val TITLE_ID_LENGTH = 16

/**
 * Detector implementation for Yuzu-derived Nintendo Switch emulators
 * (Citron, Yuzu, Sudachi, Suyu).
 * Reads active emulator log files and custom per-game configurations over privileged socket.
 */
object YuzuDetector : EmulatorDetector {
    override val supportedPackages: Set<String> =
        setOf(
            "org.citron.citron_emu",
            "org.citron.citron_emu.debug",
            "org.yuzu.yuzu_emu",
            "org.yuzu.yuzu_emu.ea",
            "org.sudachi.sudachi_emu",
            "com.suyu.suyu",
        )

    override val systemId: String = "switch"

    private fun getCandidateLogPaths(packageName: String): List<String> =
        listOf(
            "/storage/emulated/0/Android/data/$packageName/files/log/citron_log.txt",
            "/storage/emulated/0/Android/data/$packageName/files/log/yuzu_log.txt",
            "/storage/emulated/0/Android/data/$packageName/files/log/sudachi_log.txt",
            "/storage/emulated/0/Android/data/$packageName/files/log/suyu_log.txt",
            "/sdcard/Android/data/$packageName/files/log/citron_log.txt",
            "/sdcard/Android/data/$packageName/files/log/yuzu_log.txt",
        )

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

        val logPaths = getCandidateLogPaths(packageName)
        for (path in logPaths) {
            val logContent = PrivdClient.readTextFile(path)
            if (!logContent.isNullOrBlank()) {
                val session = parseSessionFromLog(packageName, logContent)
                if (session != null) {
                    AppLog.i(TAG, "Resolved session via log file '$path': ${session.gameTitle} (${session.systemId})")
                    return session
                }
            }
        }

        AppLog.d(TAG, "No active session could be parsed from logs for $packageName")
        return null
    }

    internal fun parseSessionFromLog(
        packageName: String,
        logContent: String,
    ): ActiveGameSession? {
        val lines = logContent.lineSequence()

        var lastGameTitle: String? = null
        var lastTitleId: String? = null

        // Iterate through log lines from top to bottom to capture the latest loaded game
        for (line in lines) {
            // Pattern 1: Core <Info> core/core.cpp:Load:402: Loading WILD GUNS Reloaded (0100CFC00A1D8000) ...
            if (line.contains("Loading ") && line.contains("(") && line.contains(")")) {
                val loadingIdx = line.lastIndexOf("Loading ")
                if (loadingIdx != -1) {
                    val sub = line.substring(loadingIdx + "Loading ".length)
                    val parenStart = sub.lastIndexOf('(')
                    val parenEnd = sub.lastIndexOf(')')
                    if (parenStart != -1 && parenEnd > parenStart) {
                        val titleCandidate = sub.substring(0, parenStart).trim()
                        val idCandidate = sub.substring(parenStart + 1, parenEnd).trim().uppercase(Locale.US)
                        if (idCandidate.length == TITLE_ID_LENGTH && idCandidate.all { it.isLetterOrDigit() }) {
                            lastGameTitle = titleCandidate
                            lastTitleId = idCandidate
                        }
                    }
                }
            } else if (line.contains("title_id=")) {
                // Pattern 2: PatchExeFS for title_id=0100CFC00A1D8000
                val titleIdIdx = line.lastIndexOf("title_id=")
                if (titleIdIdx != -1) {
                    val candidate = line.substring(titleIdIdx + "title_id=".length).take(TITLE_ID_LENGTH).uppercase(Locale.US)
                    if (candidate.length == TITLE_ID_LENGTH && candidate.all { it.isLetterOrDigit() }) {
                        lastTitleId = candidate
                    }
                }
            }
        }

        if (lastTitleId == null && lastGameTitle == null) {
            return null
        }

        // Try matching against RomManager to resolve exact romPath if available
        val romApps = RomManager.romApps.value
        val matchedApp =
            romApps.firstOrNull { app ->
                val romFile = app.romPath?.let { File(it) }
                val titleUpper = lastGameTitle?.uppercase(Locale.US)
                val idUpper = lastTitleId

                (idUpper != null && app.romPath?.contains(idUpper, ignoreCase = true) == true) ||
                    (titleUpper != null && app.label.uppercase(Locale.US) == titleUpper) ||
                    (titleUpper != null && romFile?.nameWithoutExtension?.uppercase(Locale.US)?.contains(titleUpper) == true)
            }

        val resolvedTitle = matchedApp?.label ?: lastGameTitle ?: "Switch Game ($lastTitleId)"
        val resolvedRomPath = matchedApp?.romPath ?: lastTitleId?.let { "$it.nsp" } ?: ""

        return ActiveGameSession(
            packageName = packageName,
            romPath = resolvedRomPath,
            gameTitle = resolvedTitle,
            systemId = systemId,
            coreOrBackend = "yuzu",
        )
    }
}
