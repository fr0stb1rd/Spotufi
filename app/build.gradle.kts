plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

import java.io.ByteArrayOutputStream

fun getGitVersionName(): String {
    return try {
        val result = providers.exec {
            commandLine("git", "describe", "--tags", "--always", "--dirty")
        }
        val tag = result.standardOutput.asText.get().trim()
        if (tag.startsWith("v")) tag.substring(1) else tag
    } catch (e: Exception) {
        "1.2.2"
    }
}

fun getGitVersionCode(): Int {
    return try {
        val result = providers.exec {
            commandLine("git", "rev-list", "--count", "HEAD")
        }
        result.standardOutput.asText.get().trim().toIntOrNull() ?: 5
    } catch (e: Exception) {
        5
    }
}

android {
    namespace = "io.github.sekademi.spotufi"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.sekademi.spotufi"
        minSdk = 26
        targetSdk = 34
        versionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: getGitVersionCode()
        versionName = project.findProperty("versionName")?.toString() ?: getGitVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (project.hasProperty("releaseStoreFile")) {
            create("release") {
                storeFile = file(project.property("releaseStoreFile").toString())
                storePassword = project.property("releaseStorePassword").toString()
                keyAlias = project.property("releaseKeyAlias").toString()
                keyPassword = project.property("releaseKeyPassword").toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (project.hasProperty("releaseStoreFile")) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
                include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.runtime:runtime-livedata")
    // Spotify metadata + YouTube streaming, ported from Meld (replaces Firebase data layer)
    implementation(project(":spotify"))
    implementation(project(":innertube"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit.ext)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation(libs.navigation.compose)

    //hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    //coroutines
    implementation(libs.coroutines.android)

    //await
    implementation(libs.coroutines.play.services)

    //coil
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    //splashScreen
    implementation(libs.splashscreen)

    //palette
    implementation(libs.palette.ktx)

    //datastore
    implementation(libs.datastore.preferences)

    //webkit (WebViewAssetLoader for secure local content loading)
    implementation(libs.webkit)

    //exoplayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    // PlayerView for the Spotify Canvas looping video on the now-playing screen.
    implementation(libs.media3.ui)
    // media session + system media notification (lock screen / notification center)
    implementation(libs.media3.session)
    // Material3 Compose widgets: PlayPauseButton, PreviousButton, NextButton, PositionAndDurationText, etc.
    implementation(libs.media3.ui.compose.material3)

    //okhttp + timber (used by the ported YouTube streaming flow)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.serialization.json)

    //jsoup — HTML parsing for artist biography
    implementation(libs.jsoup)

    //core library desugaring (required by :innertube)
    coreLibraryDesugaring(libs.desugaring)
}
