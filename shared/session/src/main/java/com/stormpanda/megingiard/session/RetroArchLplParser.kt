package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog

private const val TAG = "RetroArchLplParser"

private val EXT_SNES = setOf("sfc", "smc", "fig")
private val EXT_N64 = setOf("n64", "z64", "v64")
private val EXT_GBC = setOf("gb", "gbc")
private val EXT_NES = setOf("nes", "fds")
private val EXT_PS1 = setOf("pbp", "chd", "cue")
private val EXT_GENESIS = setOf("md", "gen", "smd")

/**
 * Pure Kotlin parser for RetroArch `content_history.lpl` and system playlist JSON files.
 */
object RetroArchLplParser {
    fun parseMostRecentSession(
        packageName: String,
        jsonContent: String,
    ): ActiveGameSession? {
        if (jsonContent.isBlank()) return null
        return try {
            val itemsIndex = jsonContent.indexOf("\"items\"")
            if (itemsIndex == -1) return null
            val firstBraceIndex = jsonContent.indexOf('{', itemsIndex)
            if (firstBraceIndex == -1) return null
            val closeBraceIndex = jsonContent.indexOf('}', firstBraceIndex)
            if (closeBraceIndex == -1 || closeBraceIndex <= firstBraceIndex) return null

            val firstItemBlock = jsonContent.substring(firstBraceIndex, closeBraceIndex + 1)

            val path = extractJsonField(firstItemBlock, "path")
            val label = extractJsonField(firstItemBlock, "label")
            val corePath = extractJsonField(firstItemBlock, "core_path")
            val coreName = extractJsonField(firstItemBlock, "core_name")

            if (path == null) return null

            val gameTitle = deriveGameTitle(label, path) ?: return null
            val systemId = resolveSystemId(path, corePath, coreName)

            ActiveGameSession(
                packageName = packageName,
                romPath = path,
                gameTitle = gameTitle,
                systemId = systemId,
                coreOrBackend = coreName ?: corePath ?: "unknown",
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "parseMostRecentSession: failed to parse LPL JSON - $e")
            null
        }
    }

    private fun extractJsonField(
        json: String,
        fieldName: String,
    ): String? {
        val pattern = Regex(""""$fieldName"\s*:\s*"(.*?)(?<!\\)"""")
        val match = pattern.find(json) ?: return null
        val value = match.groupValues[1]
        return value.replace("\\\"", "\"").replace("\\\\", "\\").takeIf { it.isNotBlank() }
    }

    fun deriveGameTitle(
        label: String?,
        path: String?,
    ): String? {
        if (!label.isNullOrBlank() && label != "DETECT") {
            return label.trim()
        }
        if (!path.isNullOrBlank()) {
            val fileName = path.substringAfterLast('/').substringAfterLast('\\')
            val nameWithoutExt = fileName.substringBeforeLast('.')
            if (nameWithoutExt.isNotBlank()) {
                return nameWithoutExt.trim()
            }
        }
        return null
    }

    fun resolveSystemId(
        path: String?,
        corePath: String?,
        coreName: String?,
    ): String {
        val ext = path?.substringAfterLast('.', "")?.lowercase() ?: ""
        val core = (coreName ?: corePath ?: "").lowercase()

        return when {
            ext in EXT_SNES || core.contains("snes") || core.contains("bsnes") -> "snes"
            ext in EXT_N64 || core.contains("mupen") || core.contains("n64") -> "n64"
            ext == "gba" || core.contains("mgba") || core.contains("vba") -> "gba"
            ext in EXT_GBC || core.contains("gambatte") -> "gbc"
            ext in EXT_NES || core.contains("fceu") || core.contains("nestopia") -> "nes"
            ext in EXT_PS1 && (core.contains("pcsx") || core.contains("beetle_psx")) -> "ps1"
            ext in EXT_GENESIS || core.contains("genesis") || core.contains("picodrive") -> "genesis"
            ext == "nds" || core.contains("melonds") || core.contains("drastic") -> "nds"
            else -> "retroarch"
        }
    }
}
