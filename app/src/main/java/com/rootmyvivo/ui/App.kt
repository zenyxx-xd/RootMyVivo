package com.rootmyvivo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.ui.flow.FlowScreen
import com.rootmyvivo.ui.dev.DevScreen
import com.rootmyvivo.ui.home.HomeScreen
import com.rootmyvivo.ui.logs.LogHistoryScreen
import com.rootmyvivo.ui.settings.SettingsScreen
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState
import kotlinx.coroutines.launch

@Composable
fun App(vm: MainViewModel, state: UiState) {
    var flowOpen by rememberSaveable { mutableStateOf(false) }
    var devOpen by rememberSaveable { mutableStateOf(false) }
    var logHistoryOpen by rememberSaveable { mutableStateOf(false) }
    var logViewerOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Системное «назад» на экране процесса: закрывает его, но не во время выполнения
    BackHandler(enabled = flowOpen) {
        if (!state.flowRunning) flowOpen = false
    }

    // Системное «назад» на экране разработчика
    BackHandler(enabled = devOpen && !flowOpen) {
        devOpen = false
    }

    // Системное «назад» в истории запусков
    BackHandler(enabled = logHistoryOpen && !flowOpen && !devOpen) {
        logHistoryOpen = false
    }

    // Системное «назад» в просмотре лога
    BackHandler(enabled = logViewerOpen && !flowOpen && !devOpen && !logHistoryOpen) {
        logViewerOpen = false
    }

    // ── Полноэкранный процесс рута (slide up) ──
    AnimatedVisibility(
        visible = flowOpen,
        enter = slideInVertically(
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            initialOffsetY = { it },
        ) + fadeIn(tween(350)),
        exit = slideOutVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ) + fadeOut(),
    ) {
        FlowScreen(
            state = state,
            canClose = !state.flowRunning,
            onClose = { flowOpen = false },
            onRetry = { vm.startRoot() },
            onSoftReboot = { vm.performSoftReboot() },
            onDismissSoftReboot = { vm.dismissSoftReboot() },
            onFullReboot = { vm.performFullReboot() },
        )
    }

    AnimatedVisibility(visible = !flowOpen, enter = fadeIn(), exit = fadeOut()) {
        // Свайп-переключение вкладок как в ReSukiSU (HorizontalPager)
        val pagerState = rememberPagerState(initialPage = 0) { 2 }

        Scaffold(
            bottomBar = {
                NavigationBar(tonalElevation = 3.dp) {
                    NavigationBarItem(
                        selected = pagerState.targetPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        icon = { Icon(Icons.Rounded.Home, null) },
                        label = { Text(stringResource(R.string.tab_home)) },
                    )
                    NavigationBarItem(
                        selected = pagerState.targetPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        icon = { Icon(Icons.Rounded.Settings, null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(vm, state, onRootStarted = { flowOpen = true }, onOpenLastLog = { logHistoryOpen = true })
                        else -> SettingsScreen(vm, state, onDevOpen = { devOpen = true })
                    }
                }
            }
        }
    }

    // ── Экран разработчика (демо-флоу, пейлоады, логи) ──
    AnimatedVisibility(
        visible = devOpen && !flowOpen,
        enter = slideInVertically(
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            initialOffsetY = { it },
        ) + fadeIn(tween(350)),
        exit = slideOutVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ) + fadeOut(),
    ) {
        DevScreen(
            vm = vm,
            state = state,
            onClose = { devOpen = false },
            onRootStarted = {
                devOpen = false
                flowOpen = true
            },
        )
    }

    // ── История запусков: карточки последних логов ──
    AnimatedVisibility(
        visible = logHistoryOpen && !flowOpen && !devOpen,
        enter = slideInVertically(
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            initialOffsetY = { it },
        ) + fadeIn(tween(350)),
        exit = slideOutVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ) + fadeOut(),
    ) {
        LogHistoryScreen(
            runs = state.logHistory,
            onOpen = { run ->
                vm.openLogRun(run)
                // экран истории закрываем сразу — иначе просмотрщик
                // показывается только после выхода из истории
                logHistoryOpen = false
                logViewerOpen = true
            },
            onClose = { logHistoryOpen = false },
        )
    }

    // ── Просмотр лога из истории (без тумана) ──
    AnimatedVisibility(
        visible = logViewerOpen && !flowOpen && !devOpen && !logHistoryOpen,
        enter = slideInVertically(
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            initialOffsetY = { it },
        ) + fadeIn(tween(350)),
        exit = slideOutVertically(
            animationSpec = tween(280, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ) + fadeOut(),
    ) {
        FlowScreen(
            state = state.copy(
                log = state.lastLog,
                flowRunning = false,
                flowResult = null,
                downloadProgress = null,
                softRebootPrompt = false,
                exploitLive = com.rootmyvivo.vm.ExploitLiveState(),
            ),
            canClose = true,
            onClose = { logViewerOpen = false },
            onRetry = {},
            onSoftReboot = {},
            onDismissSoftReboot = {},
            onFullReboot = {},
            fog = false,
        )
    }
}
