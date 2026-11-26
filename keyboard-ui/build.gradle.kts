import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * The built-in GIF service's key (KLIPY, https://partner.klipy.com). Never in the repository: it comes
 * from `klipy.apiKey=` in the git-ignored local.properties, or `-PklipyApiKey=`, or the
 * KLIPY_API_KEY environment variable (the release workflow's secret). Without it, GIF search
 * offers only the user's own GIPHY key.
 */
val klipyApiKey: String = providers.gradleProperty("klipyApiKey").orNull
    ?: providers.environmentVariable("KLIPY_API_KEY").orNull
    ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }.getProperty("klipy.apiKey")
    }
    ?: ""

android {
    namespace = "com.yeamin.jolpai.ui"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "KLIPY_API_KEY", "\"${klipyApiKey.trim()}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":keyboard-core"))
    implementation(project(":clipboard-manager"))
    implementation(project(":emoji-engine"))
    implementation(project(":voice-input"))
    implementation(project(":media"))
    implementation(project(":theme-engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.savedstate)
    implementation(libs.androidx.customview)
    // Offline English/Bangla translation (on-device; the Bangla pack is downloaded on request).
    implementation(libs.mlkit.translate)
    // Deeper English proofreading with Gemini Nano on phones that have it (AICore); beta.
    implementation(libs.mlkit.genai.proofreading)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
