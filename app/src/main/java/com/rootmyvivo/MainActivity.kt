package com.rootmyvivo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rootmyvivo.core.Tab
import com.rootmyvivo.ui.screens.*
import com.rootmyvivo.ui.theme.RootMyVivoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.state.collectAsState()
            RootMyVivoTheme(
                themeMode = state.settings.themeMode,
                dynamicColor = state.settings.dynamicColors,
            ) {
                RootMyVivoApp(vm, state)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootMyVivoApp(vm: MainViewModel, state: UiState) {
    var currentTab by remember { mutableStateOf(Tab.HOME) }
    var showJailbreak by remember { mutableStateOf(false) }

    // ── Экран джейлбрейка (slide up) ──
    AnimatedVisibility(
        visible = showJailbreak,
        enter = slideInVertically(
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow),
            initialOffsetY = { it },
        ) + fadeIn(),
        exit = slideOutVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ) + fadeOut(),
    ) {
        JailbreakScreen(state) {
            vm.stopExploit()
            showJailbreak = false
        }
    }

    // ── Основной экран ──
    AnimatedVisibility(
        visible = !showJailbreak,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(tonalElevation = 3.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            icon = {
                                Icon(tab.icon, null,
                                    modifier = Modifier.size(if (currentTab == tab) 26.dp else 24.dp))
                            },
                            label = { Text(tab.label) },
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInHorizontally(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow),
                            initialOffsetX = { if (forward) it / 4 else -it / 4 },
                        ) + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(
                                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                                    targetOffsetX = { if (forward) -it / 6 else it / 6 },
                                ) + fadeOut(tween(180)))
                    },
                    label = "tab",
                ) { tab ->
                    when (tab) {
                        Tab.HOME -> HomeScreen(state, vm,
                            onRootStarted = { showJailbreak = true })
                        Tab.SETTINGS -> SettingsScreen(state.settings, vm)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JailbreakScreen(state: UiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jailbreak", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            RootScreen(
                isRunning = state.isRunning,
                logLines = state.logLines,
                currentStep = state.currentStep,
                totalSteps = state.totalSteps,
                installStage = state.installStage,
                downloadProgress = state.downloadProgress,
                rootStatus = state.rootStatus,
                onSuccess = {},
            )
        }
    }
}
