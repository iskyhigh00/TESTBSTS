plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cl.reabill.monitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.reabill.monitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 219
        versionName = "2.3.6"

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        val updateToken = System.getenv("APK_READ_TOKEN") ?: ""
        buildConfigField("String", "UPDATE_TOKEN", "\"$updateToken\"")
        val updateRepo = System.getenv("APK_REPO") ?: ""
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")

        buildConfigField("String", "SUPA_URL", "\"${System.getenv("SUPA_URL") ?: ""}\"")
        buildConfigField("String", "SUPA_KEY", "\"${System.getenv("SUPA_KEY") ?: ""}\"")
    }

    signingConfigs {
        create("stable") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable").takeIf { it.storeFile != null } ?: signingConfigs.getByName("debug")
        }
        release {

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("stable").takeIf { it.storeFile != null } ?: signingConfigs.getByName("debug")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")

    implementation("com.google.mlkit:text-recognition:16.0.1")
}
