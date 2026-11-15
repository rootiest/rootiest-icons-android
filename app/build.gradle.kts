// AGP 9 compiles Kotlin itself: no org.jetbrains.kotlin.android plugin.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Settings > About > Open-source licenses: the dependencies and their licenses, collected at build time
    alias(libs.plugins.aboutlibraries)
}

// Settings > About > Open-source licenses also lists what is not a Gradle dependency: aboutlibraries/ holds the Tabler
// Icons the app's buttons and settings rows use (res/drawable/ic_tabler_*.xml) and their license
aboutLibraries {
    collect {
        configPath = file("aboutlibraries")
    }
}

val appVersion = rootProject.extra["appVersion"] as String
val releaseKey = rootProject.extra["releaseKey"] as java.util.Properties?

android {
    namespace = "dev.rootiest.icons"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.rootiest.icons"
        minSdk = 29
        targetSdk = 37
        versionCode = rootProject.extra["appVersionCode"] as Int
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKey != null) {
            create("release") {
                storeFile = file(releaseKey.getProperty("storeFile"))
                storePassword = releaseKey.getProperty("storePassword")
                keyAlias = releaseKey.getProperty("keyAlias")
                keyPassword = releaseKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // install only for the main user: adb's default is every profile, and a stale copy in a Private space blocks
    // installs signed with another key (Obtainium's "failureConflict")
    installation {
        installOptions += listOf("--user", "0")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    // draws the SVGs: the icons, their masks and their CSS classes
    implementation(libs.androidsvg)
    implementation(libs.aboutlibraries.compose.m3)
    debugImplementation(libs.compose.ui.tooling)
}
