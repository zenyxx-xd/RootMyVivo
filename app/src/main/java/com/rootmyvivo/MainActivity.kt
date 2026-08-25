package com.rootmyvivo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rootmyvivo.ui.screens.MainScreen
import com.rootmyvivo.ui.theme.RootMyVivoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RootMyVivoTheme { MainApp() }
        }
    }
}

@Composable
fun MainApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    
    MainScreen(
        deviceInfo = state.deviceInfo,
        supportCheck = state.supportCheck,
        isRunning = state.isRunning,
        logLines = state.logLines,
        rootStatus = state.rootStatus,
        selectedKsu = state.selectedKsu.displayName,
        ksuSheetOpen = state.ksuSheetOpen,
        onRootClick = { vm.startRoot() },
        onSelectKsu = { vm.selectKsu(it) },
        onDismissKsu = { vm.toggleKsuSheet() },
    )
}
