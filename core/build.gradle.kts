plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

// Shared by the phone app and the Wear OS app: domain models and rules, the Open-Meteo client
// and one-shot location lookup. No UI and no resources.
android {
    namespace = "com.ah0890.dailyweather.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.core)
    api(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
