plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pl.iskra.avatar.test"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "pl.iskra.avatar.lab"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.6.5-test"
        ndk { abiFilters += if(project.findProject(":avatar-voice")!=null) listOf("arm64-v8a") else listOf("arm64-v8a", "x86_64") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    androidResources { noCompress += "glb" }
}
dependencies {
 implementation(project(":avatar"))
 if(findProject(":avatar-voice")!=null) implementation(project(":avatar-voice"))
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2");testImplementation("org.json:json:20250517")
 testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0");testImplementation("com.squareup.okhttp3:okhttp-tls:4.12.0")
}
