package com.stormpanda.megingiard.focus.rom

import com.stormpanda.megingiard.AppLog

private const val TAG = "RetroArchLplParser"

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
            val path = extractJsonField(jsonContent, "path")
            val label = extractJsonField(jsonContent, "label")
            val corePath = extractJsonField(jsonContent, "core_path")
            val coreName = extractJsonField(jsonContent, "core_name")

            val gameTitle = deriveGameTitle(label, path) ?: return null
            val systemId = resolveSystemId(path, corePath, coreName)

            ActiveGameSession(
                packageName = packageName,
                romPath = path,
                gameTitle = gameTitle,
                systemId = systemId,
                coreOrBackend = coreName ?: corePath,
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
            ext in listOf("sfc", "smc", "fig") || core.contains("snes") || core.contains("bsnes") -> "snes"
            ext in listOf("n64", "z64", "v64") || core.contains("mupen") || core.contains("n64") -> "n64"
            ext == "gba" || core.contains("mgba") || core.contains("vba") -> "gba"
            ext in listOf("gb", "gbc") || core.contains("gambatte") -> "gbc"
            ext in listOf("nes", "fds") || core.contains("fceu") || core.contains("nestopia") -> "nes"
            ext in listOf("pbp", "chd", "cue") && (core.contains("pcsx") || core.contains("beetle_psx")) -> "ps1"
            ext in listOf("md", "gen", "smd") || core.contains("genesis") || core.contains("picodrive") -> "genesis"
            ext == "nds" || core.contains("melonds") || core.contains("drastic") -> "nds"
            else -> "retroarch"
        }
    }
}
