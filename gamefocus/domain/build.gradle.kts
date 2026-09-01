plugins {
    id("megingiard.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stormpanda.megingiard.gamefocus.domain"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    api(project(":shared:core"))
    api(project(":shared:catalog"))
    api(project(":shared:session"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.documentfile)
}
