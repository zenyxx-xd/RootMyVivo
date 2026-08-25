package com.rootmyvivo.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val label: String, val icon: ImageVector) {
    HOME("Главная", Icons.Rounded.Home),
    SETTINGS("Настройки", Icons.Rounded.Settings),
}

/**
 * Основной навигационный каркас: нижний бар + переключение экранов.
 * Экран джейлбрейка открывается поверх при нажатии ROOT.
 */
@Composable
fun NavigationShell(
    currentScreen: Screen,
    onScreenChange: (Screen) -> Unit,
    homeContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, null) },
                        label = { Text(screen.label) },
                        selected = currentScreen == screen,
                        onClick = { onScreenChange(screen) },
                    )
                }
            }
        },
        content = { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        slideInHorizontally(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                        ) { dir -> if (targetState == Screen.SETTINGS) dir / 4 else -dir / 4 } + fadeIn()
                    } with slideOutHorizontally { dir ->
                        if (currentScreen == Screen.SETTINGS) dir / 4 else -dir / 4
                    } + fadeOut(),
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        Screen.HOME -> homeContent()
                        Screen.SETTINGS -> settingsContent()
                    }
                }
            }
        }
    )
}
