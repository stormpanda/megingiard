package com.stormpanda.megingiard.focus.rom

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.privd.PrivdClient
import java.io.File

private const val TAG = "GameNativeDetector"

/**
 * Detector implementation for GameNative instances.
 * Reads `wine.log` over privileged socket to parse active PC/Steam game.
 */
object GameNativeDetector : EmulatorDetector {
    override val supportedPackages: Set<String> =
        setOf(
            "app.gamenative",
            "com.utkarshdalal.gamenative",
        )

    override val systemId: String = "pc"

    private val logPaths =
        listOf(
            "/storage/emulated/0/Android/data/app.gamenative/files/wine_logs/wine.log",
            "/storage/emulated/0/Android/data/com.utkarshdalal.gamenative/files/wine_logs/wine.log",
            "/sdcard/Android/data/app.gamenative/files/wine_logs/wine.log",
            "/sdcard/Android/data/com.utkarshdalal.gamenative/files/wine_logs/wine.log",
        )

    override suspend fun detectActiveSession(packageName: String): ActiveGameSession? {
        if (!supportedPackages.contains(packageName)) return null

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

        AppLog.d(TAG, "No GameNative log file could be parsed for $packageName")
        return null
    }

    internal fun parseSessionFromLog(
        packageName: String,
        content: String,
    ): ActiveGameSession? {
        // 1. Search for Steam App ID or game ID in environment variables/logs
        val appIdRegex = Regex("""(?:STEAM_APP_ID|steam_appid|app_id|game_id)\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
        val appIdMatch = appIdRegex.findAll(content).lastOrNull()
        val appId = appIdMatch?.groupValues?.get(1)

        // 2. Search for common Windows paths or executables being started by wine
        val pathRegex =
            Regex(
                """L"C:\\(?:Program Files(?:\s*\(x86\))?\\)?(?:Steam\\steamapps\\common|Epic Games|GOG Galaxy\\Games|Games)\\([^"\\]+)""",
                RegexOption.IGNORE_CASE,
            )
        val pathMatch = pathRegex.findAll(content).lastOrNull()
        val folderOrExeName = pathMatch?.groupValues?.get(1)

        if (appId == null && folderOrExeName == null) return null

        // Look for matching ROM app in RomManager
        val romApps = RomManager.romApps.value
        val matchedApp =
            if (appId != null) {
                romApps.firstOrNull { app ->
                    val file = app.romPath?.let { File(it) }
                    file?.nameWithoutExtension == appId
                }
            } else {
                null
            }

        val finalMatchedApp =
            matchedApp ?: if (folderOrExeName != null) {
                romApps.firstOrNull { app ->
                    val file = app.romPath?.let { File(it) }
                    app.label.equals(folderOrExeName, ignoreCase = true) ||
                        file?.nameWithoutExtension?.contains(folderOrExeName, ignoreCase = true) == true
                }
            } else {
                null
            }

        return if (finalMatchedApp != null) {
            ActiveGameSession(
                packageName = packageName,
                systemId = "pc",
                romPath = finalMatchedApp.romPath ?: "",
                gameTitle = finalMatchedApp.label,
            )
        } else {
            // Fallback session
            val finalAppId = appId ?: folderOrExeName ?: "unknown"
            ActiveGameSession(
                packageName = packageName,
                systemId = "pc",
                romPath = "$finalAppId.steam",
                gameTitle = folderOrExeName ?: "Steam Game $finalAppId",
            )
        }
    }
}
