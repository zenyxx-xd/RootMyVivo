package com.rootmyvivo

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rootmyvivo.ui.App
import com.rootmyvivo.ui.theme.NeoTheme
import com.rootmyvivo.vm.MainViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Уведомления о root-статусе после перезагрузки (Android 13+)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val vm = ViewModelProvider(this)[MainViewModel::class.java]

        // Диагностика adb-канала: am start … --ez adb_selftest true
        if (intent?.getBooleanExtra("adb_selftest", false) == true) {
            vm.adbSelfTest()
        }

        // Возврат в приложение (например, после запуска Shizuku) — обновить транспорт
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshTransport()
        })

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()

            // Локаль приложения = выбранный язык; "" = системный
            val localizedContext = remember(state.settings.language) {
                val locale = when (state.settings.language) {
                    "en", "ru", "zh" -> Locale(state.settings.language)
                    else -> Locale.getDefault()
                }
                val config = android.content.res.Configuration(resources.configuration)
                config.setLocale(locale)
                createConfigurationContext(config)
            }

            CompositionLocalProvider(LocalContext provides localizedContext) {
                NeoTheme(
                    themeMode = state.settings.themeMode,
                    dynamicColors = state.settings.dynamicColors,
                ) {
                    App(vm, state)
                }
            }
        }
    }
}
