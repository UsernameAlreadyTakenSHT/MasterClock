import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

// Release signing credentials live in local.properties (gitignored, never committed) so the
// keystore path/passwords never end up in source control. See F3 in AUDIT.md.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}
val releaseStoreFile = localProperties.getProperty("MASTERCLOCK_RELEASE_STORE_FILE")
val releaseStorePassword = localProperties.getProperty("MASTERCLOCK_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = localProperties.getProperty("MASTERCLOCK_RELEASE_KEY_ALIAS")
val releaseKeyPassword = localProperties.getProperty("MASTERCLOCK_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig = !releaseStoreFile.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.masterclock.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.usernamealreadytakensht.masterclock.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "0.8.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("debugOptimized") {
            initWith(getByName("debug"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            matchingFallbacks += listOf("debug")
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("complete") {
            dimension = "version"
            applicationIdSuffix = ".complete"
            versionNameSuffix = "-complete"
        }
        create("standard") {
            dimension = "version"
            applicationIdSuffix = ".standard"
            versionNameSuffix = "-standard"
        }
        create("light") {
            dimension = "version"
            applicationIdSuffix = ".light"
            versionNameSuffix = "-light"
        }
        create("extraLight") {
            dimension = "version"
            applicationIdSuffix = ".extra_light"
            versionNameSuffix = "-extralight"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    // UI & Compose Dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.material)
    
    // UI specialized utilities
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.android.embedded)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.register("buildAllReleaseFlavors") {
    group = "build"
    description = "Builds all release APKs for all flavors (+ paper) and prints their sizes"

    val flavors = listOf("complete", "standard", "light", "extraLight")
    val buildTasks = flavors.map { "assemble${it.replaceFirstChar { c -> c.uppercase() }}Release" }
    dependsOn(buildTasks)
    dependsOn(":paper:assembleRelease")

    doLast {
        println("\n==========================================")
        println("       BUILD ALL FLAVORS SUMMARY")
        println("==========================================")
        println(String.format("%-15s | %-15s", "Flavor", "Size (KB)"))
        println("------------------------------------------")

        flavors.forEach { flavor ->
            val apkDir = layout.buildDirectory.dir("outputs/apk/$flavor/release").get().asFile
            val apk = apkDir.listFiles()?.find { it.name.endsWith(".apk") }
            val size = if (apk != null) String.format("%.2f", apk.length() / 1024.0) else "N/A"
            println(String.format("%-15s | %-15s", flavor, size))
        }

        val paperApkDir = project(":paper").layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val paperApk = paperApkDir.listFiles()?.find { it.name.endsWith(".apk") }
        val paperSize = if (paperApk != null) String.format("%.2f", paperApk.length() / 1024.0) else "N/A"
        println(String.format("%-15s | %-15s", "paper", paperSize))
        println("==========================================")
    }
}

tasks.register("buildAllReleaseBundles") {
    group = "build"
    description = "Builds all release AABs (Android App Bundles) for all flavors (+ paper) and prints their sizes"

    val flavors = listOf("complete", "standard", "light", "extraLight")
    val buildTasks = flavors.map { "bundle${it.replaceFirstChar { c -> c.uppercase() }}Release" }
    dependsOn(buildTasks)
    dependsOn(":paper:bundleRelease")

    doLast {
        println("\n==========================================")
        println("       BUILD ALL BUNDLES SUMMARY")
        println("==========================================")
        println(String.format("%-15s | %-15s", "Flavor", "Size (KB)"))
        println("------------------------------------------")

        flavors.forEach { flavor ->
            val aabDir = layout.buildDirectory.dir("outputs/bundle/${flavor}Release").get().asFile
            val aab = aabDir.listFiles()?.find { it.name.endsWith(".aab") }
            val size = if (aab != null) String.format("%.2f", aab.length() / 1024.0) else "N/A"
            println(String.format("%-15s | %-15s", flavor, size))
        }

        val paperAabDir = project(":paper").layout.buildDirectory.dir("outputs/bundle/release").get().asFile
        val paperAab = paperAabDir.listFiles()?.find { it.name.endsWith(".aab") }
        val paperSize = if (paperAab != null) String.format("%.2f", paperAab.length() / 1024.0) else "N/A"
        println(String.format("%-15s | %-15s", "paper", paperSize))
        println("==========================================")
    }
}
