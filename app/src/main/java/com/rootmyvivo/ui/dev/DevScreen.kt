package com.rootmyvivo.ui.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.data.Catalog
import com.rootmyvivo.ui.common.SettingsGroup
import com.rootmyvivo.ui.flow.FlowScreen
import com.rootmyvivo.vm.LogEntry
import com.rootmyvivo.vm.LogKind
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState
import kotlinx.coroutines.launch

/**
 * «Другое»: перезапуск эксплойта, демонстрация процесса и редактор
 * каталога пейлоадов.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevScreen(vm: MainViewModel, state: UiState, onClose: () -> Unit, onRootStarted: () -> Unit) {
    var demoState by remember { mutableStateOf<UiState?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    demoContext = ctx.applicationContext

    val closeDemo = {
        demoState = null
    }

    // Демо-флоу рендерится тем же экраном процесса
    demoState?.let { demo ->
        FlowScreen(
            state = demo,
            canClose = true,
            onClose = closeDemo,
            onRetry = { runDemo(scope) { demoState = it } },
            onSoftReboot = { closeDemo() },
            onDismissSoftReboot = { demoState = demo.copy(softRebootPrompt = false) },
            onFullReboot = { closeDemo() },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Перезапуск эксплойта (залитая, сверху) ──
            Button(
                onClick = {
                    vm.startRoot()
                    onRootStarted()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                enabled = state.payload != null && !state.flowRunning,
            ) {
                Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_restart_exploit))
            }

            // ── Демонстрация эксплоита (контурная, без подложки) ──
            OutlinedButton(
                onClick = { runDemo(scope) { demoState = it } },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.other_demo_run))
            }

            // ── Каталог пейлоадов ──
            SettingsGroup(title = stringResource(R.string.settings_catalog)) {
                var url by remember(state.settings.catalogUrl) { mutableStateOf(state.settings.catalogUrl) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                                    Icon(Icons.Rounded.Restore, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.setCatalogUrl(url) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_save))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Демо-флоу успеха: копия реального процесса, живой лог пишет заглушка.
 * Единственный вариант — успех.
 */
