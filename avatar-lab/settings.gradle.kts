pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("com.android.application") version "8.13.0"
        id("com.android.library") version "8.13.0"
        id("org.jetbrains.kotlin.android") version "2.2.20"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "IskraAvatarLab"
include(":avatar", ":avatar-test")
project(":avatar").projectDir = file("../avatar")
project(":avatar-test").projectDir = file("../avatar-test")
