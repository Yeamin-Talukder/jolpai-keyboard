import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    api(project(":bangla-engine"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    // Emoji keywords for the emoji prediction tests only.
    testImplementation(project(":emoji-engine"))
}
