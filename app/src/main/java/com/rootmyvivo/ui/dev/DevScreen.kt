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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.root.LogLevel
import com.rootmyvivo.root.Phase
import com.rootmyvivo.ui.flow.FlowScreen
import com.rootmyvivo.vm.LogEntry
import com.rootmyvivo.vm.LogKind
import com.rootmyvivo.vm.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Режим демо-флоу: что показывает заглушка вместо эксплойта. */
enum class DemoVariant { SUCCESS, FAILURE, SYSTEM_BROKEN, INFINITE }

/**
 * Экран разработчика: тест UI процесса рута без устройства —
 * демо-флоу с заглушкой эксплойта, просмотр пейлоадов и лога последнего запуска.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevScreen(state: UiState, onClose: () -> Unit) {
    var mode by remember { mutableStateOf<String?>(null) }
    var demoState by remember { mutableStateOf<UiState?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    demoContext = ctx.applicationContext

    val closeDemo = {
        demoState = null
        mode = null
    }

    // Демо-флоу рендерится тем же экраном процесса — с настоящим туманом,
    // морфингом иконок и аккордеоном живого лога
    demoState?.let { demo ->
        FlowScreen(
            state = demo,
            canClose = true,
            onClose = closeDemo,
            onRetry = { runDemo(DemoVariant.SUCCESS, scope) { demoState = it } },
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
                title = { Text(stringResource(R.string.dev_title)) },
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

            Text(
                stringResource(R.string.dev_demo_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // Варианты демо-флоу
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        mode = "success"
                        runDemo(DemoVariant.SUCCESS, scope) { demoState = it }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.dev_demo_success)) }
                OutlinedButton(
                    onClick = {
                        mode = "failure"
                        runDemo(DemoVariant.FAILURE, scope) { demoState = it }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.dev_demo_failure)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        mode = "broken"
                        runDemo(DemoVariant.SYSTEM_BROKEN, scope) { demoState = it }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.dev_demo_broken)) }
                OutlinedButton(
                    onClick = {
                        mode = "infinite"
                        runDemo(DemoVariant.INFINITE, scope) { demoState = it }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.dev_demo_infinite)) }
            }

            // Пейлоады: скачанные файлы + выбранный в каталоге
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Folder, null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(R.string.dev_payloads),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    val dir = File(ctx.filesDir, "payloads")
                    val files = remember { dir.listFiles()?.sortedBy { it.name } ?: emptyList() }
                    if (files.isEmpty()) {
                        Text(
                            stringResource(R.string.dev_payloads_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        files.forEach { f ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(f.name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                Text(
                                    "%.1f КБ".format(f.length() / 1024.0),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    state.payload?.let { p ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.dev_payload_selected, p.displayName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Лог последнего реального запуска
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Description, null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(R.string.dev_lastlog),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (state.lastLog.isEmpty()) {
                        Text(
                            stringResource(R.string.dev_lastlog_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                mode = "lastlog"
                                demoState = UiState(
                                    log = state.lastLog,
                                    flowRunning = false,
                                    flowResult = com.rootmyvivo.vm.FlowResult.Success,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.dev_lastlog_open)) }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * Демо-флоу: точная копия настоящего процесса — те же события, те же строки,
 * тот же редьюсер (Progress закрывает предыдущие шаги, Complete мутирует
 * спиннер в галку, ExploitLive кормит аккордеон). Единственное отличие —
 * живой лог эксплойта пишет заглушка.
 */
