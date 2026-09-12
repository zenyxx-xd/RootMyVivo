package com.rootmyvivo.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.ui.dev.DevScreen
import com.rootmyvivo.ui.flow.FlowScreen
import com.rootmyvivo.ui.home.HomeScreen
import com.rootmyvivo.ui.logs.LogHistoryScreen
import com.rootmyvivo.ui.settings.AboutScreen
import com.rootmyvivo.ui.settings.SettingsScreen
import com.rootmyvivo.ui.settings.ThemeScreen
import com.rootmyvivo.ui.theme.LocalAppStyle
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun App(vm: MainViewModel, state: UiState) {
    var flowOpen by rememberSaveable { mutableStateOf(false) }
    var devOpen by rememberSaveable { mutableStateOf(false) }
    var logHistoryOpen by rememberSaveable { mutableStateOf(false) }
    var logViewerOpen by rememberSaveable { mutableStateOf(false) }
    var supportedOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var themeOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val style = LocalAppStyle.current

    // Системное «назад» на экране процесса: закрывает его, но не во время выполнения
    BackHandler(enabled = flowOpen) {
        if (!state.flowRunning) flowOpen = false
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
        // Свайп-переключение вкладок; в новой теме снап чуть медленнее
        val pagerState = rememberPagerState(initialPage = 0) { 2 }
        val fling = if (style.slowPager) {
            PagerDefaults.flingBehavior(
                pagerState,
                snapAnimationSpec = tween(550, easing = FastOutSlowInEasing),
            )
        } else {
            PagerDefaults.flingBehavior(pagerState)
        }

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
                    flingBehavior = fling,
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            vm = vm,
                            state = state,
                            onRootStarted = { flowOpen = true },
                            onOpenLastLog = { logHistoryOpen = true },
                            onOpenSupported = { supportedOpen = true },
                        )
                        else -> SettingsScreen(
                            vm = vm,
                            state = state,
                            onDevOpen = { devOpen = true },
                            onAboutOpen = { aboutOpen = true },
                            onThemeOpen = { themeOpen = true },
                        )
                    }
                }
            }
        }
    }

    // ── Оверлеи-«окна». В новой теме выезжают сбоку и поддерживают
    // предиктивный жест назад (окно сжимается вслед за пальцем) ──

    OverlayWindow(
        visible = themeOpen && !flowOpen,
        onDismiss = { themeOpen = false },
    ) {
        ThemeScreen(vm = vm, state = state, onClose = { themeOpen = false })
    }

    OverlayWindow(
        visible = aboutOpen && !flowOpen && !themeOpen,
        onDismiss = { aboutOpen = false },
    ) {
        AboutScreen(vm = vm, state = state, onClose = { aboutOpen = false })
    }

    OverlayWindow(
        visible = supportedOpen && !flowOpen && !themeOpen && !aboutOpen,
        onDismiss = { supportedOpen = false },
    ) {
        com.rootmyvivo.ui.home.SupportedDevicesScreen(
            state = state,
            catalogUrl = state.settings.catalogUrl,
            onClose = { supportedOpen = false },
        )
    }

    OverlayWindow(
        visible = devOpen && !flowOpen && !themeOpen && !aboutOpen && !supportedOpen,
        onDismiss = { devOpen = false },
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

    OverlayWindow(
        visible = logHistoryOpen && !flowOpen && !devOpen && !themeOpen && !aboutOpen && !supportedOpen,
        onDismiss = { logHistoryOpen = false },
    ) {
        LogHistoryScreen(
            runs = state.logHistory,
            onOpen = { run ->
                vm.openLogRun(run)
                logHistoryOpen = false
                logViewerOpen = true
            },
            onClose = { logHistoryOpen = false },
        )
    }

    OverlayWindow(
        visible = logViewerOpen && !flowOpen && !devOpen && !logHistoryOpen &&
            !themeOpen && !aboutOpen && !supportedOpen,
        onDismiss = { logViewerOpen = false },
    ) {
        FlowScreen(
            state = state.copy(
                log = state.lastLog,
                flowRunning = false,
                flowResult = null,
                downloadProgress = null,
                softRebootPrompt = false,
                exploitLive = com.rootmyvivo.vm.ExploitLiveState(
                    lines = state.lastExploitLog.mapIndexed { i, t ->
                        com.rootmyvivo.vm.LiveLogLine(i.toLong(), t)
                    },
                ),
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

/**
 * Оверлей-окно: в старой теме выезжает снизу, в новой — сбоку (справа)
 * с предиктивным жестом назад: окно сжимается и уезжает вслед за пальцем.
 */
@Composable
private fun OverlayWindow(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val style = LocalAppStyle.current
    val predictive = remember { mutableFloatStateOf(0f) }

    // предиктивный назад — только в новой теме и при включённой настройке
    if (style.predictiveBack && !style.isLegacy) {
        PredictiveBackHandler(enabled = visible) { progress ->
            try {
                progress.collect { event ->
                    predictive.floatValue = event.progress
                }
                // жест доведён до конца — закрываем
                predictive.floatValue = 0f
                onDismiss()
            } catch (e: CancellationException) {
                // жест отменён — окно возвращается на место
                predictive.floatValue = 0f
                throw e
            }
        }
    } else {
        BackHandler(enabled = visible, onBack = onDismiss)
    }

    LaunchedEffect(visible) {
        if (!visible) predictive.floatValue = 0f
    }

    val p = predictive.floatValue

    if (style.sideOverlays) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(
                animationSpec = tween(380, easing = FastOutSlowInEasing),
                initialOffsetX = { it },
            ) + fadeIn(tween(380)),
            exit = slideOutHorizontally(
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                targetOffsetX = { it },
            ) + fadeOut(tween(300)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // предиктивный жест: сжатие + сдвиг вправо + затухание
                        val s = 1f - p * 0.10f
                        scaleX = s
                        scaleY = s
                        translationX = p * size.width * 0.3f
                        alpha = 1f - (p * 0.4f).coerceIn(0f, 1f)
                    },
            ) {
                content()
            }
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                animationSpec = tween(350, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ) + fadeIn(tween(350)),
            exit = slideOutVertically(
                animationSpec = tween(280, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ) + fadeOut(),
        ) {
            content()
        }
    }
}
