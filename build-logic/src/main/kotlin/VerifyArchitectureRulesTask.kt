import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private const val DEFAULT_VALUES_DIR = "values"
private const val STRINGS_FILE = "strings.xml"
private val LOCALE_VALUES_DIR_REGEX = Regex("""values-(?:[a-z]{2,3}(?:-r[A-Z]{2})?|b\+[A-Za-z0-9+]+)""")
private val STRING_RESOURCE_TAGS = listOf("string", "plurals", "string-array")

/**
 * Enforces the mechanical AGENTS.md rules (see [ArchitectureRules]) for one module.
 *
 * Runs automatically before every `compile*Kotlin` task. Pre-existing violations are
 * tolerated via [baselineFile]; pass `-PupdateArchitectureBaseline` to rewrite it.
 */
abstract class VerifyArchitectureRulesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stringResourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:Internal
    abstract val baselineFile: RegularFileProperty

    @get:Input
    abstract val updateBaseline: Property<Boolean>

    @TaskAction
    fun verify() {
        val root = rootDir.get().asFile
        val violations = mutableListOf<ArchitectureRules.Violation>()

        for (file in kotlinSourceFiles.files) {
            if (!file.name.endsWith(".kt") && !file.name.endsWith(".java")) continue
            violations += ArchitectureRules.checkSourceFile(file.relativeTo(root).invariantSeparatorsPath, file.readLines())
        }
        violations += checkStringResources(root)

        val baseline = baselineFile.get().asFile
        if (updateBaseline.get()) {
            writeBaseline(baseline, violations)
            return
        }

        val allowed = if (baseline.exists()) ArchitectureRules.parseBaseline(baseline.readLines()) else emptyMap()
        val result = ArchitectureRules.applyBaseline(violations, allowed)
        result.shrinkable.forEach { logger.warn("Architecture baseline can be lowered (${baseline.name}): $it") }

        if (result.failures.isNotEmpty()) {
            val report = result.failures.joinToString("\n  - ")
            throw GradleException(
                "Architecture & Import Rule Verification Failed (${result.failures.size} findings):\n  - $report\n" +
                    "Fix the code. Rules: AGENTS.md / docs/agents/CODING_GUIDE.md §17.",
            )
        }
    }

    private fun checkStringResources(root: File): List<ArchitectureRules.Violation> {
        val byResDir = stringResourceFiles.files.filter { it.name == STRINGS_FILE }.groupBy { it.parentFile.parentFile }
        val violations = mutableListOf<ArchitectureRules.Violation>()
        for ((resDir, files) in byResDir) {
            val defaultFile = files.firstOrNull { it.parentFile.name == DEFAULT_VALUES_DIR } ?: continue
            val translatable = readStringNames(defaultFile, skipUntranslatable = true)
            val all = readStringNames(defaultFile, skipUntranslatable = false)
            files.filter { LOCALE_VALUES_DIR_REGEX.matches(it.parentFile.name) }.forEach { localeFile ->
                violations +=
                    ArchitectureRules.checkTranslations(
                        localePath = localeFile.relativeTo(root).invariantSeparatorsPath,
                        translatableDefaultNames = translatable,
                        allDefaultNames = all,
                        localeNames = readStringNames(localeFile, skipUntranslatable = false),
                    )
            }
            logger.info("Checked string translations in ${resDir.relativeTo(root).invariantSeparatorsPath}")
        }
        return violations
    }

    private fun readStringNames(
        file: File,
        skipUntranslatable: Boolean,
    ): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val names = mutableSetOf<String>()
        for (tag in STRING_RESOURCE_TAGS) {
            val nodes = document.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as Element
                if (skipUntranslatable && element.getAttribute("translatable") == "false") continue
                names += element.getAttribute("name")
            }
        }
        return names
    }

    private fun writeBaseline(
        baseline: File,
        violations: List<ArchitectureRules.Violation>,
    ) {
        val counts = ArchitectureRules.countByKey(violations)
        if (counts.isEmpty()) {
            if (baseline.delete()) logger.lifecycle("Architecture baseline removed (no violations): ${baseline.path}")
            return
        }
        baseline.writeText(ArchitectureRules.formatBaseline(counts).joinToString("\n", postfix = "\n"))
        logger.lifecycle("Architecture baseline written (${counts.size} entries): ${baseline.path}")
    }
}
