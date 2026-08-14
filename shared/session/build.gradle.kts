plugins {
    id("megingiard.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stormpanda.megingiard.shared.session"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":shared:core"))
    api(project(":shared:catalog"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
