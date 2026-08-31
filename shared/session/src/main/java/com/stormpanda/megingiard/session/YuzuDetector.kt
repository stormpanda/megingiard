package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.RomManager
import java.util.Locale

private const val TAG = "YuzuDetector"
private val LOADING_REGEX = Regex("""Loading\s+(.+)\s+\(([A-Fa-f0-9]{16})\)""")
private val TITLE_ID_REGEX = Regex("""title_id=([A-Fa-f0-9]{16})""", RegexOption.IGNORE_CASE)

/**
 * Detector implementation for Yuzu-derived Nintendo Switch emulators
 * (Citron, Yuzu, Sudachi, Suyu).
 * Reads active emulator log files over privileged socket.
 */
object YuzuDetector : EmulatorDetector {
    private val titleCache = mutableMapOf<String, String>()

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

    private val logFileNames =
        mapOf(
            "org.citron.citron_emu" to "citron_log.txt",
            "org.citron.citron_emu.debug" to "citron_log.txt",
            "org.sudachi.sudachi_emu" to "sudachi_log.txt",
            "com.suyu.suyu" to "suyu_log.txt",
            "org.yuzu.yuzu_emu" to "yuzu_log.txt",
            "org.yuzu.yuzu_emu.ea" to "yuzu_log.txt",
        )

    private fun getCandidateLogPaths(packageName: String): List<String> {
        val logFileName = logFileNames[packageName] ?: "yuzu_log.txt"
        val relativeSubPath = "Android/data/$packageName/files/log/$logFileName"
        return SafPathResolver.getStorageVolumeRoots().map { root -> "$root/$relativeSubPath" }
    }

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

        val logPaths = getCandidateLogPaths(packageName)
        for (path in logPaths) {
            val logContent = ProcessCmdlineProvider.readTextFile(path)
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
            val loadingMatch = LOADING_REGEX.find(line)
            if (loadingMatch != null) {
                lastGameTitle = loadingMatch.groupValues[1].trim()
                lastTitleId = loadingMatch.groupValues[2].uppercase(Locale.US)
            } else {
                val titleIdMatch = TITLE_ID_REGEX.find(line)
                if (titleIdMatch != null) {
                    lastTitleId = titleIdMatch.groupValues[1].uppercase(Locale.US)
                }
            }
        }

        if (lastTitleId == null && lastGameTitle == null) {
            return null
        }

        if (lastGameTitle != null && lastTitleId != null) {
            synchronized(titleCache) {
                titleCache[lastTitleId] = lastGameTitle
            }
        }

        val knownTitle =
            lastGameTitle ?: lastTitleId?.let { id ->
                synchronized(titleCache) { titleCache[id] }
                    ?: RomManager.romApps.value
                        .firstOrNull { app ->
                            app.romPath?.contains(id, ignoreCase = true) == true
                        }?.label
            }

        val resolvedTitle = knownTitle ?: "Switch Game (${lastTitleId ?: ""})".trim()
        val resolvedRomIdentifier = lastTitleId ?: knownTitle

        return ActiveGameSession(
            packageName = packageName,
            romPath = null,
            gameTitle = resolvedTitle,
            systemId = systemId,
            romIdentifier = resolvedRomIdentifier,
            coreOrBackend = "yuzu",
            titleId = lastTitleId,
        )
    }
}
