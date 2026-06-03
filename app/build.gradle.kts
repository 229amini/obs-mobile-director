plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val resolvedVersionCode = (
    providers.gradleProperty("VERSION_CODE").orNull
        ?: providers.environmentVariable("GITHUB_RUN_NUMBER").orNull
        ?: "6"
).toIntOrNull()?.coerceAtLeast(6) ?: 6

val resolvedVersionName = providers.gradleProperty("VERSION_NAME").orNull
    ?: providers.environmentVariable("VERSION_NAME").orNull
    ?: "0.1.5"

android {
    namespace = "com.mostafa229.obsmobiledirector"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mostafa229.obsmobiledirector"
        minSdk = 31
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
    }

    signingConfigs {
        create("publicTest") {
            storeFile = file("keystore/public-test-upload.jks")
            storePassword = "obsmobiletest"
            keyAlias = "obs-mobile-director-test"
            keyPassword = "obsmobiletest"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("publicTest")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("publicTest")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val cameraX = "1.4.1"
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-video:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("com.github.pedroSG94.RootEncoder:library:2.7.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
