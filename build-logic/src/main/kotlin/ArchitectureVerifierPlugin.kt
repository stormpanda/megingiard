import org.gradle.api.Plugin
import org.gradle.api.Project

class ArchitectureVerifierPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val verifyTask =
            target.tasks.register("verifyArchitectureRules", VerifyArchitectureRulesTask::class.java) {
                rootDir.set(target.rootDir)
                val srcDir = target.file("src")
                if (srcDir.exists()) {
                    kotlinSourceFiles.setFrom(
                        target.fileTree(srcDir) {
                            include("**/*.kt")
                            include("**/*.java")
                        },
                    )
                }
            }

        target.tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
            dependsOn(verifyTask)
        }
    }
}
