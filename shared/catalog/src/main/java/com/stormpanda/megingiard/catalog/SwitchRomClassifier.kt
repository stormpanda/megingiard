package com.stormpanda.megingiard.catalog

import com.stormpanda.megingiard.AppLog
import java.util.Locale

private const val TAG = "SwitchRomClassifier"

private const val TITLE_ID_LENGTH = 16
private const val TITLE_ID_SUFFIX_LENGTH = 3
private const val BASE_TITLE_ID_SUFFIX = "000"
private const val UPDATE_TITLE_ID_SUFFIX = "800"

private val TITLE_ID_REGEX = Regex("""(?:\[|-|\b)([A-Fa-f0-9]{$TITLE_ID_LENGTH})(?:\]|-|\b)""")
private val UPDATE_TAG_REGEX = Regex("""(?i)(?:\[|\(|\b)(?:update|upd|patch)(?:\]|\)|\b)""")
private val DLC_TAG_REGEX = Regex("""(?i)(?:\[|\(|\b)(?:dlc(?:\s*\d+)?|add-?on)(?:\]|\)|\b)""")
private val BASE_TAG_REGEX = Regex("""(?i)(?:\[|\(|\b)base(?:\]|\)|\b)""")
private val VERSION_TAG_REGEX = Regex("""(?i)\[v([0-9]+)\]""")

/**
 * Utility to inspect Nintendo Switch ROM filenames (.nsp, .nsz, .xci, .xcz)
 * and classify them as Base Game, Update, or DLC.
 */
object SwitchRomClassifier {
    /**
     * Extracts a 16-character hexadecimal Nintendo Switch Title ID if present in the filename.
     */
    fun extractTitleId(fileName: String): String? {
        val match = TITLE_ID_REGEX.find(fileName) ?: return null
        return match.groupValues[1].uppercase(Locale.US)
    }

    /**
     * Returns true if the given filename represents a standalone playable Switch Base Game.
     */
    fun isSwitchBaseGame(fileName: String): Boolean {
        if (isSwitchDlc(fileName) || isSwitchUpdate(fileName)) {
            // Explicit base tag takes precedence if present
            if (BASE_TAG_REGEX.containsMatchIn(fileName)) {
                AppLog.d(TAG, "'$fileName' has explicit [Base] tag, classifying as Base Game")
                return true
            }
            return false
        }
        return true
    }

    /**
     * Returns true if the filename represents an update or patch package.
     */
    fun isSwitchUpdate(fileName: String): Boolean {
        if (BASE_TAG_REGEX.containsMatchIn(fileName)) return false

        if (UPDATE_TAG_REGEX.containsMatchIn(fileName)) {
            return true
        }

        val titleId = extractTitleId(fileName)
        if (titleId != null && titleId.endsWith(UPDATE_TITLE_ID_SUFFIX)) {
            return true
        }

        val versionMatch = VERSION_TAG_REGEX.find(fileName)
        if (versionMatch != null) {
            val versionNum = versionMatch.groupValues[1].toLongOrNull() ?: 0L
            if (versionNum > 0L && titleId?.endsWith(BASE_TITLE_ID_SUFFIX) != true) {
                return true
            }
        }

        return false
    }

    /**
     * Returns true if the filename represents an Add-On Content (DLC) package.
     */
    fun isSwitchDlc(fileName: String): Boolean {
        if (BASE_TAG_REGEX.containsMatchIn(fileName)) return false

        if (DLC_TAG_REGEX.containsMatchIn(fileName)) {
            return true
        }

        val titleId = extractTitleId(fileName)
        if (titleId != null) {
            val suffix = titleId.takeLast(TITLE_ID_SUFFIX_LENGTH)
            if (suffix != BASE_TITLE_ID_SUFFIX && suffix != UPDATE_TITLE_ID_SUFFIX) {
                return true
            }
        }

        return false
    }
}
