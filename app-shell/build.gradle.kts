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
    namespace = "com.yeamin.jolpai"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yeamin.jolpai"
        minSdk = 26
        targetSdk = 37
        versionCode = 15
        versionName = "0.15.0"
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
        // For the on-device performance tests in :benchmark (never published). Same code, shrinking
        // and signing as release, so the numbers are what users get and the APK installs over the
        // phone's app without losing its data; profileable lets Perfetto read its trace sections.
        create("benchmarkRelease") {
            initWith(getByName("release"))
            isProfileable = true
            matchingFallbacks += "release"
        }
        // Same as release without R8, so the generated Baseline Profile keeps real class names
        // (R8 rewrites the profile for the shrunk release build).
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            isMinifyEnabled = false
            isShrinkResources = false
            isProfileable = true
            matchingFallbacks += "release"
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
    testImplementation(libs.junit)
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
    // Installs the bundled Baseline Profile (src/main/baselineProfiles) on first launch when the
    // APK didn't come from Play, so hot code is compiled ahead of time for sideloads too.
    implementation(libs.androidx.profileinstaller)
}