private fun runDemo(
    scope: kotlinx.coroutines.CoroutineScope,
    update: (UiState) -> Unit,
) {
    scope.launch {
        val appCtx = demoContext ?: return@launch
        var id = 0L
        var idCounter = 0L
        var s = UiState(flowRunning = true)

        suspend fun apply(event: com.rootmyvivo.root.FlowEvent) {
            s = when (event) {
                is com.rootmyvivo.root.FlowEvent.Step -> s.copy(
                    flowPhase = event.phase,
                    stepIndex = event.index,
                    stepTotal = event.total,
                    downloadProgress = null,
                )
                is com.rootmyvivo.root.FlowEvent.Log ->
                    s.copy(log = s.log + LogEntry(++id, event.line, event.level))
                is com.rootmyvivo.root.FlowEvent.Progress -> {
                    val closed = s.log.map { if (it.status == LogLevel.RUNNING) it.copy(status = LogLevel.OK) else it }
                    val kind = if (event.exploit) LogKind.EXPLOIT else LogKind.NORMAL
                    s.copy(log = closed + LogEntry(++id, event.text, LogLevel.RUNNING, kind))
                }
                is com.rootmyvivo.root.FlowEvent.ProgressUpdate -> {
                    val idx = s.log.indexOfLast { it.status == LogLevel.RUNNING }
                    if (idx >= 0) s.copy(log = s.log.toMutableList().apply { set(idx, s.log[idx].copy(text = event.text)) }) else s
                }
                is com.rootmyvivo.root.FlowEvent.Complete -> {
                    val idx = s.log.indexOfLast { it.status == LogLevel.RUNNING }
                    if (idx >= 0) {
                        val e = s.log[idx]
                        val upd = e.copy(status = if (event.ok) LogLevel.OK else LogLevel.ERROR, text = event.text ?: e.text)
                        s.copy(log = s.log.toMutableList().apply { set(idx, upd) })
                    } else if (event.text != null) {
                        s.copy(log = s.log + LogEntry(++id, event.text, if (event.ok) LogLevel.OK else LogLevel.ERROR))
                    } else {
                        s
                    }
                }
                is com.rootmyvivo.root.FlowEvent.Download ->
                    s.copy(downloadProgress = if (event.total > 0) event.read.toFloat() / event.total else null)
                is com.rootmyvivo.root.FlowEvent.ExploitLive -> {
                    val idx = s.log.indexOfLast { it.status == LogLevel.RUNNING && it.kind == LogKind.EXPLOIT }
                    val text = if (event.max != null && event.attempt != null) {
                        appCtx.getString(R.string.log_exploit_counter, event.attempt, event.max)
                    } else {
                        appCtx.getString(R.string.log_exploit_start)
                    }
                    val prev = s.exploitLive.lines
                    val incoming = event.lines
                    fun knownPrefix(k: Int): Boolean {
                        for (i in 0 until k) {
                            if (incoming[i] != prev[prev.size - k + i].text) return false
                        }
                        return true
                    }
                    var k = minOf(incoming.size, prev.size)
                    while (k > 0 && !knownPrefix(k)) {
                        k--
                    }
                    val acc = (prev + incoming.drop(k).map { com.rootmyvivo.vm.LiveLogLine(++idCounter, it) }).takeLast(500)
                    s.copy(
                        log = if (idx >= 0) s.log.toMutableList().apply { set(idx, s.log[idx].copy(text = text)) } else s.log,
                        exploitLive = com.rootmyvivo.vm.ExploitLiveState(event.attempt, event.max, acc),
                    )
                }
                is com.rootmyvivo.root.FlowEvent.Success -> s.copy(
                    flowRunning = false,
                    flowResult = com.rootmyvivo.vm.FlowResult.Success,
                    rootState = com.rootmyvivo.vm.RootState.ROOTED,
                    downloadProgress = null,
                    softRebootPrompt = event.softRebootRecommended,
                    lastLog = s.log,
                )
                com.rootmyvivo.root.FlowEvent.NeedsSoftReboot -> s.copy(
                    flowRunning = false,
                    flowResult = com.rootmyvivo.vm.FlowResult.Success,
                    rootState = com.rootmyvivo.vm.RootState.ROOTED,
                    downloadProgress = null,
                    softRebootPrompt = true,
                    lastLog = s.log,
                )
                is com.rootmyvivo.root.FlowEvent.Failure -> s.copy(
                    flowRunning = false,
                    flowResult = com.rootmyvivo.vm.FlowResult.Failure(event.reason),
                    rootState = com.rootmyvivo.vm.RootState.FAILED,
                    downloadProgress = null,
                    lastLog = s.log,
                )
            }
            update(s)
        }

        // ═══ Точная последовательность успешного ExploitEngine.run() ═══
        apply(com.rootmyvivo.root.FlowEvent.Log(appCtx.getString(R.string.log_started)))

        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.CATALOG, 1, 6))
        kotlinx.coroutines.delay(600)

        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.PAYLOAD, 2, 6))
        apply(com.rootmyvivo.root.FlowEvent.Log(appCtx.getString(R.string.log_payload, "iQOO Neo 11 | SM8750"), LogLevel.OK))
        kotlinx.coroutines.delay(400)

        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.DOWNLOAD, 3, 6))
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_download_start, "preload-rmv.so", 137)))
        for (p in 1..6) {
            kotlinx.coroutines.delay(160)
            apply(com.rootmyvivo.root.FlowEvent.Download("preload-rmv.so", p.toLong(), 6L))
        }
        apply(com.rootmyvivo.root.FlowEvent.Complete(true, appCtx.getString(R.string.log_download_ok, "preload-rmv.so")))

        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.DEPLOY, 4, 6))
        kotlinx.coroutines.delay(500)

        // ── Эксплойт: живой лог ──
        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.EXPLOIT, 5, 6))
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_exploit_start), exploit = true))

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
        apply(com.rootmyvivo.root.FlowEvent.ExploitLive(attempt = null, max = null, lines = lines.toList()))
        kotlinx.coroutines.delay(900)

        // счётчик — главные попытки (как в реальном логе после фикса)
        for (step in 1..8) {
            kotlinx.coroutines.delay(170)
            lines += "[*] pselect cfi attempt=$step/24 ret=6 errno=0"
            apply(
                com.rootmyvivo.root.FlowEvent.ExploitLive(attempt = 1, max = 3, lines = lines.takeLast(30)),
            )
        }

        lines += "[+] phys step pipe probe found=1 pipebuf=ffffff8881fd0000 idx=40 scan=1/1/1"
        lines += "[+] cred patched pid=20713"
        lines += "[+] posture done"
        apply(com.rootmyvivo.root.FlowEvent.ExploitLive(attempt = 1, max = 3, lines = lines.takeLast(30)))
        kotlinx.coroutines.delay(500)

        apply(com.rootmyvivo.root.FlowEvent.Complete(true))
        kotlinx.coroutines.delay(300)
        apply(com.rootmyvivo.root.FlowEvent.Log(appCtx.getString(R.string.log_root_obtained, 42), LogLevel.OK))

        // ═══ finishRoot ═══
        apply(com.rootmyvivo.root.FlowEvent.Log(appCtx.getString(R.string.log_verify, "uid=0(root) gid=0(root) context=u:r:kernel:s0"), LogLevel.OK))
        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.KSU, 6, 6))
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_persist_start)))
        kotlinx.coroutines.delay(700)
        apply(com.rootmyvivo.root.FlowEvent.Complete(true, appCtx.getString(R.string.log_persist_ok)))

        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_ksud_download)))
        kotlinx.coroutines.delay(600)
        apply(com.rootmyvivo.root.FlowEvent.Complete(true))
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_ksu_download, "android15-6.6")))
        kotlinx.coroutines.delay(600)
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_ksu_vermagic)))
        kotlinx.coroutines.delay(500)
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_ksu_load)))
        kotlinx.coroutines.delay(700)
        apply(com.rootmyvivo.root.FlowEvent.Log(appCtx.getString(R.string.log_ksu_verify)))
        kotlinx.coroutines.delay(500)

        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_manager_download, "ReSukiSU")))
        kotlinx.coroutines.delay(600)
        apply(com.rootmyvivo.root.FlowEvent.Log(appCtx.getString(R.string.log_manager_already, "ReSukiSU"), LogLevel.OK))
        kotlinx.coroutines.delay(400)
        apply(com.rootmyvivo.root.FlowEvent.Complete(true, appCtx.getString(R.string.log_ksu_active, "ReSukiSU")))

        apply(com.rootmyvivo.root.FlowEvent.Success(softRebootRecommended = true))
    }
}

/** Контекст приложения для строк в демо-корутине. */
private var demoContext: android.content.Context? = null

private typealias Phase = com.rootmyvivo.root.Phase
private typealias LogLevel = com.rootmyvivo.root.LogLevel
