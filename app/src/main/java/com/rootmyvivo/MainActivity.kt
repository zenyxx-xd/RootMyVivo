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
            RootMyVivoTheme {
                MainApp()
            }
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
        onRootClick = { vm.startRoot() },
        onSelectKsu = { vm.toggleKsuSheet() },
    )
    
    // KSU Selector Bottom Sheet
    if (state.ksuSheetOpen) {
        ModalBottomSheet(onDismissRequest = { vm.toggleKsuSheet() }) {
            Column(Modifier.padding(20.dp)) {
                Text("Выбери KernelSU вариант", 
                    style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                
                KsuVariant.entries.forEach { variant ->
                    ListItem(
                        headlineContent = { Text(variant.displayName) },
                        supportingContent = { Text(variant.description) },
                        leadingContent = {
                            RadioButton(
                                selected = state.selectedKsu == variant,
                                onClick = { vm.selectKsu(variant) }
                            )
                        },
                        modifier = Modifier.clickable { vm.selectKsu(variant) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
