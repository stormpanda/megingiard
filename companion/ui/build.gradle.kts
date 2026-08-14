import java.util.Properties

plugins {
    id("megingiard.android.application")
    id("megingiard.android.compose")
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
        versionCode = 9
        versionName = "0.9.0-SNAPSHOT"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "EXPECTED_SIGNING_SHA256",
            "\"$expectedSigningSha256\""
        )
    }

    signingConfigs {
        create("release") {
            val keyPasswordProp = localProperties.getProperty("megingiard.keystore.key.password")
            val storePasswordProp = localProperties.getProperty("megingiard.keystore.password")
            val aliasProp = localProperties.getProperty("megingiard.keystore.alias")

            if (!keyPasswordProp.isNullOrBlank() && !storePasswordProp.isNullOrBlank() && !aliasProp.isNullOrBlank()) {
                storeFile = rootProject.file("megingiard.jks")
                storePassword = storePasswordProp
                keyAlias = aliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
        release {
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig != null && releaseSigningConfig.storeFile?.exists() == true) {
                signingConfig = releaseSigningConfig
            }
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
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Register native C unit test execution task
// Task to fail release builds when no signing-certificate SHA-256 has been configured.
abstract class ValidateReleaseSignatureTask : DefaultTask() {
    @get:Input
    abstract val expectedSha256: Property<String>

    @get:Input
    abstract val malformed: Property<Boolean>

    @get:Input
    abstract val allowUnpinned: Property<Boolean>

    @TaskAction
    fun validate() {
        val sha = expectedSha256.get()
        val isMalformed = malformed.get()
        val unpinned = allowUnpinned.get()
        if ((sha.isBlank() || isMalformed) && !unpinned) {
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

val validateReleaseSignature = tasks.register<ValidateReleaseSignatureTask>("validateReleaseSignature") {
    expectedSha256.set(expectedSigningSha256)
    malformed.set(expectedSigningSha256IsMalformed)
    allowUnpinned.set(
        (project.findProperty("megingiard.allowUnpinnedRelease") as? String)
            ?.equals("true", ignoreCase = true) == true
    )
}

// Register native C unit test execution task
val nativeCTest = tasks.register<Exec>("nativeCTest") {
    group = "verification"
    description = "Compiles and executes native C unit tests."
    workingDir = rootProject.projectDir
    commandLine("./scripts/run_native_tests.sh")
}

// Ensure the privileged-mirror DEX asset is built before any app packaging task, and native C tests run before unit tests.
afterEvaluate {
    tasks.matching { it.name.contains("UnitTest") || it.name == "test" }.configureEach {
        dependsOn(nativeCTest)
    }
    tasks.matching { it.name.startsWith("merge") && it.name.contains("Assets") }.configureEach {
        dependsOn(":mirrorserver:dex")
    }
    tasks.matching { it.name.startsWith("package") || it.name.startsWith("generate") && it.name.contains("Assets") }.configureEach {
        dependsOn(":mirrorserver:dex")
    }
    tasks.matching { it.name in listOf("assembleRelease", "bundleRelease", "packageRelease") }.configureEach {
        dependsOn(validateReleaseSignature)
    }
}

dependencies {
    implementation(project(":companion:domain"))
    implementation(project(":shared:catalog"))
    implementation(project(":shared:media"))
    implementation(project(":shared:session"))

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
    implementation(libs.androidx.palette.ktx)
    implementation(libs.reorderable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hiddenapibypass)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
