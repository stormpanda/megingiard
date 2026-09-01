plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":shared:core"))
    kover(project(":shared:catalog"))
    kover(project(":shared:media"))
    kover(project(":shared:session"))
    kover(project(":companion:domain"))
    kover(project(":companion:ui"))
    kover(project(":gamefocus:domain"))
    kover(project(":gamefocus:ui"))
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*BuildConfig*",
                    "*NativeBinaryHashes*",
                    "*.R",
                    "*.R$*",
                    "*ComposableSingletons*",
                )
                annotatedBy(
                    "*Composable*",
                    "*Preview*",
                    "*Generated*",
                )
            }
        }
        total {
            html {
                title.set("Megingiard Test Coverage Report")
            }
            xml {
                onCheck.set(false)
            }
            log {
                onCheck.set(false)
            }
        }
    }
}