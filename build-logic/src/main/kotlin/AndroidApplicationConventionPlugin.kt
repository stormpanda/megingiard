import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
                apply("megingiard.architecture.verifier")
                apply("org.jetbrains.kotlinx.kover")
            }

            extensions.configure<ApplicationExtension> {
                compileSdk = 35
                defaultConfig {
                    minSdk = 33
                    targetSdk = 35
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                jvmToolchain(17)
            }

            tasks.matching { it.name == "testReleaseUnitTest" }.configureEach {
                enabled = false
            }
        }
    }
}
