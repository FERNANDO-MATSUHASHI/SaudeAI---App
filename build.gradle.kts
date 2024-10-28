// build.gradle.kts (Project-level)

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Atualize a versão do Kotlin para 1.9.0
        classpath(libs.kotlin.gradle.plugin.v190) // Kotlin versão 1.9.0
        classpath(libs.gradle.v811) // Verifique se esta é a versão correta do Gradle
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
}

// Task 'clean' usando Kotlin DSL
tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
