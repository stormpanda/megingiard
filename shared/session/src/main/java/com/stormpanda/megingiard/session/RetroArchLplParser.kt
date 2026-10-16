package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "RetroArchLplParser"

private val EXT_SNES = setOf("sfc", "smc", "fig")
private val EXT_N64 = setOf("n64", "z64", "v64")
private val EXT_GBC = setOf("gb", "gbc")
private val EXT_NES = setOf("nes", "fds")
private val EXT_PS1 = setOf("pbp", "chd", "cue")
private val EXT_GENESIS = setOf("md", "gen", "smd")

@Serializable
internal data class RetroArchPlaylist(
    val items: List<RetroArchPlaylistItem> = emptyList(),
)

@Serializable
internal data class RetroArchPlaylistItem(
    val path: String? = null,
    val label: String? = null,
    @SerialName("core_path")
    val corePath: String? = null,
    @SerialName("core_name")
    val coreName: String? = null,
)

/**
 * Pure Kotlin parser for RetroArch `content_history.lpl` and system playlist JSON files.
 */
object RetroArchLplParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseMostRecentSession(
        packageName: String,
        jsonContent: String,
    ): ActiveGameSession? {
        if (jsonContent.isBlank()) return null
        return try {
            val playlist = json.decodeFromString<RetroArchPlaylist>(jsonContent)
            val first = playlist.items.firstOrNull() ?: return null

            val path = first.path?.takeIf { it.isNotBlank() } ?: return null
            val label = first.label?.takeIf { it.isNotBlank() }
            val corePath = first.corePath?.takeIf { it.isNotBlank() }
            val coreName = first.coreName?.takeIf { it.isNotBlank() }

            val gameTitle = deriveGameTitle(label, path) ?: return null
            val systemId = resolveSystemId(path, corePath, coreName)

            val romIdentifier = path.substringAfterLast('/').substringAfterLast('\\').takeIf { it.isNotBlank() }

            ActiveGameSession(
                packageName = packageName,
                romPath = path.takeIf { it.startsWith("/") },
                gameTitle = gameTitle,
                systemId = systemId,
                romIdentifier = romIdentifier,
                coreOrBackend = coreName ?: corePath ?: "unknown",
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "parseMostRecentSession: failed to parse LPL JSON - $e")
            null
        }
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
