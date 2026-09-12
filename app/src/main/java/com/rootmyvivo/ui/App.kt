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
import androidx.compose.foundation.pager.PagerDefaults
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
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
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
 * Переход новой темы: окно выезжает справа на полной непрозрачности
 * (пиксель-снап против мерцания скруглённой кромки), нижний слой уезжает
 * влево на четверть ширины и притухает; затемнение добавляет scrim из
 * NavDisplayEffects. Отображение линейно по depth → свайп «назад»
 * следует за пальцем 1:1 (предиктивный жест из коробки).
 */
private val SideTransition: NavTransition = navGraphicsTransition(opaqueDepth = 1f) { scope ->
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
    val style = LocalAppStyle.current
    val nav = rememberNavController<RmvRoute>(RmvRoute.Main)
    val onBack: () -> Unit = remember(nav) { { nav.pop(); Unit } }
    var flowOpen by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        val transition = if (style.isLegacy) NavTransitions.Modal else SideTransition
        val effects = if (style.isLegacy) {
            NavDisplayEffects(dimAmount = 0f, enableCornerClip = false)
        } else {
            NavDisplayEffects(
                enableCornerClip = true,
                cornerClipRadius = 28.dp,
                cornerClipMode = NavCornerClipMode.Leading,
                dimAmount = 0.5f,
                backdropColor = MaterialTheme.colorScheme.surfaceContainer,
                blockInputDuringTransition = false,
            )
        }

        NavDisplay(
            navController = nav,
            onBack = onBack,
            transition = transition,
            effects = effects,
        ) {
            entry<RmvRoute.Main>(swipeDismiss = NavSwipeDirection.None) {
                MainScaffold(
                    vm = vm,
                    state = state,
                    onRootStarted = { flowOpen = true },
                    onOpenSupported = { nav.push(RmvRoute.Supported) },
                    onOpenLastLog = { nav.push(RmvRoute.LogHistory) },
                    onOpenTheme = { nav.push(RmvRoute.Theme) },
                    onOpenAbout = { nav.push(RmvRoute.About) },
                    onOpenOther = { nav.push(RmvRoute.Other) },
                )
            }
            entry<RmvRoute.Theme>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                BackInterceptor(active = !style.predictiveBack, onBack = onBack)
                ThemeScreen(vm = vm, state = state, onClose = onBack)
            }
            entry<RmvRoute.About>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                BackInterceptor(active = !style.predictiveBack, onBack = onBack)
                AboutScreen(vm = vm, state = state, onClose = onBack)
            }
            entry<RmvRoute.Supported>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                BackInterceptor(active = !style.predictiveBack, onBack = onBack)
                com.rootmyvivo.ui.home.SupportedDevicesScreen(
                    state = state,
                    catalogUrl = state.settings.catalogUrl,
                    onClose = onBack,
                )
            }
            entry<RmvRoute.Other>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                BackInterceptor(active = !style.predictiveBack, onBack = onBack)
                DevScreen(
                    vm = vm,
                    state = state,
                    onClose = onBack,
                    onRootStarted = { flowOpen = true },
                )
            }
            entry<RmvRoute.LogHistory>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                BackInterceptor(active = !style.predictiveBack, onBack = onBack)
                LogHistoryScreen(
                    runs = state.logHistory,
                    onOpen = { run ->
                        vm.openLogRun(run)
                        nav.pop()
                        nav.push(RmvRoute.LogViewer)
                    },
                    onClose = onBack,
                )
            }
            entry<RmvRoute.LogViewer>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                BackInterceptor(active = !style.predictiveBack, onBack = onBack)
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

        // Процесс рута — полноэкранный оверлей поверх всей навигации
        androidx.compose.animation.AnimatedVisibility(
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

/**
 * Когда предиктивные жесты выключены — перехватываем системный «назад»
 * и закрываем окно программно, без анимации жеста (как interceptPredictiveBack
 * в ReSukiSU). Иначе(miuix-nav сам ведёт жест 1:1.
 */
@Composable
private fun BackInterceptor(active: Boolean, onBack: () -> Unit) {
    if (!active) return
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = true,
        onBackCompleted = onBack,
    )
}

/** Главная вкладка-хост: нижняя навигация + свайп-пейджер. */
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
    val style = LocalAppStyle.current
    val scope = rememberCoroutineScope()
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
