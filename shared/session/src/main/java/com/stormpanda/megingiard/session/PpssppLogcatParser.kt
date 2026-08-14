package com.stormpanda.megingiard.session

import com.stormpanda.megingiard.AppLog

private const val TAG = "PpssppLogcatParser"
private val BOOT_REGEX = Regex("""\[BOOT\]\s+Booted\s+(.+?)(?:\.\.\.)?$""")
private val PSP_EXTENSIONS = setOf("iso", "cso", "pbp", "chd", "elf")

/**
 * Pure Kotlin parser for PPSSPP `[BOOT] Booted <path>` logcat output lines.
 */
object PpssppLogcatParser {
    /**
     * Parses logcat output lines for the most recent PPSSPP `[BOOT] Booted` entry.
     * Returns an [ActiveGameSession] for the booted ROM.
     */
    fun parseLatestBootedSession(
        packageName: String,
        logcatContent: String,
    ): ActiveGameSession? {
        if (logcatContent.isBlank()) return null
        return try {
            var lastUriOrPath: String? = null

            val lines = logcatContent.lineSequence()
            for (line in lines) {
                val match = BOOT_REGEX.find(line)
                if (match != null) {
                    val raw = match.groupValues[1].trim()
                    if (raw.isNotBlank()) {
                        lastUriOrPath = raw
                    }
                }
            }

            val rawUriOrPath = lastUriOrPath ?: return null
            val resolvedPath = SafPathResolver.resolveFilePath(rawUriOrPath) ?: rawUriOrPath
            val derivedTitle = SafPathResolver.deriveGameTitle(resolvedPath, rawUriOrPath, PSP_EXTENSIONS) ?: return null

            ActiveGameSession(
                packageName = packageName,
                romPath = resolvedPath,
                gameTitle = derivedTitle,
                systemId = "psp",
                coreOrBackend = "PPSSPP",
            )
        } catch (e: Exception) {
            AppLog.w(TAG, "parseLatestBootedSession: failed to parse PPSSPP logcat content - $e")
            null
        }
    }
}
