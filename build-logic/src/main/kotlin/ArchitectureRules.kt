import java.io.File

/**
 * Pure, Gradle-free rule engine behind `verifyArchitectureRules`.
 *
 * Enforces the mechanically checkable rules of `AGENTS.md` so that every
 * `compile*Kotlin` run (and `./scripts/verify.sh`) fails fast instead of relying on
 * an agent or reviewer to grep for them. Every rule is an exact, documented pattern —
 * see the rule table in `docs/agents/CODING_GUIDE.md` (§17).
 *
 * Pre-existing violations are tolerated through a per-module, count-based baseline
 * (`<module>/architecture-baseline.txt`); any *increase* fails the build.
 */
object ArchitectureRules {
    data class Violation(
        val ruleId: String,
        val path: String,
        val line: Int,
        val message: String,
    )

    /** Baseline key: one entry per (rule, file) pair with the tolerated violation count. */
    data class BaselineKey(
        val ruleId: String,
        val path: String,
    )

    data class BaselineResult(
        val failures: List<String>,
        val shrinkable: List<String>,
    )

    const val STAR_IMPORT = "STAR_IMPORT"
    const val CORE_ANDROID_IMPORT = "CORE_ANDROID_IMPORT"
    const val DOMAIN_UI_IMPORT = "DOMAIN_UI_IMPORT"
    const val ANDROID_LOG = "ANDROID_LOG"
    const val ENUM_VALUES = "ENUM_VALUES"
    const val JAVA_MATH = "JAVA_MATH"
    const val BARE_JOB = "BARE_JOB"
    const val VOLUME_UUID_PATH = "VOLUME_UUID_PATH"
    const val PUBLIC_MUTABLE_STATE_FLOW = "PUBLIC_MUTABLE_STATE_FLOW"
    const val HEURISTIC_SUBSTRING = "HEURISTIC_SUBSTRING"
    const val INLINE_FONT_SIZE = "INLINE_FONT_SIZE"
    const val HARDCODED_COLOR = "HARDCODED_COLOR"
    const val UNUSED_TAG = "UNUSED_TAG"
    const val MISSING_TRANSLATION = "MISSING_TRANSLATION"
    const val STALE_TRANSLATION = "STALE_TRANSLATION"

    private const val APP_LOG_FILE = "AppLog.kt"
    private val THEME_DEFINITION_FILES = setOf("AppTheme.kt", "AppColors.kt")

    private val ENUM_VALUES_REGEX = Regex("""\.values\(\)""")
    private val JAVA_MATH_REGEX = Regex("""\bjava\.lang\.Math\b|(?<![\w.])Math\.[a-zA-Z]+\(""")
    private val BARE_JOB_REGEX = Regex("""(?<!\w)Job\(\)""")
    private val VOLUME_UUID_REGEX = Regex("""/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}""")

