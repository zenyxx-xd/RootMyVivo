package com.rootmyvivo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.ui.dev.DevScreen
import com.rootmyvivo.ui.flow.FlowScreen
import com.rootmyvivo.ui.home.HomeScreen
import com.rootmyvivo.ui.logs.LogHistoryScreen
import com.rootmyvivo.ui.settings.AboutScreen
import com.rootmyvivo.ui.settings.SettingsScreen
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import kotlin.math.roundToInt

/** Экраны навигации (сериализуемые ключи miuix-nav). */
@Serializable
private sealed interface RmvRoute : top.yukonga.miuix.kmp.nav.core.NavKey {
    @Serializable data object Main : RmvRoute
    @Serializable data object Theme : RmvRoute
    @Serializable data object About : RmvRoute
    @Serializable data object Supported : RmvRoute
    @Serializable data object Other : RmvRoute
    @Serializable data object LogHistory : RmvRoute
    @Serializable data object LogViewer : RmvRoute
}

/**
 * Переход окон: окно выезжает справа на полной непрозрачности, нижний слой
 * уезжает влево на четверть ширины и притухает; затемнение — scrim из
 * NavDisplayEffects. Отображение линейно по depth → жест «назад» следует
 * за пальцем 1:1. Быстрое во всех фазах: открытие 280мс, закрытие —
 * жест/кнопка «назад» 200мс, отмена жеста — резкая пружина.
 */
private val SideTransition: NavTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    motion = top.yukonga.miuix.kmp.nav.transition.NavMotion(
        // закрытие: жест «назад» доведён до конца / кнопка назад
        commit = top.yukonga.miuix.kmp.nav.transition.NavSettleSpec.Tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing,
        ),
        // жест «назад» отменён — окно резким щелчком возвращается
        cancel = top.yukonga.miuix.kmp.nav.transition.NavSettleSpec.Spring(
            stiffness = 1500f,
        ),
        // программные открытие/закрытие (тап по пункту)
        programmatic = top.yukonga.miuix.kmp.nav.transition.NavSettleSpec.Tween(
            durationMillis = 280,
            easing = FastOutSlowInEasing,
        ),
    ),
) { scope ->
    val width = scope.layoutSize.width.toFloat()
    val d = scope.relativeDepth
    val rtl = scope.layoutDirection == LayoutDirection.Rtl
    if (d <= 0f) {
        val p = (-d).coerceIn(0f, 1f)
        translationX = ((if (rtl) -1f else 1f) * p * width).roundToInt().toFloat()
        alpha = 1f
    } else {
        val cover = d.coerceIn(0f, 1f)
        translationX = (if (rtl) 1f else -1f) * cover * width * 0.25f
        alpha = 1f - 0.12f * cover
    }
}

@Composable
fun App(vm: MainViewModel, state: UiState) {
    val nav = rememberNavController<RmvRoute>(RmvRoute.Main)
    val onBack: () -> Unit = remember(nav) { { nav.pop(); Unit } }
    var flowOpen by rememberSaveable { mutableStateOf(false) }

    // Быстрый двойной тап по пункту дважды пушит один и тот же ключ —
    // miuix-nav на дубликатах в стеке падает. Дебаунс 350мс + запрет
    // дублей: пока экран уже в стеке, повторный push игнорируется.
    val lastPushAt = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val pushRoute: (RmvRoute) -> Unit = remember(nav) {
        { route ->
            val now = System.currentTimeMillis()
            if (now - lastPushAt.longValue > 350 && nav.backStack.none { it == route }) {
                lastPushAt.longValue = now
                nav.push(route)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavDisplay(
            navController = nav,
            onBack = onBack,
            transition = SideTransition,
            effects = NavDisplayEffects(
                enableCornerClip = true,
                cornerClipRadius = 28.dp,
                cornerClipMode = NavCornerClipMode.Leading,
                dimAmount = 0.5f,
                backdropColor = MaterialTheme.colorScheme.surfaceContainer,
                blockInputDuringTransition = false,
            ),
        ) {
            entry<RmvRoute.Main>(swipeDismiss = NavSwipeDirection.None) {
                MainScaffold(
                    vm = vm,
                    state = state,
                    onRootStarted = { flowOpen = true },
                    onOpenSupported = { pushRoute(RmvRoute.Supported) },
                    onOpenLastLog = { pushRoute(RmvRoute.LogHistory) },
                    onOpenTheme = { pushRoute(RmvRoute.Theme) },
                    onOpenAbout = { pushRoute(RmvRoute.About) },
                    onOpenOther = { pushRoute(RmvRoute.Other) },
                )
            }
            entry<RmvRoute.Theme>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                com.rootmyvivo.ui.settings.ThemeScreen(vm = vm, state = state, onClose = onBack)
            }
            entry<RmvRoute.About>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                AboutScreen(vm = vm, state = state, onClose = onBack)
            }
            entry<RmvRoute.Supported>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                com.rootmyvivo.ui.home.SupportedDevicesScreen(
                    state = state,
                    catalogUrl = state.settings.catalogUrl,
                    onClose = onBack,
                )
            }
            entry<RmvRoute.Other>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                DevScreen(
                    vm = vm,
                    state = state,
                    onClose = onBack,
                    onRootStarted = { flowOpen = true },
                )
            }
            entry<RmvRoute.LogHistory>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                LogHistoryScreen(
                    runs = state.logHistory,
                    onOpen = { run ->
                        vm.openLogRun(run)
                        nav.pop()
                        pushRoute(RmvRoute.LogViewer)
                    },
                    onClose = onBack,
                )
            }
            entry<RmvRoute.LogViewer>(swipeDismiss = NavSwipeDirection.LeftToRight) {
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
                    onClose = onBack,
                    onRetry = {},
                    onSoftReboot = {},
                    onDismissSoftReboot = {},
                    onFullReboot = {},
                    fog = false,
                )
            }
        }

    // ── Диалог обновления приложения (поверх всего) ──
    if (state.updateDialogOpen) {
        state.appUpdate?.let { update ->
            com.rootmyvivo.ui.home.UpdateDialog(
                update = update,
                download = state.updateDownload,
                onUpdate = vm::updateAction,
                onCancel = vm::dismissUpdateDialog,
            )
        }
    }

        // Процесс рута — полноэкранный оверлей поверх всей навигации
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

        // «Назад» закрывает процесс рута раньше навигации
        BackHandler(enabled = flowOpen) {
            if (!state.flowRunning) flowOpen = false
        }
    }
}

/** Главная вкладка-хост: нижняя навигация + свайп-пейджер (медленный снап). */
@Composable
private fun MainScaffold(
    vm: MainViewModel,
    state: UiState,
    onRootStarted: () -> Unit,
    onOpenLastLog: () -> Unit,
    onOpenSupported: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenOther: () -> Unit,
) {
    val scope = rememberCoroutineScope()
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
                    0 -> HomeScreen(
                        vm = vm,
                        state = state,
                        onRootStarted = onRootStarted,
                        onOpenLastLog = onOpenLastLog,
                        onOpenSupported = onOpenSupported,
                    )
                    else -> SettingsScreen(
                        vm = vm,
                        state = state,
                        onDevOpen = onOpenOther,
                        onAboutOpen = onOpenAbout,
                        onThemeOpen = onOpenTheme,
                    )
                }
            }
        }
    }
}
