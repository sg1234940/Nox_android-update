plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pl.iskra.avatar"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.android.filament:filament-android:1.66.0")
    implementation("com.google.android.filament:gltfio-android:1.66.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
