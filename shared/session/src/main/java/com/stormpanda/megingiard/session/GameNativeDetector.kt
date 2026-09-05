package com.stormpanda.megingiard.session
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.RomManager
import java.io.File

private const val TAG = "GameNativeDetector"
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

/**
 * Detector implementation for GameNative instances.
 * Traverses running processes to resolve active PC/Steam game.
 */
object GameNativeDetector : EmulatorDetector {
    override val supportedPackages: Set<String> =
        setOf(
            "app.gamenative",
            "com.utkarshdalal.gamenative",
        )

    override val systemId: String = "pc"

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (packageName !in supportedPackages) return null

        val procList = ProcessCmdlineProvider.getRunningProcesses()
        if (!procList.isNullOrBlank()) {
            val session = parseSessionFromProcesses(packageName, procList)
            if (session != null) {
                AppLog.i(TAG, "Resolved session via process list: ${session.gameTitle} (${session.systemId})")
                return session
            }
        }

        AppLog.d(TAG, "No GameNative session could be resolved for $packageName")
        return null
    }

    internal fun parseSessionFromProcesses(
        packageName: String,
        procList: String,
    ): ActiveGameSession? {
        val lines = procList.split('\n')

        // Find the UID of the target package (e.g. app.gamenative)
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

        var bestSession: ActiveGameSession? = null
        var hasRomManagerMatch = false

        for (line in lines) {
            if (line.startsWith("PROC ")) {
                val parts = line.split(' ', limit = PROC_SPLIT_LIMIT)
                if (parts.size >= PROC_SPLIT_LIMIT) {
                    val uid = parts[PROC_INDEX_UID]
                    val cmdline = parts[PROC_INDEX_CMDLINE].trim()
                    if (uid == targetUid) {
                        val exePath = extractExePath(cmdline) ?: continue
                        val normalizedCmd = exePath.replace('/', '\\')
                        val lastSlash = normalizedCmd.lastIndexOf('\\')
                        val exeName = if (lastSlash != -1) normalizedCmd.substring(lastSlash + 1) else normalizedCmd

                        if (isSystemBinary(exeName, normalizedCmd)) {
                            continue
                        }

                        // Try to extract the game folder name from typical Windows steamapps paths, virtual drive A:\ paths, or use the exe name
                        val commonIndex = normalizedCmd.indexOf(STEAMAPPS_COMMON, ignoreCase = true)
                        val folderName =
                            if (commonIndex != -1) {
                                val start = commonIndex + STEAMAPPS_COMMON.length
                                val end = normalizedCmd.indexOf('\\', start)
                                if (end != -1) {
                                    normalizedCmd.substring(start, end)
                                } else {
                                    cleanExeName(exeName)
                                }
                            } else {
                                extractDriveGameFolder(normalizedCmd) ?: cleanExeName(exeName)
                            }

                        val rawExeBaseName = exeName.substringBeforeLast('.', missingDelimiterValue = exeName)
                        val cleanedExeBaseName = cleanExeName(exeName)

                        // Match against RomManager
                        val romApps = RomManager.romApps.value
                        val matchedApp =
                            romApps.firstOrNull { app ->
                                val file = app.romPath?.let { File(it) }
                                app.label.equals(folderName, ignoreCase = true) ||
                                    file?.nameWithoutExtension?.contains(folderName, ignoreCase = true) == true ||
                                    app.label.equals(rawExeBaseName, ignoreCase = true) ||
                                    app.label.equals(cleanedExeBaseName, ignoreCase = true)
                            }

                        val session =
                            if (matchedApp != null) {
                                val matchPath = matchedApp.romPath?.takeIf { it.startsWith("/") }
                                val matchIdentifier = matchedApp.romPath?.let { File(it).name } ?: "$folderName$STEAM_SUFFIX"
                                ActiveGameSession(
                                    packageName = packageName,
                                    systemId = "pc",
                                    romPath = matchPath,
                                    romIdentifier = matchIdentifier,
                                    gameTitle = matchedApp.label,
                                )
                            } else {
                                ActiveGameSession(
                                    packageName = packageName,
                                    systemId = "pc",
                                    romPath = null,
                                    romIdentifier = "$folderName$STEAM_SUFFIX",
                                    gameTitle = folderName,
                                )
                            }

                        if (matchedApp != null) {
                            bestSession = session
                            hasRomManagerMatch = true
                        } else if (!hasRomManagerMatch) {
                            bestSession = session
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

    private fun cleanExeName(exeName: String): String {
        val withoutExt = exeName.substringBeforeLast('.', missingDelimiterValue = exeName)
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
