package com.stormpanda.megingiard.focus.rom

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import java.io.File

private const val TAG = "GameNativeDetector"

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

        val procList = PrivdClient.getRunningProcesses()
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
                val parts = line.split(' ', limit = 4)
                if (parts.size >= 4) {
                    val uid = parts[2]
                    val cmdline = parts[3].trim()
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

        // Find running .exe under that UID which is not a known system process
        val systemHelpers =
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

        for (line in lines) {
            if (line.startsWith("PROC ")) {
                val parts = line.split(' ', limit = 4)
                if (parts.size >= 4) {
                    val uid = parts[2]
                    val cmdline = parts[3].trim()
                    if (uid == targetUid) {
                        if (cmdline.endsWith(".exe", ignoreCase = true)) {
                            val lastSlash = cmdline.lastIndexOf('\\')
                            val lastForwardSlash = cmdline.lastIndexOf('/')
                            val separatorIdx = maxOf(lastSlash, lastForwardSlash)
                            val exeName = if (separatorIdx != -1) cmdline.substring(separatorIdx + 1) else cmdline

                            if (systemHelpers.any { helper ->
                                    exeName.equals(helper, ignoreCase = true) ||
                                        cmdline.contains(helper, ignoreCase = true)
                                }
                            ) {
                                continue
                            }

                            // Try to extract the game folder name from typical Windows steamapps paths, or use the exe name
                            // path looks like: C:\Program Files (x86)\Steam\steamapps\common\Baba Is You\Baba Is You.exe
                            val commonIndex = cmdline.indexOf("steamapps\\common\\", ignoreCase = true)
                            val folderName =
                                if (commonIndex != -1) {
                                    val start = commonIndex + "steamapps\\common\\".length
                                    val end = cmdline.indexOf('\\', start)
                                    if (end != -1) {
                                        cmdline.substring(start, end)
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
                                ActiveGameSession(
                                    packageName = packageName,
                                    systemId = "pc",
                                    romPath = matchedApp.romPath ?: "",
                                    gameTitle = matchedApp.label,
                                )
                            } else {
                                ActiveGameSession(
                                    packageName = packageName,
                                    systemId = "pc",
                                    romPath = "$folderName.steam",
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
