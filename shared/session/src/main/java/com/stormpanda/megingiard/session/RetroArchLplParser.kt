package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import com.stormpanda.megingiard.catalog.EMULATOR_ID_RETROARCH
import com.stormpanda.megingiard.catalog.SUPPORTED_SYSTEMS
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "RetroArchLplParser"

private val EXT_SNES = setOf("sfc", "smc", "snes", "fig")
private val EXT_N64 = setOf("n64", "z64", "v64")
private val EXT_GB = setOf("gb", "gbc")
private val EXT_NES = setOf("nes", "fds")
private val EXT_PS1 = setOf("pbp", "chd", "cue", "bin")
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
        val ext = path?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val coreFileName =
            corePath
                ?.substringAfterLast('/')
                ?.substringAfterLast('\\')
                ?.lowercase()
                .orEmpty()
        val retroArchSystems = SUPPORTED_SYSTEMS.filter { it.emulatorId == EMULATOR_ID_RETROARCH }

        if (coreFileName.isNotEmpty()) {
            if (ext.isNotEmpty()) {
                val exactCoreAndExtMatch =
                    retroArchSystems.firstOrNull { sys ->
                        ext in sys.extensions &&
                            (
                                sys.retroArchCore.equals(coreFileName, ignoreCase = true) ||
                                    sys.retroArchCoreAlternatives.any { it.equals(coreFileName, ignoreCase = true) }
                            )
                    }
                if (exactCoreAndExtMatch != null) {
                    return exactCoreAndExtMatch.id
                }
            }
            val exactCoreMatch =
                retroArchSystems.firstOrNull { sys ->
                    sys.retroArchCore.equals(coreFileName, ignoreCase = true) ||
                        sys.retroArchCoreAlternatives.any { it.equals(coreFileName, ignoreCase = true) }
                }
            if (exactCoreMatch != null) {
                return exactCoreMatch.id
            }
        }

        val combinedCore = "${coreName.orEmpty()} ${corePath.orEmpty()}".lowercase()

        when {
            ext in EXT_SNES || combinedCore.contains("snes") || combinedCore.contains("bsnes") -> return "snes"

            ext in EXT_N64 || combinedCore.contains("mupen") || combinedCore.contains("n64") -> return "n64"

            ext == "gba" || combinedCore.contains("mgba") || combinedCore.contains("vba") || combinedCore.contains("gpsp") -> return "gba"

            ext in EXT_GB ||
                combinedCore.contains("gambatte") ||
                combinedCore.contains("sameboy") ||
                combinedCore.contains("gearboy") -> return "gb"

            ext in EXT_NES ||
                combinedCore.contains("fceu") ||
                combinedCore.contains("nestopia") ||
                combinedCore.contains("mesen") -> return "nes"

            ext in EXT_PS1 &&
                (
                    combinedCore.contains("pcsx") ||
                        combinedCore.contains("beetle_psx") ||
                        combinedCore.contains("beetle psx") ||
                        combinedCore.contains("mednafen_psx") ||
                        combinedCore.contains("swanstation")
                ) -> return "ps1"

            ext in EXT_GENESIS ||
                combinedCore.contains("genesis") ||
                combinedCore.contains("picodrive") ||
                combinedCore.contains("blastem") -> return "genesis"

            ext == "nds" ||
                combinedCore.contains("melonds") ||
                combinedCore.contains("desmume") ||
                combinedCore.contains("drastic") -> return "nds"
        }

        if (ext.isNotEmpty()) {
            val matchingByExt = retroArchSystems.filter { ext in it.extensions }
            if (matchingByExt.size == 1) {
                return matchingByExt.first().id
            }
        }

        return EMULATOR_ID_RETROARCH
    }
}
