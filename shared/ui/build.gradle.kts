plugins {
    id("megingiard.android.library")
    id("megingiard.android.compose")
}

android {
    namespace = "com.stormpanda.megingiard.shared.ui"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":shared:core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)
    // Not referenced in code: reorderable transitively upgrades Compose Foundation beyond the BOM (1.6.x)
    // to 1.7+, which GamepadScaffold needs for LocalBringIntoViewSpec. Remove once the Compose BOM is raised.
    implementation(libs.reorderable)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
