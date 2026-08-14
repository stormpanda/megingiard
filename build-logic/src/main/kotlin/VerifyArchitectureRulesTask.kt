import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class VerifyArchitectureRulesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun verify() {
        val errors = mutableListOf<String>()
        val rootPath = rootDir.get().asFile.path

        for (file in kotlinSourceFiles.files) {
            if (!file.name.endsWith(".kt") && !file.name.endsWith(".java")) continue

            val lines = file.readLines()
            val relativePath = file.path.substringAfter(rootPath + File.separator)

            lines.forEachIndexed { index, line ->
                val lineNo = index + 1
                val trimmed = line.trim()

                // Rule 1: No wildcard/star imports
                if (trimmed.startsWith("import ") && trimmed.endsWith(".*")) {
                    errors.add("$relativePath:$lineNo: Star import forbidden: '$trimmed'")
                }

                // Rule 2: Pure JVM :shared:core must not import android.* or androidx.*
                if (relativePath.startsWith("shared${File.separator}core") || relativePath.startsWith("shared/core")) {
                    if (trimmed.startsWith("import android.") || trimmed.startsWith("import androidx.")) {
                        errors.add("$relativePath:$lineNo: Pure JVM module :shared:core must not import Android framework: '$trimmed'")
                    }
                }

                // Rule 3: Domain modules must not import Android UI or Compose classes
                if (relativePath.contains("domain${File.separator}src") || relativePath.contains("domain/src")) {
                    if (trimmed.startsWith("import androidx.compose.") || trimmed.startsWith("import android.view.")) {
                        errors.add("$relativePath:$lineNo: Domain layer module must not import UI framework classes: '$trimmed'")
                    }
                }
            }
        }

        if (errors.isNotEmpty()) {
            val report = errors.joinToString("\n  - ")
            throw GradleException("Architecture & Import Rule Verification Failed (${errors.size} violations):\n  - $report")
        }
    }
}
