plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pl.iskra.avatar.test"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "pl.iskra.avatar.test.v4"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.6-test"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "glb" }
}
dependencies { implementation(project(":avatar")); implementation("com.squareup.okhttp3:okhttp:4.12.0"); testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20250517"); testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0"); testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0") }
