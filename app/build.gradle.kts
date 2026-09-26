import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Секреты подписи вне репозитория: keystore.properties в корне проекта
// (gitignore). Без него релиз собирается неподписанным — CI это легально.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

// Токен отправки отчётов валидации (fine-grained PAT, только Actions:write
// на RootMyVivo-Payloads). Тоже вне репозитория; без него приложение
// просто не шлёт отчёты (RootReport.enabled = false).
val reportProps = Properties().apply {
    val f = rootProject.file("report_token.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

android {
    namespace = "com.rootmyvivo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.rootmyvivo"
        minSdk = 31
        targetSdk = 36
        // Схема MMmmpp: 01-мажор 00-минор 10-патчи
        versionCode = 10100
        versionName = "1.1.0"
        buildConfigField(
            "String", "RMV_DISPATCH_TOKEN",
            "\"${reportProps.getProperty("dispatchToken", "")}\"",
        )
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: без него dex с Compose + icons-extended + miuix раздувает
            // APK до 46 МБ неиспользуемого кода — с ним ~в 5 раз меньше
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
    // Навигация в духе новых Android (NavDisplay + предиктивный назад)
    implementation("top.yukonga.miuix.kmp:miuix-nav:0.9.4-rc01")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
