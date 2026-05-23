import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---------------------------------------------------------------------------
// Signature pinning: read the expected release signing-certificate SHA-256
// from local.properties (key: `megingiard.signing.sha256`). When empty,
// runtime pinning becomes a no-op — appropriate for debug builds signed with
// the Android default debug keystore. To populate, run:
//   keytool -list -v -keystore <release.jks> -alias <alias> | grep SHA-256
// and paste the uppercase hex value (with or without colons) into
// local.properties.
// ---------------------------------------------------------------------------
val localProperties = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { stream -> props.load(stream) }
}

private val SIGNING_SHA256_PATTERN = Regex("[0-9A-F]{64}")
val expectedSigningSha256Raw: String =
    (localProperties.getProperty("megingiard.signing.sha256") ?: "")
        .replace(":", "")
        .replace(" ", "")
        .uppercase()
val expectedSigningSha256: String =
    if (expectedSigningSha256Raw.matches(SIGNING_SHA256_PATTERN)) expectedSigningSha256Raw else ""
val expectedSigningSha256IsMalformed: Boolean =
    expectedSigningSha256Raw.isNotBlank() && expectedSigningSha256.isBlank()

android {
    namespace = "com.stormpanda.megingiard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stormpanda.megingiard"
        minSdk = 33
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField(
            "String",
            "EXPECTED_SIGNING_SHA256",
            "\"$expectedSigningSha256\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            if (variant.buildType.name == "release") {
                output.outputFileName = "Megingiard-v${variant.versionName}.apk"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

// Ensure the privileged-mirror DEX asset is built before any app packaging task.
afterEvaluate {
    tasks.matching { it.name.startsWith("merge") && it.name.contains("Assets") }.configureEach {
        dependsOn(":mirrorserver:dex")
    }
    tasks.matching { it.name.startsWith("package") || it.name.startsWith("generate") && it.name.contains("Assets") }.configureEach {
        dependsOn(":mirrorserver:dex")
    }

    // Fail release builds when no signing-certificate SHA-256 has been
    // configured. Without it, SignatureGuard runs in "Skipped" mode and the
    // shipped APK has no tamper protection — almost certainly a mistake.
    // Devs who knowingly want an unpinned local release build can opt out via
    //   ./gradlew assembleRelease -Pmegingiard.allowUnpinnedRelease=true
    val allowUnpinned = (project.findProperty("megingiard.allowUnpinnedRelease") as? String)
        ?.equals("true", ignoreCase = true) == true
    val releaseGuardTasks = listOf(
        "assembleRelease",
        "bundleRelease",
        "packageRelease",
    )
    releaseGuardTasks.forEach { taskName ->
        tasks.matching { it.name == taskName }.configureEach {
            doFirst {
                if ((expectedSigningSha256.isBlank() || expectedSigningSha256IsMalformed) && !allowUnpinned) {
                    throw GradleException(
                        "Release build aborted: 'megingiard.signing.sha256' must be set " +
                            "to a 64-character hex SHA-256 fingerprint in local.properties. " +
                            "Without it, SignatureGuard cannot pin the release APK identity.\n" +
                            "  1. Read your release cert SHA-256:\n" +
                            "       keytool -list -v -keystore megingiard.jks -alias release\n" +
                            "  2. Add to local.properties:\n" +
                            "       megingiard.signing.sha256=AB:CD:…\n" +
                            "Override (NOT for distribution) with " +
                            "-Pmegingiard.allowUnpinnedRelease=true"
                    )
                }
            }
        }
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.material)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.reorderable)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
