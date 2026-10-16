// DayFlow Planner — root build script.
// Versions are pinned directly (see README): this build box is fully offline and
// the version-catalog codegen would require a javac that the provisioned JDK lacks.
buildscript {
    configurations.all {
        // The offline mirror only carries javapoet 1.13.0 (newer than what AGP's
        // databinding-compiler-common pom requests; API-compatible for our build).
        resolutionStrategy.force("com.squareup:javapoet:1.13.0")
    }
}

plugins {
    id("com.android.application") version "8.9.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
    id("com.google.devtools.ksp") version "2.1.21-2.0.1" apply false
}
