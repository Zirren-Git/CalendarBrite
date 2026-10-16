import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Release signing: keystore generated for this project (see README/report).
// Falls back to the debug signing config when no `keystore.properties` exists.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Runtime classpath for the pure-logic self tests run on the desktop JVM
// (no JUnit artifacts exist in this offline toolchain; see :app:runSelfTests).
val selfTestRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

configurations.all {
    resolutionStrategy.force(
        // KSP's worker classpath pins kotlin-stdlib 2.1.20; the offline mirror
        // carries 2.1.21 which matches the applied Kotlin plugin.
        "org.jetbrains.kotlin:kotlin-stdlib:2.1.21",
        // The offline mirror only carries javapoet 1.13.0 (AGP's databinding-compiler-common
        // pom requests 1.10.0; API-compatible for this build).
        "com.squareup:javapoet:1.13.0",
        // KSP Gradle plugin asks for an older symbol-processing-api for its worker
        // classpath; the offline mirror carries 2.1.21-2.0.1 matching KGP 2.1.21.
        "com.google.devtools.ksp:symbol-processing-api:2.1.21-2.0.1",
        "com.google.devtools.ksp:symbol-processing-common-deps:2.1.21-2.0.1",
        "com.google.devtools.ksp:symbol-processing-aa-embeddable:2.1.21-2.0.1",
        "com.google.devtools.ksp:symbol-processing:2.1.21-2.0.1",
    )
}

// ---- Offline cache substitution -------------------------------------------
// This machine resolves everything from a pre-populated Gradle module cache.
// Whenever a POM pins a module version that the cache does not carry (or
// carries only the POM without the artifact), substitute the newest cached
// version of that module. Without this, dozens of metadata-level version skew
// issues (older stdlib pins etc.) would need individual forces.
val cacheModules = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")

fun versionKey(s: String): List<Long> =
    Regex("\\d+").findAll(s).map { it.value.toLong() }.toList()

fun cachedVersions(group: String, module: String): List<String> {
    val dir = File(cacheModules, "$group/$module")
    val names = dir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: return emptyList()
    return names.sortedWith { a, b ->
        val ka = versionKey(a); val kb = versionKey(b)
        for (i in 0 until maxOf(ka.size, kb.size)) {
            val x = ka.getOrElse(i) { 0L }; val y = kb.getOrElse(i) { 0L }
            if (x != y) return@sortedWith x.compareTo(y)
        }
        a.compareTo(b)
    }
}

fun cacheHasArtifact(group: String, module: String, version: String): Boolean {
    val dir = File(cacheModules, "$group/$module/$version")
    if (!dir.isDirectory) return false
    return dir.walkTopDown().any {
        it.isFile && (it.name.endsWith(".jar") || it.name.endsWith(".aar"))
    }
}

configurations.matching { !it.name.contains("Metadata") }.all {
    resolutionStrategy.eachDependency {
        val group = requested.group
        val module = requested.name
        val version = requested.version ?: return@eachDependency
        if (!File(cacheModules, "$group/$module").isDirectory) return@eachDependency
        val available = cachedVersions(group, module)
        if (available.isEmpty()) return@eachDependency
        val ok = version in available && cacheHasArtifact(group, module, version)
        if (!ok) {
            val candidates = available.filter { v -> cacheHasArtifact(group, module, v) }
            if (candidates.isNotEmpty() && candidates.last() != version) {
                useVersion(candidates.last())
                because("offline cache substitution for $group:$module:$version")
            }
        }
    }
}

// This build box ships a jlink-trimmed JRE without the jdk.compiler module.
// The app has zero .java sources, so no real compilation ever happens through
// javac — but AGP eagerly binds a toolchain-provided JavaCompiler to its
// JavaCompile tasks, which fails capability probing at task-creation time.
// Unbinding both the value and the convention makes the tasks inert (they stay
// NO-SOURCE for the whole build).
tasks.withType<JavaCompile>().configureEach {
    javaCompiler.set(null as org.gradle.jvm.toolchain.JavaCompiler?)
    javaCompiler.convention(null as org.gradle.jvm.toolchain.JavaCompiler?)
}

android {
    namespace = "com.dayflow.planner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dayflow.planner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("dayflowRelease") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("dayflowRelease")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    lint {
        // Lint tooling artifacts are not available in this fully-offline toolchain.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("androidx.work:work-runtime-ktx:2.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    selfTestRuntime("org.jetbrains.kotlin:kotlin-stdlib:2.1.21")
}

androidComponents {
    beforeVariants { builder ->
        builder.enableAndroidTest = false
    }
}

// ---- Logic self-tests -----------------------------------------------------
// Executes `com.dayflow.planner.selftest.SelfTest` on the desktop JVM against
// the pure-Kotlin logic classes compiled for the debug variant. The logic
// classes intentionally avoid Android APIs, so android.jar is not needed at
// runtime.
tasks.register<JavaExec>("runSelfTests") {
    group = "verification"
    description = "Runs DayFlow pure-logic self tests on the JVM"
    dependsOn("compileDebugKotlin")
    classpath(
        files(layout.buildDirectory.dir("tmp/kotlin-classes/debug")),
        selfTestRuntime,
    )
    mainClass.set("com.dayflow.planner.selftest.SelfTest")
}
