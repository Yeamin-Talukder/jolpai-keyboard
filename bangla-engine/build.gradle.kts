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
    // Only the JsonElement tree parser is used, so the serialization compiler plugin isn't needed.
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
