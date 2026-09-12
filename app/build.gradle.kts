plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rootmyvivo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rootmyvivo"
        minSdk = 31
        targetSdk = 36
        // Схема MMmmpp: 01-мажор 00-минор 10-патчи
        versionCode = 10015
        versionName = "1.0.15"
    }

    signingConfigs {
        create("release") {
            storeFile = file("C:/neo11/rmv-release.jks")
            storePassword = "rmv-beta-2026"
            keyAlias = "rmv"
            keyPassword = "rmv-beta-2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(bom)
    implementation("androidx.compose.material3:material3:1.5.0-alpha28")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
