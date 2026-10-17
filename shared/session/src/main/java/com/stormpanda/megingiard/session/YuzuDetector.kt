package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SYSTEM_ID_SWITCH
import com.stormpanda.megingiard.catalog.SafPathResolver
import com.stormpanda.megingiard.catalog.SwitchEmulators
import java.io.File
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
    override val supportedPackages: Set<String> =
        SwitchEmulators.SUPPORTED_EMULATOR_OPTIONS.map { it.packageName }.toSet()

    override val systemId: String = SYSTEM_ID_SWITCH

    private fun getCandidateLogPaths(packageName: String): List<String> {
        val logFileName = "${SwitchEmulators.getLogBrand(packageName)}_log.txt"
        val relativeSubPath = "Android/data/$packageName/files/log/$logFileName"
        return SafPathResolver.getStorageVolumeRoots().map { root -> "$root/$relativeSubPath" }
    }

    override suspend fun detectActiveSession(
        packageName: String,
        focusedRomPath: String?,
        focusedRomIdentifier: String?,
    ): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

        val logPaths = getCandidateLogPaths(packageName)
        for (path in logPaths) {
            val logContent = ProcessCmdlineProvider.readTextFile(path)
            if (!logContent.isNullOrBlank()) {
                val session =
                    parseSessionFromLog(
                        packageName = packageName,
                        logContent = logContent,
                        focusedRomPath = focusedRomPath,
                        focusedRomIdentifier = focusedRomIdentifier,
                    )
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
        focusedRomPath: String? = null,
        focusedRomIdentifier: String? = null,
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

        val switchRomApps = RomManager.romApps.value.filter { it.systemId == SYSTEM_ID_SWITCH }
        val matchedApp =
            switchRomApps.firstOrNull { app ->
                val matchesTitleId =
                    lastTitleId != null && app.romPath?.contains(lastTitleId, ignoreCase = true) == true
                val matchesTitle =
                    lastGameTitle != null &&
                        (
                            app.label.equals(lastGameTitle, ignoreCase = true) ||
                                app.defaultLabel?.equals(lastGameTitle, ignoreCase = true) == true ||
                                app.romPath?.let { File(it).nameWithoutExtension.equals(lastGameTitle, ignoreCase = true) } == true
                        )
                matchesTitleId || matchesTitle
            } ?: run {
                if (focusedRomPath != null || focusedRomIdentifier != null) {
                    switchRomApps.firstOrNull { app ->
                        app.romPath?.equals(focusedRomPath, ignoreCase = true) == true ||
                            (
                                focusedRomIdentifier != null &&
                                    (
                                        app.romPath?.endsWith("/$focusedRomIdentifier", ignoreCase = true) == true ||
                                            app.romPath.equals(focusedRomIdentifier, ignoreCase = true) ||
                                            app.label.equals(
                                                focusedRomIdentifier.substringBeforeLast('.'),
                                                ignoreCase = true,
                                            )
                                    )
                            )
                    }
                } else {
                    null
                }
            }

        val resolvedRomPath: String?
        val resolvedRomIdentifier: String?
        val resolvedTitle: String

        if (matchedApp != null) {
            resolvedRomPath = matchedApp.romPath?.takeIf { it.startsWith("/") }
            resolvedRomIdentifier =
                matchedApp.romPath?.let { File(it).name } ?: (focusedRomIdentifier ?: lastTitleId ?: matchedApp.label)
            resolvedTitle = lastGameTitle ?: matchedApp.label
        } else if (focusedRomPath != null || focusedRomIdentifier != null) {
            resolvedRomPath = focusedRomPath?.takeIf { it.startsWith("/") }
            resolvedRomIdentifier =
                focusedRomIdentifier ?: focusedRomPath?.let { File(it).name } ?: lastTitleId ?: lastGameTitle
            resolvedTitle =
                lastGameTitle
                    ?: focusedRomIdentifier?.substringBeforeLast('.')
                    ?: focusedRomPath?.let { File(it).nameWithoutExtension }
                    ?: "Switch Game (${lastTitleId ?: ""})".trim()
        } else {
            resolvedRomPath = null
            resolvedTitle = lastGameTitle ?: "Switch Game (${lastTitleId ?: ""})".trim()
            resolvedRomIdentifier = lastTitleId ?: lastGameTitle
        }

        return ActiveGameSession(
            packageName = packageName,
            romPath = resolvedRomPath,
            gameTitle = resolvedTitle,
            systemId = systemId,
            romIdentifier = resolvedRomIdentifier,
            coreOrBackend = SwitchEmulators.getLogBrand(packageName),
            titleId = lastTitleId,
        )
    }
}
