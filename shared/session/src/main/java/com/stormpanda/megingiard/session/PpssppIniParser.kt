package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.RomManager
import java.io.File

private const val TAG = "PpssppIniParser"
private val PSP_EXTENSIONS = setOf("iso", "cso", "pbp", "chd", "elf")

/**
 * Pure Kotlin parser for PPSSPP `ppsspp.ini` configuration files.
 * Extracts the most recent game path from the `[Recent]` section (`FileName0`).
 */
object PpssppIniParser {
    /**
     * Parses `ppsspp.ini` content and returns an [ActiveGameSession] if a valid,
     * non-blank `FileName0` entry is present under the `[Recent]` section.
     */
    fun parseMostRecentSession(
        packageName: String,
        iniContent: String,
    ): ActiveGameSession? {
        if (iniContent.isBlank()) return null
        return try {
            val fileName0 = extractFileName0(iniContent) ?: return null
            val romPath = fileName0.trim()
            if (romPath.isBlank()) return null

            val derivedTitle = deriveGameTitle(romPath) ?: return null

            ActiveGameSession(
                packageName = packageName,
                romPath = romPath,
                gameTitle = derivedTitle,
                systemId = "psp",
                coreOrBackend = "PPSSPP",
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "parseMostRecentSession: failed to parse PPSSPP ini content - $e")
            null
        }
    }

    private fun extractFileName0(iniContent: String): String? {
        val lines = iniContent.lineSequence()
        var insideRecentSection = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("[") && line.endsWith("]")) {
                val sectionName = line.substring(1, line.length - 1).trim()
                insideRecentSection = sectionName.equals("Recent", ignoreCase = true)
                continue
            }

            if (insideRecentSection) {
                if (line.startsWith("FileName0", ignoreCase = true)) {
                    val equalsIndex = line.indexOf('=')
                    if (equalsIndex != -1) {
                        val value = line.substring(equalsIndex + 1).trim()
                        return value.removeSurrounding("\"").takeIf { it.isNotBlank() }
                    }
                }
            }
        }
        return null
    }

    internal fun deriveGameTitle(romPath: String): String? {
        // Try matching against RomManager catalog
        val romApps = RomManager.romApps.value
        val matchedApp =
            romApps.firstOrNull { app ->
                val appRomPath = app.romPath
                appRomPath.equals(romPath, ignoreCase = true) ||
                    (appRomPath != null && File(appRomPath).name.equals(File(romPath).name, ignoreCase = true))
            }
        if (matchedApp != null) {
            return matchedApp.label
        }

        val fileName = romPath.substringAfterLast('/').substringAfterLast('\\')
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val nameWithoutExt =
            if (ext in PSP_EXTENSIONS) {
                fileName.substringBeforeLast('.')
            } else {
                fileName
            }

        return nameWithoutExt.trim().takeIf { it.isNotBlank() }
    }
}
