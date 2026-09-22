package com.rootmyvivo.ui.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.data.Catalog
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
    val demoEnabled = state.payload != null && !state.flowRunning
    val rooted = state.rootState == RootState.ROOTED
    val closeDemo = {
        demoState = null
    }
    /** Тот же редьюсер, что у боевого процесса: демо показывает ровно тот же UI */
    val demoUi = remember(appCtx) { FlowUiReducer(appCtx) }

    // Демо-флоу рендерится тем же экраном процесса
    demoState?.let { demo ->
        FlowScreen(
            state = demo,
            canClose = true,
            onClose = closeDemo,
            onRetry = { runDemo(demoUi, scope, appCtx) { demoState = it } },
            onSoftReboot = { closeDemo() },
            onDismissSoftReboot = { demoState = demo.copy(softRebootPrompt = false) },
        )
        return
    }

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
                OutlinedButton(
                    onClick = { runDemo(demoUi, scope, appCtx) { demoState = it } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.other_demo_run), maxLines = 1)
                }
            }

            // ── Root ──
            DevSection(title = stringResource(R.string.settings_root)) {
                SectionCaption(
                    stringResource(R.string.settings_clean_traces_desc),
                )
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
            }

            // ── Каталог ──
            DevSection(title = stringResource(R.string.settings_catalog)) {
                var url by remember(state.settings.catalogUrl) { mutableStateOf(state.settings.catalogUrl) }
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.bodySmall,
                    trailingIcon = {
                        if (url != Catalog.DEFAULT_URL) {
                            IconButton(onClick = {
                                vm.setCatalogUrl(Catalog.DEFAULT_URL)
                            }) {
                                Icon(
                                    Icons.Rounded.Restore, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    },
                )
                Button(
                    onClick = { vm.setCatalogUrl(url) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_save), maxLines = 1)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Секция вкладки «Другое»: заголовок над карточкой-подложкой. */
@Composable
private fun DevSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/** Поясняющая строка внутри секции. */
@Composable
private fun SectionCaption(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        apply(FlowEvent.Step(Phase.CATALOG, 1, 6))
        kotlinx.coroutines.delay(600)

        apply(FlowEvent.Step(Phase.PAYLOAD, 2, 6))
        apply(FlowEvent.Log(ctx.getString(R.string.log_payload, "iQOO Neo 11 | SM8750"), LogLevel.OK))
        kotlinx.coroutines.delay(400)

        apply(FlowEvent.Step(Phase.DOWNLOAD, 3, 6))
        apply(FlowEvent.Progress(ctx.getString(R.string.log_download_start, "preload-rmv.so", 137)))
        for (p in 1..6) {
            kotlinx.coroutines.delay(160)
            apply(FlowEvent.Download("preload-rmv.so", p.toLong(), 6L))
        }
        apply(FlowEvent.Complete(true, ctx.getString(R.string.log_download_ok, "preload-rmv.so")))

        apply(FlowEvent.Step(Phase.DEPLOY, 4, 6))
        kotlinx.coroutines.delay(500)

        // ── Эксплойт: живой лог ──
        apply(FlowEvent.Step(Phase.EXPLOIT, 5, 6))
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
        apply(FlowEvent.Step(Phase.KSU, 6, 6))
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

        apply(FlowEvent.Success(softRebootRecommended = true))
        update(s.copy(flowRunning = false, lastLog = s.log))
    }
}
