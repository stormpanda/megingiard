plugins {
    `kotlin-dsl`
}

group = "com.stormpanda.megingiard.buildlogic"

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly("com.android.tools.build:gradle:8.13.2")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    compileOnly("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
    compileOnly("org.jetbrains.kotlinx.kover:org.jetbrains.kotlinx.kover.gradle.plugin:0.9.1")
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "megingiard.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "megingiard.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "megingiard.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("kotlinJvm") {
            id = "megingiard.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("architectureVerifier") {
            id = "megingiard.architecture.verifier"
            implementationClass = "ArchitectureVerifierPlugin"
        }
    }
}
