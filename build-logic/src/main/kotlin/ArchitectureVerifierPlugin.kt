import org.gradle.api.Plugin
import org.gradle.api.Project

private const val BASELINE_FILE_NAME = "architecture-baseline.txt"
private const val UPDATE_BASELINE_PROPERTY = "updateArchitectureBaseline"

class ArchitectureVerifierPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val verifyTask =
            target.tasks.register("verifyArchitectureRules", VerifyArchitectureRulesTask::class.java) {
                group = "verification"
                description = "Verifies mechanical AGENTS.md rules (imports, logging, state, design tokens, translations)."
                rootDir.set(target.rootDir)
                baselineFile.set(target.layout.projectDirectory.file(BASELINE_FILE_NAME))
                updateBaseline.set(
                    target.providers
                        .gradleProperty(UPDATE_BASELINE_PROPERTY)
                        .map { true }
                        .orElse(false),
                )
                val srcDir = target.file("src")
                if (srcDir.exists()) {
                    kotlinSourceFiles.setFrom(
                        target.fileTree(srcDir) {
                            include("**/*.kt")
                            include("**/*.java")
                        },
                    )
                    stringResourceFiles.setFrom(
                        target.fileTree(srcDir) {
                            include("*/res/values*/strings.xml")
                        },
                    )
                }
                val baseline = target.file(BASELINE_FILE_NAME)
                if (baseline.exists()) {
                    inputs.file(baseline).withPropertyName("baseline")
                }
            }

        target.tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
            dependsOn(verifyTask)
        }
    }
}
