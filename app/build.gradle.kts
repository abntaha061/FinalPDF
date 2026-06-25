import java.net.URL
import java.net.HttpURLConnection
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.finalpdf.rpxkml"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation("androidx.core:core-splashscreen:1.0.1")
  implementation("androidx.compose.ui:ui-text-google-fonts:1.6.3")
  implementation("com.google.accompanist:accompanist-pager:0.32.0")
  implementation("com.google.accompanist:accompanist-pager-indicators:0.32.0")
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.coil.compose)
  implementation("com.github.mhiew:android-pdf-viewer:3.2.0-beta.1")
  implementation("androidx.webkit:webkit:1.12.0")
  implementation("org.apache.poi:poi-ooxml:5.2.5")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation("androidx.documentfile:documentfile:1.0.1")
  implementation("org.burnoutcrew.composereorderable:reorderable:0.9.6")
  implementation("com.tom-roush:pdfbox-android:2.0.27.0")
  implementation("com.google.mlkit:text-recognition:16.0.0")
  // implementation("com.google.mlkit:text-recognition-arabic:16.0.0-beta1")
  implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.appcompat:appcompat:1.6.1")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.hilt.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

val buildDir = layout.buildDirectory.get().asFile
val projDir = layout.projectDirectory.asFile

tasks.register("downloadPdfJs") {
    val destFile = File(buildDir, "pdfjs.zip")
    val destDir = File(projDir, "src/main/assets/pdfjs")

    doLast {
        val checkFile1 = File(destDir, "web/viewer.html")
        val checkFile2 = File(destDir, "build/pdf.js")
        if (checkFile1.exists() && checkFile2.exists()) {
            println("PDF.js already exists at $destDir. Skipping download and extraction.")
            return@doLast
        }
        if (!destDir.exists()) {
            destDir.mkdirs()
        }
        val url = URL("https://github.com/mozilla/pdf.js/releases/download/v3.11.174/pdfjs-3.11.174-legacy-dist.zip")
        println("Downloading PDF.js from $url...")
        
        var connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        var status = connection.responseCode
        var redirectUrl = url
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
            status == HttpURLConnection.HTTP_MOVED_PERM ||
            status == 302 || status == 301 || status == 307 || status == 308) {
            val newUrl = connection.getHeaderField("Location")
            redirectUrl = URL(newUrl)
            connection = redirectUrl.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
        }
        
        connection.inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        println("Extracting PDF.js zip...")
        ZipInputStream(BufferedInputStream(FileInputStream(destFile))).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        destFile.delete()
        println("PDF.js successfully downloaded and extracted to assets!")
    }
}

tasks.named("preBuild") {
    dependsOn("downloadPdfJs")
}

