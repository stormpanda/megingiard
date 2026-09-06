import java.util.Properties

plugins {
    id("megingiard.android.application")
    id("megingiard.android.compose")
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { stream -> props.load(stream) }
}

android {
    namespace = "com.stormpanda.megingiard.gamefocus"

    defaultConfig {
        applicationId = "com.stormpanda.megingiard.gamefocus"
        versionCode = 10
        versionName = "0.9.2"
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
                output.outputFileName = "Megingiard-GameFocus-v${variant.versionName}.apk"
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

dependencies {
    implementation(project(":gamefocus:domain"))
    implementation(project(":shared:ui"))
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

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
