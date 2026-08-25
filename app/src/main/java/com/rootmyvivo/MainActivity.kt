package com.rootmyvivo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rootmyvivo.ui.screens.*
import com.rootmyvivo.ui.theme.RootMyVivoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RootMyVivoTheme {
                RootMyVivoApp()
            }
        }
    }
}

enum class Tab(val label: String) {
    HOME("Главная"),
    SETTINGS("Настройки"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootMyVivoApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var showJailbreak by remember { mutableStateOf(false) }

    // ── Экран джейлбрейка ──
    if (showJailbreak) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Jailbreak", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = {
                            vm.stopExploit()
                            showJailbreak = false
                        }) {
                            Icon(Icons.Rounded.ArrowBack, "Назад")
                        }
                    }
                )
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                RootScreen(
                    isRunning = state.isRunning,
                    logLines = state.logLines,
                    currentStep = state.currentStep,
                    totalSteps = state.totalSteps,
                    installStage = state.installStage,
                    downloadProgress = state.downloadProgress,
                    rootStatus = state.rootStatus,
                    onSuccess = { /* stay on screen to show success */ },
                )
            }
        }
        return
    }

    // ── Обычная навигация: Главная ↔ Настройки ──
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) },
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            Crossfade(targetState = currentTab, label = "tab") { tab ->
                when (tab) {
                    Tab.HOME -> HomeScreen(state, vm,
                        onRootStarted = { showJailbreak = true })
                    Tab.SETTINGS -> SettingsScreen("ru", {}, true, {})
                }
            }
        }
    }
}
