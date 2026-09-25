plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.kosherswitch"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.kosherswitch"
        minSdk = 29
        targetSdk = 34
        versionCode = 12
        versionName = "1.2"
        // Only the phone's own chip type; keeps the app small.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        // The final app is signed with this key. Every future update MUST use the same key,
        // or Android won't accept it (and the app can only be removed by a factory reset).
        create("kosher") {
            storeFile = file("../keystore/kosher-switch.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("kosher") }
        getByName("release") {
            signingConfig = signingConfigs.getByName("kosher")
            isMinifyEnabled = false
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Jewish calendar, parsha, holidays and zmanim (LGPL 2.1)
    implementation("com.kosherjava:zmanim:2.5.0")
    // On-device AI (runs offline on the phone; Apache 2.0)
    implementation("com.google.mediapipe:tasks-genai:0.10.35")
}
