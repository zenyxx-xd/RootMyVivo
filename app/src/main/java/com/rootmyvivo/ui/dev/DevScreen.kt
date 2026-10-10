package com.rootmyvivo.ui.dev

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.root.FlowEvent
import com.rootmyvivo.root.LogLevel
import com.rootmyvivo.root.Phase
import com.rootmyvivo.ui.flow.FlowScreen
import com.rootmyvivo.ui.flow.FlowUiReducer
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.RootState
import com.rootmyvivo.vm.UiState
import kotlinx.coroutines.launch

/**
 * «Другое»: инструменты разработчика. Секции-карточки — процесс (перезапуск
 * эксплойта, демо), root (зачистка следов) и каталог пейлоадов.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevScreen(vm: MainViewModel, state: UiState, onClose: () -> Unit, onRootStarted: () -> Unit) {
    var demoState by remember { mutableStateOf<UiState?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }
    // Выбранный метод демо — сохраняется (переживает выход и перезапуск)
    val demoPrefs = remember(appCtx) { appCtx.getSharedPreferences("neo_prefs", android.content.Context.MODE_PRIVATE) }
    var demoMethodDf by remember { mutableStateOf(demoPrefs.getBoolean("demoMethodDf", false)) }
    fun pickDemoMethod(df: Boolean) {
        demoMethodDf = df
        demoPrefs.edit().putBoolean("demoMethodDf", df).apply()
    }
    val demoEnabled = state.payload != null && !state.flowRunning
    val rooted = state.rootState == RootState.ROOTED
    val closeDemo = {
        demoState = null
    }
    /** Тот же редьюсер, что у боевого процесса: демо показывает ровно тот же UI */
    val demoUi = remember(appCtx) { FlowUiReducer(appCtx) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_other)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Процесс ──
            DevSection(title = stringResource(R.string.other_section_process)) {
                Button(
                    onClick = {
                        vm.startRoot()
                        onRootStarted()
                    },
                    enabled = demoEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_restart_exploit), maxLines = 1)
                }
                SectionCaption(stringResource(R.string.other_restart_desc))
                var demoExpanded by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = {
                            if (demoMethodDf) runDemoDf(demoUi, scope, appCtx) { demoState = it }
                            else runDemo(demoUi, scope, appCtx) { demoState = it }
                        },
                        // Демо всегда кликабельно: активный процесс всё равно
                        // перекрывает экран своим оверлеем
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.other_demo_with_method, if (demoMethodDf) "DirtyFrag" else "GhostLock"),
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { demoExpanded = !demoExpanded }) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown, null,
                            modifier = Modifier.rotate(if (demoExpanded) 180f else 0f),
                        )
                    }
                }
                // Радиокнопки выбора метода — две строки после демо-кнопки;
                // кликабельна вся строка, отступ между строками минимальный
                // if + animateContentSize: AnimatedVisibility при выходе не
                // участвует в layout (мгновенный коллапс — отсюда прыжок
                // подписи при закрытии); здесь высота анимируется непрерывно
                androidx.compose.foundation.layout.Column(
                    Modifier.animateContentSize(
                        animationSpec = androidx.compose.animation.core.tween(220),
                    ),
                ) {
                    if (demoExpanded) {
                    Column(
                        Modifier.padding(top = 0.dp, start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                ) { pickDemoMethod(true) },
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = demoMethodDf,
                                onClick = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.other_demo_method_df), style = MaterialTheme.typography.titleSmall)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                ) { pickDemoMethod(false) },
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = !demoMethodDf,
                                onClick = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.other_demo_method_gl), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    }
                }
                SectionCaption(stringResource(R.string.other_demo_desc))
            }

            // ── Root ──
            DevSection(title = stringResource(R.string.settings_root)) {
                OutlinedButton(
                    onClick = vm::cleanRootTraces,
                    enabled = rooted && !state.flowRunning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.CleaningServices, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_clean_traces), maxLines = 1)
                }
                SectionCaption(stringResource(R.string.settings_clean_traces_desc))
            }

            // ── Для разработчика: разрешить DirtyFrag на всех ядрах —
            // ровно тот же вид, что у тумблеров в «О приложении»
            com.rootmyvivo.ui.common.SettingsGroup(title = stringResource(R.string.settings_df_dev_subtitle)) {
                com.rootmyvivo.ui.common.SettingsRow(
                    title = stringResource(R.string.settings_df_all_kernels),
                    description = stringResource(R.string.settings_df_all_kernels_desc),
                    icon = Icons.Rounded.Rule,
                    trailing = {
                        androidx.compose.material3.Switch(
                            checked = state.settings.allowDfAllKernels,
                            onCheckedChange = { v ->
                                vm.updateSettings { it.copy(allowDfAllKernels = v) }
                            },
                        )
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Демо-флоу — тем же экраном процесса, поверх Scaffold, с переходом.
    // Снапшот демо-состояния: во время exit-анимации контент жив
    // (иначе экран мгновенно исчезает внутри анимации)
    var shownDemo by remember { mutableStateOf<UiState?>(null) }
    androidx.compose.runtime.LaunchedEffect(demoState) {
        demoState?.let { shownDemo = it }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = demoState != null,
        enter = androidx.compose.animation.slideInVertically(
            animationSpec = androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            initialOffsetY = { it },
        ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(350)),
        exit = androidx.compose.animation.slideOutVertically(
            animationSpec = androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            targetOffsetY = { it },
        ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(280)),
    ) {
        shownDemo?.let { demo ->
            FlowScreen(
                state = demo,
                canClose = true,
                onClose = closeDemo,
                onRetry = { runDemo(demoUi, scope, appCtx) { demoState = it } },
                onSoftReboot = { closeDemo() },
                onDismissSoftReboot = { demoState = demo.copy(softRebootPrompt = false) },
            )
        }
    }
}

/** Секция вкладки «Другое»: заголовок над карточкой-подложкой. */
@Composable
private fun DevSection(
    title: String?,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** Поясняющая строка внутри секции: прижата к кнопке, чуть правее её края. */
@Composable
private fun SectionCaption(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Демо-флоу успеха: копия реального процесса, живой лог пишет заглушка.
 * Состояние ведёт общий FlowUiReducer — тот же, что у боевого запуска.
 * Единственный вариант — успех.
 */
private fun runDemo(
    ui: FlowUiReducer,
    scope: kotlinx.coroutines.CoroutineScope,
    ctx: android.content.Context,
    update: (UiState) -> Unit,
) {
    scope.launch {
        var s = UiState(flowRunning = true)
        fun apply(event: FlowEvent) {
            s = ui.apply(s, event)
            update(s)
        }

        // ═══ Точная последовательность успешного ExploitEngine.run() ═══
        apply(FlowEvent.Log(ctx.getString(R.string.log_started)))
        apply(FlowEvent.Log(ctx.getString(R.string.log_payload, "iQOO Neo 11 | SM8750"), LogLevel.OK))
        kotlinx.coroutines.delay(400)

        apply(FlowEvent.Step(Phase.DOWNLOAD, 1, 4))
        apply(FlowEvent.Progress(ctx.getString(R.string.log_download_start, "preload-rmv.so", 137)))
        for (p in 1..6) {
            kotlinx.coroutines.delay(160)
            apply(FlowEvent.Download("preload-rmv.so", p.toLong(), 6L))
        }
        apply(FlowEvent.Complete(true, ctx.getString(R.string.log_download_ok, "preload-rmv.so")))

        apply(FlowEvent.Step(Phase.DEPLOY, 2, 4))
        kotlinx.coroutines.delay(500)

        // ── Эксплойт: живой лог ──
        apply(FlowEvent.Step(Phase.EXPLOIT, 3, 4))
        apply(FlowEvent.Progress(ctx.getString(R.string.log_exploit_start), exploit = true))

        val boot = listOf(
            "[+] rmv preload starting pid=20711",
            "[+] rmv exploit attempt 1/3",
            "[+] startup context pid=20712 uid=2000 euid=2000 gid=2000 attr=u:r:shell:s0 enforce=1",
            "[+] build config label=pd2520-bp2a.250605.031.a3 slide=pselect main=pselect",
            "[+] p0 profile phys_offset=0000000080000000 kernel_phys_load=00000000a8000000 delta=0000000028000000",
            "[*] slide child context route=pselect pid=27467 uid=2000 attr=u:r:shell:s0",
            "[+] slide boot_id_leaked_nfulnl_logger pid=27467 value=ffffffe81c102268",
            "[+] slide-kaslr-ok base=ffffffe81a000000 slide=000000279a000000",
            "[*] quiesce: loadavg=2.31",
        )
        val lines = boot.toMutableList()
        apply(FlowEvent.ExploitLive(attempt = null, max = null, lines = lines.toList()))
        kotlinx.coroutines.delay(900)

        // счётчик — главные попытки (как в реальном логе после фикса)
        for (step in 1..8) {
            kotlinx.coroutines.delay(170)
            lines += "[*] pselect cfi attempt=$step/24 ret=6 errno=0"
            apply(FlowEvent.ExploitLive(attempt = 1, max = 3, lines = lines.takeLast(30)))
        }

        lines += "[+] phys step pipe probe found=1 pipebuf=ffffff8881fd0000 idx=40 scan=1/1/1"
        lines += "[+] cred patched pid=20713"
        lines += "[+] posture done"
        apply(FlowEvent.ExploitLive(attempt = 1, max = 3, lines = lines.takeLast(30)))
        kotlinx.coroutines.delay(500)

        apply(FlowEvent.Complete(true))
        kotlinx.coroutines.delay(300)
        apply(FlowEvent.Log(ctx.getString(R.string.log_root_obtained, 42), LogLevel.OK))

        // ═══ finishRoot ═══
        apply(FlowEvent.Log(ctx.getString(R.string.log_verify, "uid=0(root) gid=0(root) context=u:r:kernel:s0"), LogLevel.OK))
        apply(FlowEvent.Step(Phase.KSU, 4, 4))
        apply(FlowEvent.Progress(ctx.getString(R.string.log_persist_start)))
        kotlinx.coroutines.delay(700)
        apply(FlowEvent.Complete(true, ctx.getString(R.string.log_persist_ok)))

        apply(FlowEvent.Progress(ctx.getString(R.string.log_ksud_download)))
        kotlinx.coroutines.delay(600)
        apply(FlowEvent.Complete(true))
        apply(FlowEvent.Progress(ctx.getString(R.string.log_ksu_download, "android15-6.6")))
        kotlinx.coroutines.delay(600)
        apply(FlowEvent.Progress(ctx.getString(R.string.log_ksu_vermagic)))
        kotlinx.coroutines.delay(500)
        apply(FlowEvent.Progress(ctx.getString(R.string.log_ksu_load)))
        kotlinx.coroutines.delay(700)
        apply(FlowEvent.Log(ctx.getString(R.string.log_ksu_verify)))
        kotlinx.coroutines.delay(500)

        apply(FlowEvent.Progress(ctx.getString(R.string.log_manager_download, "ReSukiSU")))
        kotlinx.coroutines.delay(600)
        apply(FlowEvent.Log(ctx.getString(R.string.log_manager_already, "ReSukiSU"), LogLevel.OK))
        kotlinx.coroutines.delay(400)
        apply(FlowEvent.Complete(true, ctx.getString(R.string.log_ksu_active, "ReSukiSU")))

        // Зачистка следов — этап в реальном finishRoot (KSU ACTIVE)
        apply(FlowEvent.Progress(ctx.getString(R.string.log_cleanup_start)))
        kotlinx.coroutines.delay(500)
        apply(FlowEvent.Complete(true, ctx.getString(R.string.log_cleanup_done)))

        apply(FlowEvent.Success(softRebootRecommended = true))
        update(s.copy(flowRunning = false, lastLog = s.log))
    }
}

/** DF-демо: точная последовательность успешного runDirtyFrag() с реальными
 *  логами натива (encryption-only, verify OK, ***SUCCESS***). */
private fun runDemoDf(
    ui: FlowUiReducer,
    scope: kotlinx.coroutines.CoroutineScope,
    ctx: android.content.Context,
    update: (UiState) -> Unit,
) {
    scope.launch {
        var s = UiState(flowRunning = true)
        fun apply(event: FlowEvent) {
            s = ui.apply(s, event)
            update(s)
        }

        apply(FlowEvent.Log(ctx.getString(R.string.log_started)))
        apply(FlowEvent.Log(ctx.getString(R.string.log_df_method), LogLevel.OK))

        // ── 1. Подготовка: SA + стейджинг ──
        apply(FlowEvent.Step(Phase.DEPLOY, 1, 3))
        kotlinx.coroutines.delay(700)
        apply(FlowEvent.Log(ctx.getString(R.string.log_df_staged), LogLevel.OK))

        // ── 2. Запуск эксплойта: реальный лог натива ──
        apply(FlowEvent.Step(Phase.EXPLOIT, 2, 3))
        apply(FlowEvent.Progress(ctx.getString(R.string.log_exploit_start), exploit = true))

        val dfLog = listOf(
            "=== setup ===",
            "found ko_target: /vendor/lib64/libbinderdebug.so",
            "encap port: 56411",
            "spi: 0x719c948b",
            "",
            "=== EXPLOIT ===",
            "* patch #1 (crash_dump64 <- splicehelper, 1488 bytes)",
            "patched 1488 bytes to /apex/com.android.runtime/bin/crash_dump64+0x0",
            "patch #1 verify OK",
            "* ko android15-6.6 (8464 bytes)",
            "* patch #2 (/vendor/lib64/libbinderdebug.so <- dirtyfrag.ko, 8464 bytes)",
            "patched 8464 bytes to /vendor/lib64/libbinderdebug.so+0x0",
            "* finding symbol offsets for /system/lib64/libc++.so",
            "PACIASP/BTI found at hook site, advancing +4",
            "* /system/lib64/libc++.so hook=0xa49fc shell=0xf6d80 len=468",
            "* patching /system/lib64/libc++.so shellcode (480 bytes)",
            "patched 480 bytes to /system/lib64/libc++.so+0xf6d80",
            "* patching /system/lib64/libc++.so trampoline at 0xa49f0",
            "patched 16 bytes to /system/lib64/libc++.so+0xa49f0",
            "",
            "=== init  ===",
            "* triggering...",
            "libc++: mutex acquired, loading custom module",
            "dfroot: launching bootstrap",
            "bootstrap: prefs loaded",
            "bootstrap: adopting zygote env",
            "bootstrap: env adopted",
            "bootstrap: setting partitions ro",
            "bootstrap: partitions set ro",
            "bootstrap: starting SU daemon",
            "***SUCCESS***",
        )
        // Порции как в живом логе: setup → патчи → триггер → бутстрап → SUCCESS
        val chunks = listOf(4, 6, 8, 7, 6)
        var shown = 0
        for (c in chunks) {
            kotlinx.coroutines.delay(600)
            shown = (shown + c).coerceAtMost(dfLog.size)
            apply(FlowEvent.ExploitLive(attempt = null, max = null, lines = dfLog.take(shown)))
        }
        kotlinx.coroutines.delay(400)

        apply(FlowEvent.Complete(true))
        kotlinx.coroutines.delay(300)
        apply(FlowEvent.Log(ctx.getString(R.string.log_df_root_iface), LogLevel.OK))
        apply(FlowEvent.Log(ctx.getString(R.string.log_root_obtained, 38), LogLevel.OK))

        // ── 3. Менеджер KernelSU (выбранный) ──
        apply(FlowEvent.Step(Phase.KSU, 3, 3))
        apply(FlowEvent.Progress(ctx.getString(R.string.log_manager_download, "KernelSU")))
        kotlinx.coroutines.delay(700)
        apply(FlowEvent.Complete(true))
        apply(FlowEvent.Progress(ctx.getString(R.string.log_ksu_load)))
        kotlinx.coroutines.delay(600)
        apply(FlowEvent.Complete(true, ctx.getString(R.string.log_ksu_active, "KernelSU")))
        apply(FlowEvent.Progress(ctx.getString(R.string.log_cleanup_start)))
        kotlinx.coroutines.delay(500)
        apply(FlowEvent.Complete(true, ctx.getString(R.string.log_cleanup_done)))

        apply(FlowEvent.Success(softRebootRecommended = true))
        update(s.copy(flowRunning = false, lastLog = s.log))
    }
}
