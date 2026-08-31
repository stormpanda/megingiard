package com.stormpanda.megingiard.rom

private const val TAG = "RomNameCleaner"

private val BRACKET_REGEX = Regex("""\s*(\([^)]*\)|\[[^\]]*\])""")
private val SHORT_DISC_REGEX = Regex("""(?i)\b(cd|d|s|p)\b\s*[0-9]+\b|\b(cd|d|s|p)[0-9]+\b""")

/**
 * Cleans common metadata tags from ROM names (e.g. region, dump, versions)
 * while preserving multi-disc or part identifiers.
 */
fun cleanRomName(name: String): String {
    if (name.isBlank()) return name

    val result =
        BRACKET_REGEX.replace(name) { matchResult ->
            val content = matchResult.value
            val trimmedContent = content.trim()
            if (trimmedContent.length > 2) {
                val innerContent = trimmedContent.substring(1, trimmedContent.length - 1)
                if (innerContent.containsDiscIndicator()) {
                    // Keep the whole bracketed block (including leading space)
                    matchResult.value
                } else {
                    // Remove the bracketed block
                    ""
                }
            } else {
                ""
            }
        }

    // Clean up extra spaces
    return result.replace(Regex("""\s+"""), " ").trim()
}

private val DISC_KEYWORDS = listOf("disc", "disk", "side", "part", "track", "tape")

private fun String.containsDiscIndicator(): Boolean {
    val lowercase = this.lowercase()
    return DISC_KEYWORDS.any { lowercase.contains(it) } || SHORT_DISC_REGEX.containsMatchIn(this)
}
