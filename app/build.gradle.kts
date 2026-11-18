plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "online.jaringansaya.notifier"
    compileSdk = 35

    defaultConfig {
        applicationId = "online.jaringansaya.notifier"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Satu keystore tetap supaya APK versi baru bisa di-install menimpa versi lama
    // tanpa harus uninstall (antrean & pengaturan tidak hilang).
    // Simpan repo ini sebagai PRIVATE.
    signingConfigs {
        create("shared") {
            storeFile = file("jsnotifier.keystore")
            storePassword = "jsnotifier"
            keyAlias = "jsnotifier"
            keyPassword = "jsnotifier"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