    // Top-level or first-level member declaration (indent <= 4) that is not private.
    private val MUTABLE_STATE_FLOW_DECL_REGEX =
        Regex(
            """^ {0,4}(?:(?:public|internal|protected|override|open|@\S+)\s+)*va[lr]\s+\w+\s*(?::\s*MutableStateFlow|=\s*MutableStateFlow)""",
        )
    private val HEURISTIC_SUBSTRING_REGEX = Regex("""\.contains\(\s*"(?:launcher|home|game)"""", RegexOption.IGNORE_CASE)
    private val INLINE_FONT_SIZE_REGEX = Regex("""fontSize\s*=\s*[0-9.]+\.sp\b""")
    private val HARDCODED_COLOR_REGEX = Regex("""\bColor\(\s*0x[0-9A-Fa-f]{6,8}\s*\)""")
    private val TAG_DECL_REGEX = Regex("""private\s+const\s+val\s+TAG\b""")
    private val TAG_WORD_REGEX = Regex("""\bTAG\b""")

    /**
     * Checks one Kotlin/Java source file.
     *
     * @param relativePath path relative to the repository root, using `/` separators.
     * @param lines the file content split into lines.
     */
    fun checkSourceFile(
        relativePath: String,
        lines: List<String>,
    ): List<Violation> {
        val path = relativePath.replace(File.separatorChar, '/')
        val fileName = path.substringAfterLast('/')
        val isMain = path.contains("/src/main/")
        val isSharedCore = path.startsWith("shared/core/")
        val isDomain = path.contains("domain/src/")
        val isAppLog = fileName == APP_LOG_FILE
        val isThemeDefinition = fileName in THEME_DEFINITION_FILES

        val violations = mutableListOf<Violation>()

        fun add(
            rule: String,
            lineNo: Int,
            message: String,
        ) {
            violations += Violation(rule, path, lineNo, message)
        }

        lines.forEachIndexed { index, line ->
            val lineNo = index + 1
            val trimmed = line.trim()
            if (isCommentLine(trimmed)) return@forEachIndexed

            if (trimmed.startsWith("import ")) {
                if (trimmed.endsWith(".*")) {
                    add(STAR_IMPORT, lineNo, "Star import forbidden (§8.2): '$trimmed'")
                }
                if (isSharedCore && (trimmed.startsWith("import android.") || trimmed.startsWith("import androidx."))) {
                    add(CORE_ANDROID_IMPORT, lineNo, "Pure JVM module :shared:core must not import Android framework (§6): '$trimmed'")
                }
                if (isDomain && (trimmed.startsWith("import androidx.compose.") || trimmed.startsWith("import android.view."))) {
                    add(DOMAIN_UI_IMPORT, lineNo, "Domain layer module must not import UI framework classes (§6): '$trimmed'")
                }
            }

            if (!isAppLog && (trimmed == "import android.util.Log" || line.contains("android.util.Log."))) {
                add(ANDROID_LOG, lineNo, "Use AppLog instead of android.util.Log (§8.4)")
            }

            if (!isMain) return@forEachIndexed

            if (ENUM_VALUES_REGEX.containsMatchIn(line)) {
                add(ENUM_VALUES, lineNo, "Use enum.entries instead of values() (§8.1)")
            }
            if (JAVA_MATH_REGEX.containsMatchIn(line)) {
                add(JAVA_MATH, lineNo, "Use kotlin.math instead of java.lang.Math (§8.1)")
            }
            if (line.contains("CoroutineScope(") && BARE_JOB_REGEX.containsMatchIn(line)) {
                add(BARE_JOB, lineNo, "Class-level scopes use SupervisorJob(), not Job() (§10.1)")
            }
            if (VOLUME_UUID_REGEX.containsMatchIn(line)) {
                add(VOLUME_UUID_PATH, lineNo, "Hardcoded storage volume UUID path; use SafPathResolver.getStorageVolumeRoots() (§8.3)")
            }
            if (MUTABLE_STATE_FLOW_DECL_REGEX.containsMatchIn(line)) {
                add(PUBLIC_MUTABLE_STATE_FLOW, lineNo, "MutableStateFlow must be private; expose StateFlow via asStateFlow() (§7.1)")
            }
            if (HEURISTIC_SUBSTRING_REGEX.containsMatchIn(line)) {
                add(HEURISTIC_SUBSTRING, lineNo, "Substring role guessing is forbidden without operator approval (§7.5)")
            }
            if (!isThemeDefinition && INLINE_FONT_SIZE_REGEX.containsMatchIn(line)) {
                add(INLINE_FONT_SIZE, lineNo, "Use MaterialTheme.typography instead of inline fontSize (§16.1)")
            }
            if (!isThemeDefinition && HARDCODED_COLOR_REGEX.containsMatchIn(line)) {
                add(HARDCODED_COLOR, lineNo, "Use LocalAppColors / MaterialTheme.colorScheme instead of Color(0x…) (§16.2)")
            }
        }

        if (isMain) {
            val tagDeclLine = lines.indexOfFirst { TAG_DECL_REGEX.containsMatchIn(it) }
            if (tagDeclLine >= 0) {
                val usages = lines.sumOf { TAG_WORD_REGEX.findAll(it).count() }
                if (usages < 2) {
                    add(UNUSED_TAG, tagDeclLine + 1, "TAG declared but never used — add AppLog calls, do not delete TAG (§8.4)")
                }
            }
        }
        return violations
    }

    /**
     * Compares the default `values/strings.xml` names against one locale.
     *
     * @param translatableDefaultNames default resource names excluding `translatable="false"`.
     * @param allDefaultNames every default resource name.
     * @param localeNames resource names present in the locale file.
     */
    fun checkTranslations(
        localePath: String,
        translatableDefaultNames: Set<String>,
        allDefaultNames: Set<String>,
        localeNames: Set<String>,
    ): List<Violation> {
        val path = localePath.replace(File.separatorChar, '/')
        val missing = (translatableDefaultNames - localeNames).sorted()
        val stale = (localeNames - allDefaultNames).sorted()
        return missing.map { Violation(MISSING_TRANSLATION, path, 0, "Missing translation for '$it' (§9.2)") } +
            stale.map { Violation(STALE_TRANSLATION, path, 0, "Translation '$it' has no default string (§9.2)") }
    }

    fun parseBaseline(lines: List<String>): Map<BaselineKey, Int> =
        lines
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { entry ->
                val parts = entry.split('|')
                if (parts.size != 3) return@mapNotNull null
                val count = parts[2].trim().toIntOrNull() ?: return@mapNotNull null
                BaselineKey(parts[0].trim(), parts[1].trim()) to count
            }.toMap()

    fun countByKey(violations: List<Violation>): Map<BaselineKey, Int> =
        violations.groupingBy { BaselineKey(it.ruleId, it.path) }.eachCount()

    fun formatBaseline(counts: Map<BaselineKey, Int>): List<String> =
        listOf(
            "# Architecture rule baseline — pre-existing violations tolerated by verifyArchitectureRules.",
            "# Format: RULE_ID|path/from/repo/root|max_count. Lower or delete entries as you fix them;",
            "# never raise a count to make a new violation pass.",
        ) +
            counts.entries
                .sortedWith(compareBy({ it.key.ruleId }, { it.key.path }))
                .map { "${it.key.ruleId}|${it.key.path}|${it.value}" }

    /** Applies the baseline: returns the violations exceeding it and the entries that can shrink. */
    fun applyBaseline(
        violations: List<Violation>,
        baseline: Map<BaselineKey, Int>,
    ): BaselineResult {
        val counts = countByKey(violations)
        val failures = mutableListOf<String>()
        counts.forEach { (key, count) ->
            val allowed = baseline[key] ?: 0
            if (count > allowed) {
                violations.filter { BaselineKey(it.ruleId, it.path) == key }.forEach { v ->
                    val location = if (v.line > 0) "${v.path}:${v.line}" else v.path
                    failures += "$location: [${v.ruleId}] ${v.message}"
                }
                if (allowed > 0) {
                    failures += "${key.path}: [${key.ruleId}] $count violations exceed baseline of $allowed"
                }
            }
        }
        val shrinkable =
            baseline
                .filter { (key, allowed) -> (counts[key] ?: 0) < allowed }
                .map { (key, allowed) -> "${key.ruleId}|${key.path}: baseline $allowed, actual ${counts[key] ?: 0}" }
        return BaselineResult(failures, shrinkable)
    }

    private fun isCommentLine(trimmed: String): Boolean = trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
}
