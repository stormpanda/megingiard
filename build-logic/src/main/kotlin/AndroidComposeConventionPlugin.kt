import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            val extension = extensions.findByType(CommonExtension::class.java)
            extension?.buildFeatures?.compose = true
            extensions.findByType(ComposeCompilerGradlePluginExtension::class.java)?.apply {
                stabilityConfigurationFile.set(
                    rootProject.layout.projectDirectory.file("compose_stability_config.conf"),
                )
            }
        }
    }
}
