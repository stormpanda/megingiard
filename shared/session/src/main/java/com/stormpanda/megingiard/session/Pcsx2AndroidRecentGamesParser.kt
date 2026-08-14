package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "Pcsx2AndroidRecentGamesParser"

@Serializable
internal data class Pcsx2AndroidRecentEntry(
    val uri: String? = null,
    val title: String? = null,
    val serial: String? = null,
    val ext: String? = null,
    val platform: String? = null,
)

/**
 * Pure Kotlin parser for PCSX2-derived Android emulators (ARMSX2, AetherSX2, NetherSX2) `recent_games.json` files.
 */
object Pcsx2AndroidRecentGamesParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses PCSX2-Android `recent_games.json` content and extracts an [ActiveGameSession]
     * corresponding to the most recently played game (index 0).
     */
    fun parseMostRecentSession(
        packageName: String,
        jsonContent: String,
    ): ActiveGameSession? {
        if (jsonContent.isBlank()) return null
        return try {
            val entries = json.decodeFromString<List<Pcsx2AndroidRecentEntry>>(jsonContent)
            val first = entries.firstOrNull() ?: return null

            val romPath = SafPathResolver.resolveFilePath(first.uri)
            val gameTitle =
                first.title?.trim()?.takeIf { it.isNotBlank() }
                    ?: SafPathResolver.deriveGameTitle(romPath ?: first.uri ?: "", first.uri)
                    ?: first.serial?.trim()?.takeIf { it.isNotBlank() }
                    ?: return null

            ActiveGameSession(
                packageName = packageName,
                romPath = romPath,
                gameTitle = gameTitle,
                systemId = "ps2",
                coreOrBackend = "PCSX2",
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "parseMostRecentSession: failed to parse PCSX2-Android recent_games JSON - $e")
            null
        }
    }
}
