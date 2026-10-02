package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SYSTEM_ID_SWITCH
import com.stormpanda.megingiard.catalog.SafPathResolver
import com.stormpanda.megingiard.catalog.SwitchEmulators
import java.util.Locale

private const val TAG = "YuzuDetector"
private val LOADING_REGEX = Regex("""Loading\s+(.+)\s+\(([A-Fa-f0-9]{16})\)""")
private val VIEW_SETUP_REGEX = Regex("""\[EmulationFragment\]\s+Starting view setup for game:\s+(.+)""")
private val CUSTOM_SETTINGS_REGEX = Regex("""\[EmulationFragment\]\s+Loading custom settings for\s+(.+)""")
private val CONTROL_DATA_REGEX = Regex("""Control data for\s+([A-Fa-f0-9]{16}):\s+name="(.+?)"""")
private val TITLE_ID_REGEX = Regex("""title_id=([A-Fa-f0-9]{16})""", RegexOption.IGNORE_CASE)
private const val BASE_TITLE_ID_SUFFIX = "000"

/**
 * Detector implementation for Yuzu-derived Nintendo Switch emulators
 * (Citron, Eden, Sudachi, Suyu, Yuzu).
 * Reads active emulator log files over privileged socket.
 */
object YuzuDetector : EmulatorDetector {
    private val titleCache = mutableMapOf<String, String>()

    override val supportedPackages: Set<String> =
        SwitchEmulators.SUPPORTED_EMULATOR_OPTIONS.map { it.packageName }.toSet()

    override val systemId: String = SYSTEM_ID_SWITCH

    private val logFileNames =
        mapOf(
            "org.citron.citron_emu" to "citron_log.txt",
            "org.citron.citron_emu.debug" to "citron_log.txt",
            "org.sudachi.sudachi_emu" to "sudachi_log.txt",
            "com.suyu.suyu" to "suyu_log.txt",
            "org.yuzu.yuzu_emu" to "yuzu_log.txt",
            "org.yuzu.yuzu_emu.ea" to "yuzu_log.txt",
            "dev.eden.eden_emulator" to "eden_log.txt",
            "dev.eden.eden_emulator.debug" to "eden_log.txt",
            "dev.eden.eden_emulator.nightly" to "eden_log.txt",
            "dev.eden.eden_emulator.nightly.debug" to "eden_log.txt",
            "dev.eden.eden_emulator.dualscreen" to "eden_log.txt",
            "dev.eden.eden_emulator.dualscreen.debug" to "eden_log.txt",
            "dev.eden.eden_emulator.dualscreen.nightly" to "eden_log.txt",
            "dev.eden.eden_emulator.dualscreen.nightly.debug" to "eden_log.txt",
            "dev.legacy.eden_emulator" to "eden_log.txt",
            "dev.legacy.eden_emulator.debug" to "eden_log.txt",
            "dev.legacy.eden_emulator.nightly" to "eden_log.txt",
            "dev.legacy.eden_emulator.nightly.debug" to "eden_log.txt",
        )

    private val backendBrandByPackage =
        mapOf(
            "dev.eden.eden_emulator" to "eden",
            "dev.eden.eden_emulator.debug" to "eden",
            "dev.eden.eden_emulator.nightly" to "eden",
            "dev.eden.eden_emulator.nightly.debug" to "eden",
            "dev.eden.eden_emulator.dualscreen" to "eden",
            "dev.eden.eden_emulator.dualscreen.debug" to "eden",
            "dev.eden.eden_emulator.dualscreen.nightly" to "eden",
            "dev.eden.eden_emulator.dualscreen.nightly.debug" to "eden",
            "dev.legacy.eden_emulator" to "eden",
            "dev.legacy.eden_emulator.debug" to "eden",
            "dev.legacy.eden_emulator.nightly" to "eden",
            "dev.legacy.eden_emulator.nightly.debug" to "eden",
            "org.citron.citron_emu" to "citron",
            "org.citron.citron_emu.debug" to "citron",
            "org.sudachi.sudachi_emu" to "sudachi",
            "com.suyu.suyu" to "suyu",
            "org.yuzu.yuzu_emu" to "yuzu",
            "org.yuzu.yuzu_emu.ea" to "yuzu",
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
                continue
            }

            val controlMatch = CONTROL_DATA_REGEX.find(line)
            if (controlMatch != null) {
                val id = controlMatch.groupValues[1].uppercase(Locale.US)
                val name = controlMatch.groupValues[2].trim()
                if (name.isNotBlank()) {
                    lastGameTitle = name
                }
                val matchedIsBase = id.endsWith(BASE_TITLE_ID_SUFFIX)
                if (lastTitleId == null || matchedIsBase) {
                    lastTitleId = id
                }
                continue
            }

            val viewSetupMatch = VIEW_SETUP_REGEX.find(line)
            if (viewSetupMatch != null) {
                val name = viewSetupMatch.groupValues[1].trim()
                if (name.isNotBlank()) {
                    lastGameTitle = name
                }
                continue
            }

            val customSettingsMatch = CUSTOM_SETTINGS_REGEX.find(line)
            if (customSettingsMatch != null) {
                val name = customSettingsMatch.groupValues[1].trim()
                if (name.isNotBlank() && lastGameTitle == null) {
                    lastGameTitle = name
                }
                continue
            }

            val titleIdMatch = TITLE_ID_REGEX.find(line)
            if (titleIdMatch != null) {
                val matchedId = titleIdMatch.groupValues[1].uppercase(Locale.US)
                // Switch base game title IDs always end with "000". Updates end with "800", DLC with "001"-"FFE".
                // Never overwrite an existing base game title ID with a non-base (DLC/update) title ID.
                val matchedIsBase = matchedId.endsWith(BASE_TITLE_ID_SUFFIX)
                if (lastTitleId == null || matchedIsBase) {
                    lastTitleId = matchedId
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
            coreOrBackend = backendBrandByPackage[packageName] ?: "yuzu",
            titleId = lastTitleId,
        )
    }
}
