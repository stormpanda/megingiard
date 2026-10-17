package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.PcEmulators
import com.stormpanda.megingiard.catalog.RomManager
import com.stormpanda.megingiard.catalog.SYSTEM_ID_PC
import java.io.File

private const val TAG = "PcEmulatorDetector"
private const val PROC_SPLIT_LIMIT = 4
private const val PROC_INDEX_UID = 2
private const val PROC_INDEX_CMDLINE = 3
private const val DRIVE_PREFIX_LEN = 3
private const val EXE_EXT_LEN = 4
private const val STEAMAPPS_COMMON = "steamapps\\common\\"
private const val EXE_EXT = ".exe"
private const val STEAM_SUFFIX = ".steam"

private val SYSTEM_HELPERS =
    setOf(
        "wineserver",
        "services.exe",
        "winedevice.exe",
        "explorer.exe",
        "winhandler.exe",
        "tabtip.exe",
        "steamclient_loader_x64.exe",
        "steamclient_loader_x86.exe",
        "steamclient_loader.exe",
        "steam.exe",
        "gameoverlayui.exe",
        "gameoverlayui64.exe",
        "steamerrorreporter.exe",
        "steamerrorreporter64.exe",
        "streaming_client.exe",
        "vrserver.exe",
        "vrmonitor.exe",
        "vrdashboard.exe",
        "vrwebhelper.exe",
        "vrstartup.exe",
        "winemenubuilder.exe",
        "crashhandler.exe",
        "crashhandler64.exe",
        "dxsetup.exe",
        "vc_redist.x64.exe",
        "vc_redist.x86.exe",
        "vcredist_x64.exe",
        "vcredist_x86.exe",
        "pulseaudio",
        "plugplay.exe",
        "svchost.exe",
        "rpcss.exe",
        "start.exe",
        "conhost.exe",
        "mountmgr.exe",
        "rundll32.exe",
        "wineboot.exe",
        "winecfg.exe",
        "winedbg.exe",
        "msiexec.exe",
        "regedit.exe",
        "cmd.exe",
    )

private val STEAM_RUNTIME_FOLDER_PREFIXES =
    listOf(
        "proton",
        "droiddeck-proton",
        "steamlinuxruntime",
    )

private val STEAM_RUNTIME_FOLDER_NAMES =
    setOf(
        "steamworks shared",
        "steam controller configs",
    )

private val LINUX_WRAPPER_BINARIES =
    setOf(
        "reaper",
        "steam-launch-wrapper",
        "pressure-vessel-wrap",
        "pressure-vessel-adverb",
        "pv-adverb",
        "srt-bwrap",
        "bwrap",
        "sh",
        "bash",
        "dash",
        "env",
        "python",
        "python3",
        "proton",
        "wine",
        "wine64",
        "wine-preloader",
        "wine64-preloader",
        "box64",
        "box86",
        "fexinterpreter",
        "fexloader",
        "steam",
        "steamwebhelper",
    )

private val LINUX_ARCH_SUFFIXES =
    listOf(
        ".bin.x86_64",
        ".bin.x86",
        ".x86_64",
        ".x86",
        ".arm64",
        ".aarch64",
        ".sh",
    )

private val ENGINE_DIRECTORIES =
    setOf(
        "bin",
        "binaries",
        "x64",
        "x86",
        "engine",
        "content",
        "build",
        "game",
    )

private val GENERIC_ROOT_DIRECTORIES =
    setOf(
        "games",
        "program files",
        "program files (x86)",
        "steam",
        "steamlibrary",
    )

private val SHIPPING_SUFFIXES =
    listOf(
        "-win64-shipping",
        "-win32-shipping",
        "_win64_shipping",
        "_win32_shipping",
        "-shipping",
        "_shipping",
    )

private const val RANK_GENERIC_CANDIDATE = 0
private const val RANK_STEAMAPPS_CANDIDATE = 1
private const val RANK_ROM_MATCH = 2
private const val RANK_ROM_AND_STEAMAPPS_MATCH = 3
private val STEAM_APP_ID_REGEX = Regex("""\b(?:Steam)?AppId=(\d+)\b""", RegexOption.IGNORE_CASE)

/**
 * Detector implementation for PC/Steam emulators (`PcEmulators`: GameNative and DroidDeck).
 * Traverses running processes to resolve active PC/Steam game.
 */
object PcEmulatorDetector : EmulatorDetector {
    private data class GameProcessCandidate(
        val folderName: String,
        val binaryName: String,
        val isSteamappsCommon: Boolean,
    )

