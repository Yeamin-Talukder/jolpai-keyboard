import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * On-device performance tests (Macrobenchmark) and Baseline Profile generation for the app and
 * the keyboard service. Nothing here ships in the app. Run by hand with adb, never with Gradle's
 * connected tasks: those uninstall the app afterwards, which would wipe the phone's settings and
 * learned words. See benchmark/README.md.
 */
plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.yeamin.jolpai.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 29 // Baseline Profile capture needs 28+, async trace sections 29+
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The phone isn't rooted and stays on (clocks unlocked), so say so instead of failing.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY,UNLOCKED"
    }

    // Matches app-shell's build types of the same names (see app-shell/build.gradle.kts).
    buildTypes {
        create("benchmarkRelease") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
        create("nonMinifiedRelease") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app-shell"
    // The test APK drives the app from outside (it may stop and restart it), so it runs itself.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

androidComponents {
    beforeVariants(selector().all()) { it.enable = it.buildType != "debug" && it.buildType != "release" }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.test.ext.junit)
}
