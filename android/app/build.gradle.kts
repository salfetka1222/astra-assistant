import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.astra.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.astra.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
    }

    val signingPath = System.getenv("ASTRA_SIGNING_KEYSTORE_PATH")
    val signingPassword = System.getenv("ASTRA_SIGNING_STORE_PASSWORD")
    val signingAlias = System.getenv("ASTRA_SIGNING_KEY_ALIAS")
    val signingKeyPassword = System.getenv("ASTRA_SIGNING_KEY_PASSWORD")
    val releaseSigningConfigured = listOf(signingPath, signingPassword, signingAlias, signingKeyPassword)
        .all { !it.isNullOrBlank() }

    signingConfigs {
        create("astraRelease") {
            if (releaseSigningConfigured) {
                storeFile = File(signingPath!!)
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("astraRelease")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
