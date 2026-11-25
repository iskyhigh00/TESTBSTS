import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Install the approved launcher artwork before Android configures resource inputs.
val launcherArchive = rootProject.file("../BILLMON_iconos.zip")
val launcherRes = file("src/main/res")
ZipFile(launcherArchive).use { archive ->
    val expected = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").flatMap { density ->
        listOf("ic_launcher", "ic_launcher_round", "ic_launcher_foreground", "ic_launcher_monochrome").map { name ->
            "mipmap-$density/$name.png"
        }
    }
    expected.forEach { path ->
        val entry = requireNotNull(archive.getEntry(path)) { "Missing launcher resource: $path" }
        val bytes = archive.getInputStream(entry).use { it.readBytes() }
        val target = launcherRes.resolve(path)
        if (!target.exists() || !target.readBytes().contentEquals(bytes)) {
            target.parentFile.mkdirs()
            target.writeBytes(bytes)
        }
    }
}

android {
    namespace = "cl.reabill.monitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.reabill.monitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 258
        versionName = "3.1.1"

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