    override val supportedPackages: Set<String> =
        PcEmulators.SUPPORTED_EMULATOR_OPTIONS.map { it.packageName }.toSet()

    override val systemId: String = SYSTEM_ID_PC

    override suspend fun detectActiveSession(
        packageName: String,
        focusedRomPath: String?,
        focusedRomIdentifier: String?,
    ): ActiveGameSession? {
        if (packageName !in supportedPackages) return null

        val procList = ProcessCmdlineProvider.getRunningProcesses()
        if (!procList.isNullOrBlank()) {
            val session = parseSessionFromProcesses(packageName, procList, focusedRomPath, focusedRomIdentifier)
            if (session != null) {
                AppLog.i(TAG, "Resolved session via process list: ${session.gameTitle} (${session.systemId})")
                return session
            }
        }

        AppLog.d(TAG, "No PC emulator session could be resolved for $packageName")
        return null
    }

    internal fun parseSessionFromProcesses(
        packageName: String,
        procList: String,
        focusedRomPath: String? = null,
        focusedRomIdentifier: String? = null,
    ): ActiveGameSession? {
        val lines = procList.split('\n')

        // Find the UID of the target package (e.g. app.gamenative or com.droiddeck.launcher)
        var targetUid: String? = null
        for (line in lines) {
            if (line.startsWith("PROC ")) {
                val parts = line.split(' ', limit = PROC_SPLIT_LIMIT)
                if (parts.size >= PROC_SPLIT_LIMIT) {
                    val uid = parts[PROC_INDEX_UID]
                    val cmdline = parts[PROC_INDEX_CMDLINE].trim()
                    if (cmdline == packageName || cmdline.startsWith("$packageName:")) {
                        targetUid = uid
                        break
                    }
                }
            }
        }

        if (targetUid == null) {
            AppLog.d(TAG, "parseSessionFromProcesses: Main process not found for $packageName")
            return null
        }

        var detectedSteamAppId: String? = null
        for (line in lines) {
            if (line.startsWith("PROC ")) {
                val parts = line.split(' ', limit = PROC_SPLIT_LIMIT)
                if (parts.size >= PROC_SPLIT_LIMIT && parts[PROC_INDEX_UID] == targetUid) {
                    val cmdline = parts[PROC_INDEX_CMDLINE].trim()
                    val match = STEAM_APP_ID_REGEX.find(cmdline)
                    if (match != null) {
                        detectedSteamAppId = match.groupValues[1]
                    }
                }
            }
        }

        val backendName =
            PcEmulators.SUPPORTED_EMULATOR_OPTIONS
                .firstOrNull { it.packageName == packageName }
                ?.displayName
        var bestSession: ActiveGameSession? = null
        var bestCandidateRank = -1
        val romApps = RomManager.romApps.value.filter { it.systemId == SYSTEM_ID_PC }

        for (line in lines) {
            if (line.startsWith("PROC ")) {
                val parts = line.split(' ', limit = PROC_SPLIT_LIMIT)
                if (parts.size >= PROC_SPLIT_LIMIT) {
                    val uid = parts[PROC_INDEX_UID]
                    val cmdline = parts[PROC_INDEX_CMDLINE].trim()
                    if (uid == targetUid) {
                        val candidate = extractGameCandidate(cmdline) ?: continue
                        val folderName = candidate.folderName
                        val exeName = candidate.binaryName

                        val rawExeBaseName = exeName.substringBeforeLast('.', missingDelimiterValue = exeName)
                        val cleanedExeBaseName = cleanExeName(exeName)

                        // Match against PC ROMs in RomManager
                        val matchedApp =
                            romApps.firstOrNull { app ->
                                val file = app.romPath?.let { File(it) }
                                app.label.equals(folderName, ignoreCase = true) ||
                                    file?.nameWithoutExtension?.contains(folderName, ignoreCase = true) == true ||
                                    app.label.equals(rawExeBaseName, ignoreCase = true) ||
                                    app.label.equals(cleanedExeBaseName, ignoreCase = true) ||
                                    (detectedSteamAppId != null && file?.nameWithoutExtension == detectedSteamAppId)
                            } ?: run {
                                // If direct name matching fails (e.g. dev folder typo), check if a focused ROM was launched
                                if (focusedRomPath != null || focusedRomIdentifier != null) {
                                    romApps.firstOrNull { app ->
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

                        val session =
                            if (matchedApp != null) {
                                val matchPath = matchedApp.romPath?.takeIf { it.startsWith("/") }
                                val matchIdentifier =
                                    matchedApp.romPath?.let { File(it).name } ?: (focusedRomIdentifier ?: "$folderName$STEAM_SUFFIX")
                                ActiveGameSession(
                                    packageName = packageName,
                                    systemId = SYSTEM_ID_PC,
                                    romPath = matchPath,
                                    romIdentifier = matchIdentifier,
                                    gameTitle = matchedApp.label,
                                    coreOrBackend = backendName,
                                    titleId = detectedSteamAppId,
                                )
                            } else if (focusedRomPath != null || focusedRomIdentifier != null) {
                                val matchPath = focusedRomPath?.takeIf { it.startsWith("/") }
                                val matchIdentifier =
                                    focusedRomIdentifier ?: focusedRomPath?.let { File(it).name } ?: "$folderName$STEAM_SUFFIX"
                                val matchTitle =
                                    focusedRomIdentifier?.substringBeforeLast('.') ?: focusedRomPath?.let { File(it).nameWithoutExtension }
                                        ?: folderName
                                ActiveGameSession(
                                    packageName = packageName,
                                    systemId = SYSTEM_ID_PC,
                                    romPath = matchPath,
                                    romIdentifier = matchIdentifier,
                                    gameTitle = matchTitle,
                                    coreOrBackend = backendName,
                                    titleId = detectedSteamAppId,
                                )
                            } else {
                                ActiveGameSession(
                                    packageName = packageName,
                                    systemId = SYSTEM_ID_PC,
                                    romPath = null,
                                    romIdentifier = "$folderName$STEAM_SUFFIX",
                                    gameTitle = folderName,
                                    coreOrBackend = backendName,
                                    titleId = detectedSteamAppId,
                                )
                            }

                        val candidateRank =
                            when {
                                matchedApp != null && candidate.isSteamappsCommon -> RANK_ROM_AND_STEAMAPPS_MATCH
                                matchedApp != null -> RANK_ROM_MATCH
                                candidate.isSteamappsCommon -> RANK_STEAMAPPS_CANDIDATE
                                else -> RANK_GENERIC_CANDIDATE
                            }
                        if (candidateRank >= bestCandidateRank) {
                            bestSession = session
                            bestCandidateRank = candidateRank
                        }
                    }
                }
            }
        }

        return bestSession
    }

    private fun extractExePath(rawCmd: String): String? {
        val trimmed = rawCmd.trim()
        if (trimmed.startsWith('"')) {
            val closeQuote = trimmed.indexOf('"', startIndex = 1)
            if (closeQuote != -1) {
                val candidate = trimmed.substring(1, closeQuote).trim()
                if (candidate.endsWith(EXE_EXT, ignoreCase = true)) {
                    return candidate
                }
            }
        }

        val exeIndex = trimmed.indexOf(EXE_EXT, ignoreCase = true)
        if (exeIndex != -1) {
            val end = exeIndex + EXE_EXT_LEN
            if (end == trimmed.length || trimmed[end] == ' ' || trimmed[end] == '"') {
                return trimmed.substring(0, end).trim().removeSurrounding("\"")
            }
        }
        return null
    }

    private fun isSystemBinary(
        exeName: String,
        normalizedPath: String,
    ): Boolean {
        if (SYSTEM_HELPERS.any { helper ->
                exeName.equals(helper, ignoreCase = true) ||
                    normalizedPath.contains(helper, ignoreCase = true)
            }
        ) {
            return true
        }
        val lower = normalizedPath.lowercase()
        return lower.startsWith("c:\\windows\\") ||
            lower.startsWith("\\windows\\") ||
            lower.contains("\\windows\\system32\\") ||
            lower.contains("\\windows\\syswow64\\")
    }

    private fun extractGameCandidate(cmdline: String): GameProcessCandidate? {
        val normalizedCmd = cmdline.replace('/', '\\')
        if (normalizedCmd.contains(STEAMAPPS_COMMON, ignoreCase = true)) {
            return extractSteamappsCommonCandidate(normalizedCmd)
        }

        val exePath = extractExePath(cmdline) ?: return null
        val normalizedExe = exePath.replace('/', '\\')
        val lastSlash = normalizedExe.lastIndexOf('\\')
        val exeName = if (lastSlash != -1) normalizedExe.substring(lastSlash + 1) else normalizedExe
        if (isSystemBinary(exeName, normalizedExe)) {
            return null
        }
        val folderName = extractDriveGameFolder(normalizedExe) ?: cleanExeName(exeName)
        return GameProcessCandidate(
            folderName = folderName,
            binaryName = exeName,
            isSteamappsCommon = false,
        )
    }

    private fun extractSteamappsCommonCandidate(normalizedCmd: String): GameProcessCandidate? {
        var searchStart = 0
        while (searchStart < normalizedCmd.length) {
            val commonIndex = normalizedCmd.indexOf(STEAMAPPS_COMMON, startIndex = searchStart, ignoreCase = true)
            if (commonIndex == -1) break

            val folderStart = commonIndex + STEAMAPPS_COMMON.length
            val folderEnd = normalizedCmd.indexOf('\\', startIndex = folderStart)
            if (folderEnd == -1) break

            val rawFolder = normalizedCmd.substring(folderStart, folderEnd).trim().removeSurrounding("\"")
            searchStart = folderEnd + 1
            if (rawFolder.isEmpty() || isSteamRuntimeFolder(rawFolder)) {
                continue
            }

            val rest = normalizedCmd.substring(folderEnd + 1)
            val nextCommonIndex = rest.indexOf(STEAMAPPS_COMMON, ignoreCase = true)
            val segment = if (nextCommonIndex != -1) rest.substring(0, nextCommonIndex) else rest

            val exeIndex = segment.indexOf(EXE_EXT, ignoreCase = true)
            if (exeIndex != -1) {
                val relativeExePath = segment.substring(0, exeIndex + EXE_EXT_LEN).trim().removeSurrounding("\"")
                val exeName = relativeExePath.substringAfterLast('\\').trim().removeSurrounding("\"")
                if (exeName.isNotEmpty() && !isSystemBinary(exeName, relativeExePath)) {
                    return GameProcessCandidate(
                        folderName = rawFolder,
                        binaryName = exeName,
                        isSteamappsCommon = true,
                    )
                }
                continue
            }

            val cleanedSegment = segment.substringBefore('"').substringBefore(" -").trim()
            val afterLastSlash = cleanedSegment.substringAfterLast('\\').trim()
            val linuxBinary = afterLastSlash.substringBefore(' ').trim()
            if (linuxBinary.isNotEmpty() &&
                linuxBinary.lowercase() !in LINUX_WRAPPER_BINARIES &&
                !isSystemBinary(linuxBinary, cleanedSegment)
            ) {
                return GameProcessCandidate(
                    folderName = rawFolder,
                    binaryName = linuxBinary,
                    isSteamappsCommon = true,
                )
            }
        }
        return null
    }

    private fun isSteamRuntimeFolder(folderName: String): Boolean {
        val lower = folderName.trim().lowercase()
        return lower in STEAM_RUNTIME_FOLDER_NAMES ||
            STEAM_RUNTIME_FOLDER_PREFIXES.any { prefix -> lower.startsWith(prefix) }
    }

    private fun cleanExeName(exeName: String): String {
        var base = exeName
        val lowerBase = base.lowercase()
        for (archSuffix in LINUX_ARCH_SUFFIXES) {
            if (lowerBase.endsWith(archSuffix) && base.length > archSuffix.length) {
                base = base.substring(0, base.length - archSuffix.length)
                break
            }
        }
        val withoutExt = base.substringBeforeLast('.', missingDelimiterValue = base)
        val lower = withoutExt.lowercase()
        for (suffix in SHIPPING_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return withoutExt.substring(0, withoutExt.length - suffix.length)
            }
        }
        return withoutExt
    }

    private fun extractDriveGameFolder(normalizedPath: String): String? {
        if (normalizedPath.length >= DRIVE_PREFIX_LEN &&
            normalizedPath[1] == ':' &&
            normalizedPath[2] == '\\'
        ) {
            val nextSlash = normalizedPath.indexOf('\\', startIndex = DRIVE_PREFIX_LEN)
            if (nextSlash != -1) {
                val segment = normalizedPath.substring(DRIVE_PREFIX_LEN, nextSlash).trim()
                val segmentLower = segment.lowercase()
                if (segmentLower in GENERIC_ROOT_DIRECTORIES) {
                    val secondSlash = normalizedPath.indexOf('\\', startIndex = nextSlash + 1)
                    if (secondSlash != -1) {
                        val subSegment = normalizedPath.substring(nextSlash + 1, secondSlash).trim()
                        if (subSegment.isNotEmpty() && subSegment.lowercase() !in ENGINE_DIRECTORIES) {
                            return subSegment
                        }
                    }
                } else if (segment.isNotEmpty() && segmentLower !in ENGINE_DIRECTORIES) {
                    return segment
                }
            }
        }
        return null
    }
}
