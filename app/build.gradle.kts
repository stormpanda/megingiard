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
val expectedSigningSha256: String = run {
    val props = java.util.Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
    (props.getProperty("megingiard.signing.sha256") ?: "")
        .replace(":", "")
        .replace(" ", "")
        .uppercase()
}

android {
    namespace = "com.stormpanda.megingiard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stormpanda.megingiard"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0-SNAPSHOT"

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
