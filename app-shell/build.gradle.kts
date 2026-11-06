import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Release signing: create `keystore.properties` in the project root (it is git-ignored) with
 * storeFile=…, storePassword=…, keyAlias=…, keyPassword=… . Without it, release builds are unsigned.
 */
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) } }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.yeamin.lipi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yeamin.lipi"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.7.0"
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Day to day, release builds are signed with this PC's debug key so they update the
            // test phone's install in place and keep its data. Builds for publishing use the
            // store key only when asked: ./gradlew :app-shell:assembleRelease -PstoreKey
            // (needs keystore.properties). Android refuses an update signed with a different
            // key, so never install a store-signed APK over a debug-signed one without a backup.
            val useStoreKey = keystoreFile.exists() && project.hasProperty("storeKey")
            signingConfig = if (useStoreKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }

    // One APK per processor type: offline translation's native library is ~16 MB per type, so a
    // single APK for all four was 74 MB. Phones (arm64) install app-shell-arm64-v8a-release.apk.
    // A Play App Bundle splits by processor on its own.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = false
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":keyboard-core"))
    implementation(project(":keyboard-ui"))
    implementation(project(":core-data"))
    implementation(project(":clipboard-manager"))
    implementation(project(":voice-input"))
    implementation(project(":media"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
