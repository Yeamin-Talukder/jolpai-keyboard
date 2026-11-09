import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Trains the on-device next-word models on the PC. Not part of the app; run with
// ./gradlew :lm-trainer:run --args="prepare|train|eval ..." (see Main.kt).
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

application {
    mainClass.set("com.yeamin.jolpai.lmtrainer.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx6g", "-XX:+UseParallelGC")
}

dependencies {
    // The app's own model reader, to check exported files; Bangla normalisation.
    implementation(project(":suggestion-engine"))
    testImplementation(libs.junit)
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