private fun runDemo(
    variant: DemoVariant,
    scope: kotlinx.coroutines.CoroutineScope,
    update: (UiState) -> Unit,
) {
    scope.launch {
        val appCtx = demoContext ?: return@launch
        var id = 0L
        var idCounter = 0L
        var s = UiState(flowRunning = true)

        // ── редьюсер: 1-в-1 как в MainViewModel.applyFlowEvent ──
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
                    // как в MainViewModel: накапливаем с устойчивыми id
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

        fun msg(res: Int, level: LogLevel = LogLevel.INFO, vararg args: Any?) =
            com.rootmyvivo.root.FlowEvent.Log(appCtx.getString(res, *args), level)

        // ═══ Точная последовательность ExploitEngine.run() ═══
        apply(msg(R.string.log_started))

        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.CATALOG, 1, 6))
        delay(600)

        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.PAYLOAD, 2, 6))
        apply(msg(R.string.log_payload, LogLevel.OK, "GhostLock PD2520-A16"))
        delay(400)

        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.DOWNLOAD, 3, 6))
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_download_start, "preload-rmv.so", 137)))
        for (p in 1..6) {
            delay(160)
            apply(com.rootmyvivo.root.FlowEvent.Download("preload-rmv.so", p.toLong(), 6L))
        }
        apply(com.rootmyvivo.root.FlowEvent.Complete(true, appCtx.getString(R.string.log_download_ok, "preload-rmv.so")))

        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.DEPLOY, 4, 6))
        delay(500)

        // ── Эксплойт: заглушка с настоящим живым логом ──
        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.EXPLOIT, 5, 6))
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_exploit_start), exploit = true))

        val boot = listOf(
            "[+] rmv preload starting pid=20711",
            "[+] rmv exploit attempt 1/3",
            "[+] startup context pid=20712 uid=2000 euid=2000 gid=2000 attr=u:r:shell:s0 enforce=1",
            "[+] build config label=pd2520-bp2a.250605.031.A3 slide=pselect main=pselect",
            "[+] p0 profile phys_offset=0000000080000000 kernel_phys_load=00000000a8000000 delta=0000000028000000",
            "[*] slide child context route=pselect pid=27467 uid=2000 attr=u:r:shell:s0",
            "[+] slide boot_id_leaked_nfulnl_logger pid=27467 value=ffffffe81c102268",
            "[+] slide-kaslr-ok base=ffffffe81a000000 slide=000000279a000000",
            "[*] quiesce: loadavg=2.31",
        )
        val lines = boot.toMutableList()
        apply(
            com.rootmyvivo.root.FlowEvent.ExploitLive(attempt = null, max = null, lines = lines.toList()),
        )
        delay(900)

        for (step in 1..24) {
            if (variant == DemoVariant.INFINITE && step > 24) break
            delay(if (variant == DemoVariant.INFINITE) 500 else 170)
            lines += when {
                step % 3 == 0 -> "[*] pselect returned attempt=$step/24 ret=6 errno=0 success=1 delay=50000"
                step % 3 == 1 -> "[*] pselect cfi miss attempt=$step/24 step=4 errno=0; refreshing FOPS page"
                else -> "[*] cfi write ret=35 errno=0 (attempt=$step/24)"
            }
            apply(
                com.rootmyvivo.root.FlowEvent.ExploitLive(attempt = step, max = 24, lines = lines.takeLast(30)),
            )
        }
        if (variant == DemoVariant.INFINITE) return@launch

        lines += "[+] phys step pipe probe found=1 pipebuf=ffffff8881fd0000 idx=40 scan=1/1/1"
        lines += "[+] cred patched pid=20713"
        lines += "[+] posture done"
        apply(com.rootmyvivo.root.FlowEvent.ExploitLive(attempt = 24, max = 24, lines = lines.takeLast(30)))
        delay(500)

        if (variant == DemoVariant.FAILURE) {
            apply(com.rootmyvivo.root.FlowEvent.Complete(false))
            apply(com.rootmyvivo.root.FlowEvent.Failure(com.rootmyvivo.root.FlowEvent.Reason.EXPLOIT))
            return@launch
        }
        if (variant == DemoVariant.SYSTEM_BROKEN) {
            apply(com.rootmyvivo.root.FlowEvent.Failure(com.rootmyvivo.root.FlowEvent.Reason.SYSTEM_BROKEN))
            return@launch
        }

        apply(com.rootmyvivo.root.FlowEvent.Complete(true))
        delay(300)
        apply(msg(R.string.log_root_obtained, LogLevel.OK, 42))

        // ═══ finishRoot ═══
        apply(msg(R.string.log_verify, LogLevel.OK, "uid=0(root) gid=0(root) context=u:r:kernel:s0"))
        apply(com.rootmyvivo.root.FlowEvent.Step(Phase.KSU, 6, 6))
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_persist_start)))
        delay(700)
        apply(com.rootmyvivo.root.FlowEvent.Complete(true, appCtx.getString(R.string.log_persist_ok)))

        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_ksud_download)))
        delay(600)
        apply(com.rootmyvivo.root.FlowEvent.Complete(true))
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_ksu_download, "android15-6.6")))
        delay(600)
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_ksu_vermagic)))
        delay(500)
        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_ksu_load)))
        delay(700)
        apply(msg(R.string.log_ksu_verify))
        delay(500)

        apply(com.rootmyvivo.root.FlowEvent.Progress(appCtx.getString(R.string.log_manager_download, "ReSukiSU")))
        delay(600)
        apply(msg(R.string.log_manager_already, LogLevel.OK, "ReSukiSU"))
        delay(400)
        apply(com.rootmyvivo.root.FlowEvent.Complete(true, appCtx.getString(R.string.log_ksu_active, "ReSukiSU")))

        apply(com.rootmyvivo.root.FlowEvent.Success(softRebootRecommended = true))
    }
}

/** Контекст приложения для строк в демо-корутине. */
private var demoContext: android.content.Context? = null
