package com.stormpanda.megingiard.session
import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.RomManager
import java.io.File

private const val TAG = "GameNativeDetector"
private const val PROC_SPLIT_LIMIT = 4
private const val PROC_INDEX_UID = 2
private const val PROC_INDEX_CMDLINE = 3
private val SYSTEM_HELPERS =
    setOf(
        "wineserver",
        "services.exe",
        "winedevice.exe",
        "explorer.exe",
        "winhandler.exe",
        "tabtip.exe",
        "steamclient_loader_x64.exe",
        "pulseaudio",
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
        if (!supportedPackages.contains(packageName)) return null

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

        for (line in lines) {
            if (line.startsWith("PROC ")) {
                val parts = line.split(' ', limit = PROC_SPLIT_LIMIT)
                if (parts.size >= PROC_SPLIT_LIMIT) {
                    val uid = parts[PROC_INDEX_UID]
                    val cmdline = parts[PROC_INDEX_CMDLINE].trim()
                    if (uid == targetUid) {
                        if (cmdline.endsWith(".exe", ignoreCase = true)) {
                            val normalizedCmd = cmdline.replace('/', '\\')
                            val lastSlash = normalizedCmd.lastIndexOf('\\')
                            val exeName = if (lastSlash != -1) normalizedCmd.substring(lastSlash + 1) else normalizedCmd

                            if (SYSTEM_HELPERS.any { helper ->
                                    exeName.equals(helper, ignoreCase = true) ||
                                        normalizedCmd.contains(helper, ignoreCase = true)
                                }
                            ) {
                                continue
                            }

                            // Try to extract the game folder name from typical Windows steamapps paths, or use the exe name
                            // path looks like: C:\Program Files (x86)\Steam\steamapps\common\Baba Is You\Baba Is You.exe
                            val commonIndex = normalizedCmd.indexOf("steamapps\\common\\", ignoreCase = true)
                            val folderName =
                                if (commonIndex != -1) {
                                    val start = commonIndex + "steamapps\\common\\".length
                                    val end = normalizedCmd.indexOf('\\', start)
                                    if (end != -1) {
                                        normalizedCmd.substring(start, end)
                                    } else {
                                        exeName.removeSuffix(".exe")
                                    }
                                } else {
                                    exeName.removeSuffix(".exe")
                                }

                            // Match against RomManager
                            val romApps = RomManager.romApps.value
                            val matchedApp =
                                romApps.firstOrNull { app ->
                                    val file = app.romPath?.let { File(it) }
                                    app.label.equals(folderName, ignoreCase = true) ||
                                        file?.nameWithoutExtension?.contains(folderName, ignoreCase = true) == true ||
                                        app.label.equals(exeName.removeSuffix(".exe"), ignoreCase = true)
                                }

                            return if (matchedApp != null) {
                                val matchPath = matchedApp.romPath?.takeIf { it.startsWith("/") }
                                val matchIdentifier = matchedApp.romPath?.let { File(it).name } ?: "$folderName.steam"
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
                                    romIdentifier = "$folderName.steam",
                                    gameTitle = folderName,
                                )
                            }
                        }
                    }
                }
            }
        }

        return null
    }
}
