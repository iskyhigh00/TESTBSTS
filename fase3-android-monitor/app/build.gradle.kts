import java.awt.image.BufferedImage
import java.awt.RenderingHints
import java.util.ArrayDeque
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


// Generate a non-adaptive launcher bitmap from the approved artwork.
// Remove only background connected to the image boundary.
val sourceIcon = launcherRes.resolve("mipmap-xxxhdpi/ic_launcher_foreground.png")
val originalIcon = javax.imageio.ImageIO.read(sourceIcon)
val w = originalIcon.width
val h = originalIcon.height
val bitmap = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
bitmap.setRGB(0, 0, w, h, originalIcon.getRGB(0, 0, w, h, null, 0, w), 0, w)
val visited = BooleanArray(w * h)
val queue = ArrayDeque<Int>()
fun enqueue(x: Int, y: Int) {
    if (x !in 0 until w || y !in 0 until h) return
    val i = y * w + x
    if (visited[i]) return
    visited[i] = true
    val c = bitmap.getRGB(x, y)
    val r = (c ushr 16) and 255
    val g = (c ushr 8) and 255
    val b = c and 255
    if ((c ushr 24) < 16 || (minOf(r,g,b) > 190 && maxOf(r,g,b)-minOf(r,g,b) < 30)) {
        bitmap.setRGB(x,y,0)
        queue.add(i)
    }
}
for (x in 0 until w) { enqueue(x,0); enqueue(x,h-1) }
for (y in 0 until h) { enqueue(0,y); enqueue(w-1,y) }
while (queue.isNotEmpty()) {
    val i = queue.removeFirst()
    val x = i % w
    val y = i / w
    enqueue(x-1,y); enqueue(x+1,y); enqueue(x,y-1); enqueue(x,y+1)
}
var left=w; var top=h; var right=-1; var bottom=-1
for (y in 0 until h) for (x in 0 until w) {
    if ((bitmap.getRGB(x,y) ushr 24) > 32) {
        left=minOf(left,x); top=minOf(top,y); right=maxOf(right,x); bottom=maxOf(bottom,y)
    }
}
require(right >= left && bottom >= top) { "Empty launcher artwork" }
val cropped = bitmap.getSubimage(left,top,right-left+1,bottom-top+1)
val output = BufferedImage(192,192,BufferedImage.TYPE_INT_ARGB)
val scale = 184.0 / maxOf(cropped.width,cropped.height)
val ow = (cropped.width*scale).toInt()
val oh = (cropped.height*scale).toInt()
val graphics = output.createGraphics()
graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC)
graphics.drawImage(cropped,(192-ow)/2,(192-oh)/2,ow,oh,null)
graphics.dispose()
val iconTarget = launcherRes.resolve("drawable-nodpi/billmon_photo.png")
iconTarget.parentFile.mkdirs()
javax.imageio.ImageIO.write(output,"png",iconTarget)
val adaptive = BufferedImage(324,324,BufferedImage.TYPE_INT_ARGB)
val ag = adaptive.createGraphics()
ag.drawImage(output,66,66,null)
ag.dispose()
javax.imageio.ImageIO.write(adaptive,"png",launcherRes.resolve("drawable-nodpi/billmon_adaptive_foreground.png"))
require((output.getRGB(0,0) ushr 24) == 0) { "Launcher background must be transparent" }


android {
    namespace = "cl.reabill.monitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "cl.reabill.monitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 261
        versionName = "3.1.4"

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
