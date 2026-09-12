package com.rootmyvivo.vm

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rootmyvivo.R
import com.rootmyvivo.data.AppUpdate
import com.rootmyvivo.data.AppUpdater
import com.rootmyvivo.data.Catalog
import com.rootmyvivo.data.DeviceInfo
import com.rootmyvivo.data.Prefs
import com.rootmyvivo.data.Settings
import com.rootmyvivo.data.checkSupport
import com.rootmyvivo.root.ExploitEngine
import com.rootmyvivo.root.FlowEvent
import com.rootmyvivo.root.LogLevel
import com.rootmyvivo.root.KsuVariant
import com.rootmyvivo.root.Phase
import com.rootmyvivo.shell.Transport
import com.rootmyvivo.shell.TransportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val prefs = Prefs(app)
    private val catalog = Catalog()
    private var engine: ExploitEngine? = null
    private var updateCheckJob: kotlinx.coroutines.Job? = null

    init {
        Transport.prefs = prefs
        Catalog.initCache(app.filesDir)
        _state.value = _state.value.copy(
            settings = prefs.settings(),
            selectedKsu = KsuVariant.byId("resukisu"),
        )
        catalog.url = _state.value.settings.catalogUrl
        _state.value = _state.value.copy(
            selectedKsu = KsuVariant.byId(prefs.selectedKsu),
            needsSoftReboot = prefs.softRebootPendingActual(),
            logHistory = loadLogHistory(),
        )
        // Транспорт обновляется при запуске/смерти Shizuku
        Transport.onBinderStateChanged = { refreshTransport() }
        detectDevice()
        refreshTransport()
        // Автопоиск обновлений при каждом запуске приложения
        if (_state.value.settings.autoUpdateCheck) checkForUpdate()
    }

    // ─────────── Устройство и каталог ───────────

    fun detectDevice() {
        viewModelScope.launch {
            _state.value = _state.value.copy(rootState = RootState.CHECKING)

            val device = withContext(Dispatchers.IO) { DeviceInfo.detect() }
            val check = withContext(Dispatchers.IO) {
                val appCtx = getApplication<Application>()
                checkSupport(device) {
                    // Локально: демон эксплойта (до загрузки KSU).
                    // После KSU рут доступен только shell-домену (allow_shell) —
                    // проверяем ещё и через транспорт, иначе после софт-ребута
                    // живой рут выглядит пропавшим
                    val local = try {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "true")).waitFor() == 0
                    } catch (_: Exception) {
                        false
                    }
                    val viaTransport = try {
                        kotlinx.coroutines.runBlocking { Transport.su(appCtx, "true").first == 0 }
                    } catch (_: Exception) {
                        false
                    }
                    local || viaTransport
                }
            }
            if (check.rootAlreadyActive) prefs.firstRootDone = true

            // Устройство показываем сразу — каталог не должен блокировать UI
            _state.value = _state.value.copy(
                device = device,
                rootState = if (check.rootAlreadyActive) RootState.ROOTED else RootState.NOT_ROOTED,
            )

            // Каталог в фоне
            catalog.url = _state.value.settings.catalogUrl
            val result = catalog.fetch()
            val cat = result.getOrNull()
            _state.value = _state.value.copy(
                payload = cat?.let { catalog.findPayload(it, device) },
                catalogState = if (cat != null) CatalogState.READY else CatalogState.ERROR,
            )
        }
    }

    // ─────────── Транспорт ───────────

    fun refreshTransport() {
        viewModelScope.launch {
            val t = withContext(Dispatchers.IO) { Transport.detectBlocking(getApplication()) }
            _state.value = _state.value.copy(transport = t)
            if (t == TransportState.Shizuku) {
                withContext(Dispatchers.IO) { Transport.bindShizukuService(getApplication()) }
            }
        }
    }

    fun requestShizukuPermission() {
        val ok = Transport.requestShizukuPermission {
            refreshTransport()
        }
        if (!ok) refreshTransport()
    }

    fun openShizukuApp() {
        val ctx = getApplication<Application>()
        try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
            } else {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "openShizukuApp failed", e)
        }
    }

    // ─────────── Процесс рута ───────────

    fun startRoot() {
        val device = _state.value.device ?: return
        val ctx = getApplication<Application>()
        engine = ExploitEngine(ctx, device, catalog)
        runStartedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(
            flowRunning = true,
            log = emptyList(),
            exploitLive = ExploitLiveState(),
            flowResult = null,
            downloadProgress = null,
            softRebootPrompt = false,
            // rootState не трогаем: без «проверки состояния» между кнопкой и экраном взлома
        )
        // Foreground-сервис: процесс рута продолжается и в фоне
        com.rootmyvivo.ExploitService.start(ctx, ctx.getString(R.string.notif_root_running))

        viewModelScope.launch {
            val variant = _state.value.selectedKsu
            // Эксплойт уже работает (приложение вылетело/перезапущено)?
            // Подхватываем его живой лог вместо запуска нового
            val alreadyRunning = withContext(Dispatchers.IO) { engine!!.detectRunning() }
            val ok = if (alreadyRunning) {
                engine!!.runAttached(variant) { event -> applyFlowEvent(event) }
            } else {
                engine!!.run(variant) { event -> applyFlowEvent(event) }
            }
            _state.value = _state.value.copy(flowRunning = false)
            com.rootmyvivo.ExploitService.stop(ctx)
            if (ok) {
                prefs.firstRootDone = true
                refreshTransport()
            }
            // Автопоиск обновлений после каждого запуска — если включён
            if (_state.value.settings.autoUpdateCheck) checkForUpdate()
        }
    }

    fun stopRoot() {
        engine?.stop()
        _state.value = _state.value.copy(flowRunning = false)
        com.rootmyvivo.ExploitService.stop(getApplication())
    }

    // ─────────── Обновление приложения ───────────

    /**
     * Проверить обновления в фоне (GitHub releases, стабильные только).
     * Результат — диалог + компактная плашка на главной. Повторные вызовы
     * схлопываются. manual=true — по кнопке: без обновлений показывает тост.
     */
    fun checkForUpdate(manual: Boolean = false) {
        if (updateCheckJob?.isActive == true) return
        updateCheckJob = viewModelScope.launch {
            val update = withContext(Dispatchers.IO) { AppUpdater.check(getApplication()) }
            // не перетираем активное скачивание предыдущей проверки
            if (_state.value.updateDownload == null) {
                _state.value = _state.value.copy(
                    appUpdate = update,
                    updateDialogOpen = update != null,
                )
                if (manual && update == null) {
                    val ctx = getApplication<Application>()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            ctx,
                            ctx.getString(R.string.update_none_found),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Универсальное действие кнопки обновления: до скачивания — скачать и
     * установить, после — переоткрыть системный установщик.
     */
    fun updateAction() {
        if (_state.value.updateDownload?.done == true) {
            retryInstallDownloaded()
        } else {
            downloadAndInstallUpdate()
        }
    }

    /** Закрыть диалог — компактная плашка остаётся на главной */
    fun dismissUpdateDialog() {
        _state.value = _state.value.copy(updateDialogOpen = false)
    }

    fun dismissUpdate() {
        _state.value = _state.value.copy(appUpdate = null)
    }

    /** Скачать APK обновления с живым прогрессом и открыть установщик. */
    fun downloadAndInstallUpdate() {
        val update = _state.value.appUpdate ?: return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _state.value = _state.value.copy(
                updateDownload = UpdateDownloadState(update.versionName, 0, update.apkSize),
            )
            try {
                val apk = AppUpdater.download(update, ctx) { read, total ->
                    _state.value = _state.value.copy(
                        updateDownload = UpdateDownloadState(update.versionName, read, total),
                    )
                }
                _state.value = _state.value.copy(
                    updateDownload = UpdateDownloadState(update.versionName, apk.length(), apk.length()),
                    updateInstalling = true,
                )
                val started = AppUpdater.install(apk, ctx)
                if (!started) {
                    _state.value = _state.value.copy(updateInstalling = false)
                } else {
                    // установщик открыт — закрываем диалог, карточка остаётся
                    // с кнопкой «Установить» на случай возврата назад
                    _state.value = _state.value.copy(updateDialogOpen = false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "update download failed: ${e.message}")
                _state.value = _state.value.copy(
                    updateDownload = null,
                    appUpdate = update, // плашку возвращаем — можно повторить
                )
            }
        }
    }

    /** Повторно открыть установщик уже скачанного APK (после возврата). */
    fun retryInstallDownloaded() {
        val dl = _state.value.updateDownload ?: return
        if (!dl.done) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            // ищем скачанный файл по маске — имя содержит versionName
            val dir = ctx.externalCacheDir ?: ctx.cacheDir
            val f = dir.listFiles()
                ?.filter { it.name.startsWith("rmv-update-") && it.name.endsWith(".apk") }
                ?.maxByOrNull { it.lastModified() }
            if (f != null && f.exists()) AppUpdater.install(f, ctx)
        }
    }

    /** Soft reboot — только по подтверждению пользователя. */
    fun performSoftReboot() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            // Система сломана → soft reboot зависнет на глазах; сразу полная перезагрузка
            val healthy = withContext(Dispatchers.IO) { Transport.systemHealthy(ctx) }
            if (!healthy) {
                addLog(ctx.getString(R.string.log_softreboot_broken))
                withContext(Dispatchers.IO) { Transport.su(ctx, "reboot") }
                return@launch
            }
            addLog(ctx.getString(R.string.log_softreboot_perform))
            // SIGKILL, как в оригинальном эксплойте: мгновенная смерть system_server —
            // зигота сразу перезапускает интерфейс. SIGTERM виснет на минуты
            var (code, out) = withContext(Dispatchers.IO) {
                Transport.su(ctx, "kill -9 \$(pidof system_server)")
            }
            if (code != 0) {
                Log.w("NeoVM", "soft reboot via kill failed: ${out.take(100)}")
                code = withContext(Dispatchers.IO) { Transport.su(ctx, "stop").first }
                if (code == 0) code = withContext(Dispatchers.IO) { Transport.su(ctx, "start").first }
            }
            if (code != 0) {
                addLog(ctx.getString(R.string.log_softreboot_fail))
                _state.value = _state.value.copy(softRebootPrompt = false)
            } else {
                // Интерфейс перезапускается — ядро чистое, статус сбрасываем насовсем
                prefs.softRebootPending = false
                _state.value = _state.value.copy(
                    softRebootPrompt = false,
                    needsSoftReboot = false,
                )
            }
        }
    }

    /** Полная перезагрузка устройства (кнопка на карточке сломанной системы). */
    fun performFullReboot() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            addLog(ctx.getString(R.string.log_full_reboot))
            withContext(Dispatchers.IO) { Transport.su(ctx, "reboot") }
        }
    }

    fun dismissSoftReboot() {
        _state.value = _state.value.copy(softRebootPrompt = false)
    }

    /** Показать/скрыть просмотр лога из истории */
    fun toggleLogViewer() {
        _state.value = _state.value.copy(logViewerOpen = !_state.value.logViewerOpen)
    }

    /** Открыть лог конкретного запуска из истории. */
    fun openLogRun(info: LogRunInfo) {
        val entries = try {
            info.file.readLines().drop(1).mapIndexedNotNull { i, line ->
                val idx = line.indexOf('\t')
                if (idx <= 0) {
                    null
                } else {
                    val st = runCatching { LogLevel.valueOf(line.substring(0, idx)) }.getOrDefault(LogLevel.INFO)
                    LogEntry(i.toLong(), line.substring(idx + 1), st)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        val exploit = try {
            info.exploitFile?.takeIf { it.exists() }
                ?.readLines()
                ?.filter { it.isNotBlank() }
                ?.takeLast(300)
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        _state.value = _state.value.copy(
            lastLog = entries,
            lastExploitLog = exploit,
            logViewerOpen = true,
        )
    }

    /** История запусков на диск: до 5 последних, с метаданными. Переживает ребут. */
    private fun saveLogHistory(success: Boolean, failReason: String?) {
        try {
            val ctx = getApplication<Application>()
            val dir = java.io.File(ctx.filesDir, "logs").apply { mkdirs() }
            val start = runStartedAt
            val dur = (System.currentTimeMillis() - start) / 1000
            val body = _state.value.log.joinToString("\n") { entry ->
                entry.status.name + "\t" + entry.text.replace('\n', ' ')
            }
            // Метастрока + лог; имя файла = время старта запуска
            val f = java.io.File(dir, "$start.log")
            f.writeText(
                "META\t$start\t$dur\t${if (success) "ok" else "fail"}\t" +
                    "${failReason ?: ""}\t${_state.value.selectedKsu.displayName}\t${_state.value.log.size}\n" +
                    body,
            )
            // Живой лог эксплойта (live.log: попытки, CFI-этапы, phys step,
            // cred) — отдельным файлом, рядом; нужен для разбора сбоев
            val live = _state.value.exploitLive.lines
            if (live.isNotEmpty()) {
                java.io.File(dir, "$start.exploit.log").writeText(
                    live.joinToString("\n") { it.text },
                )
            }
            // старее 5 запусков — вычищаем (и live-логи тоже)
            dir.listFiles { x -> x.name.endsWith(".log") }
                ?.sortedByDescending { it.name }
                ?.groupBy { it.name.substringBefore('.') }
                ?.let { groups ->
                    groups.entries.sortedByDescending { it.key.toLongOrNull() ?: 0L }
                        .drop(5)
                        .forEach { (_, files) -> files.forEach { it.delete() } }
                }
            _state.value = _state.value.copy(logHistory = loadLogHistory())
        } catch (_: Exception) {
        }
    }

    private fun loadLogHistory(): List<LogRunInfo> = try {
        val dir = java.io.File(getApplication<Application>().filesDir, "logs")
        dir.listFiles { x -> x.name.endsWith(".log") && !x.name.endsWith(".exploit.log") }
            ?.sortedByDescending { it.name }
            ?.mapNotNull { f ->
                val meta = f.useLines { it.firstOrNull() } ?: return@mapNotNull null
                val p = meta.split('\t')
                if (p.size < 7 || p[0] != "META") return@mapNotNull null
                val exploit = java.io.File(f.parentFile, f.nameWithoutExtension + ".exploit.log")
                LogRunInfo(
                    startedAt = p[1].toLongOrNull() ?: return@mapNotNull null,
                    durationSec = p[2].toLongOrNull() ?: 0,
                    success = p[3] == "ok",
                    failReason = p[4].ifEmpty { null },
                    variant = p[5],
                    lines = p[6].toIntOrNull() ?: 0,
                    file = f,
                    exploitFile = exploit.takeIf { it.exists() },
                )
            } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    /** Время старта текущего/последнего запуска — для метаданных истории. */
    private var runStartedAt = System.currentTimeMillis()

    private var logId = 0L
    private var liveId = 0L

    /** Финальная строка успешного процесса — «Готово». */
    private fun appendDoneLine() {
        _state.value = _state.value.copy(
            log = _state.value.log + LogEntry(
                ++logId,
                getApplication<Application>().getString(R.string.log_done),
                LogLevel.OK,
            ),
        )
    }

    /** Root получен: запоминаем, что soft reboot ещё не выполнялся (до перезагрузки устройства). */
    private fun markSoftRebootPending() {
        prefs.softRebootPending = true
        prefs.rootBootId = prefs.currentBootId()
        _state.value = _state.value.copy(needsSoftReboot = true)
    }

    private fun applyFlowEvent(event: FlowEvent) {
        when (event) {
            is FlowEvent.Step -> {
                _state.value = _state.value.copy(
                    flowPhase = event.phase,
                    stepIndex = event.index,
                    stepTotal = event.total,
                    downloadProgress = null, // новый шаг — прогресс загрузки сброшен
                )
                com.rootmyvivo.ExploitService.update(getApplication(), phaseName(event.phase))
            }
            is FlowEvent.Log -> _state.value = _state.value.copy(
                log = _state.value.log + LogEntry(++logId, event.line, event.level),
            )
            is FlowEvent.Progress -> {
                // Новый шаг начинается — предыдущие RUNNING закрываются галочкой,
                // иначе они остаются крутиться вечно
                val logList = _state.value.log
                val closed = logList.map {
                    if (it.status == LogLevel.RUNNING) it.copy(status = LogLevel.OK) else it
                }
                val kind = if (event.exploit) LogKind.EXPLOIT else LogKind.NORMAL
                _state.value = _state.value.copy(
                    log = closed + LogEntry(++logId, event.text, LogLevel.RUNNING, kind),
                )
            }
            is FlowEvent.ExploitLive -> {
                val ctx = getApplication<Application>()
                val logList = _state.value.log
                val idx = logList.indexOfLast { it.status == LogLevel.RUNNING && it.kind == LogKind.EXPLOIT }
                val text = if (event.max != null && event.attempt != null) {
                    ctx.getString(R.string.log_exploit_counter, event.attempt, event.max)
                } else {
                    ctx.getString(R.string.log_exploit_start)
                }
                // Живой лог: движок присылает окно из хвоста — накапливаем с
                // устойчивыми id, чтобы каждая строка анимировалась один раз
                val prev = _state.value.exploitLive.lines
                val incoming = event.lines
                // Диф: префикс нового окна совпадает с суффиксом накопленного —
                // это уже известные строки; свежие — только хвост после него
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
                val fresh = incoming.drop(k)
                val acc = (prev + fresh.map { LiveLogLine(++liveId, it) }).takeLast(500)
                _state.value = _state.value.copy(
                    log = if (idx >= 0) {
                        logList.toMutableList().apply { set(idx, logList[idx].copy(text = text)) }
                    } else {
                        logList
                    },
                    exploitLive = ExploitLiveState(event.attempt, event.max, acc),
                )
                com.rootmyvivo.ExploitService.update(ctx, text)
            }
            is FlowEvent.ProgressUpdate -> {
                val logList = _state.value.log
                val idx = logList.indexOfLast { it.status == LogLevel.RUNNING }
                if (idx >= 0) {
                    _state.value = _state.value.copy(
                        log = logList.toMutableList().apply {
                            set(idx, logList[idx].copy(text = event.text))
                        },
                    )
                }
            }
            is FlowEvent.Complete -> {
                val logList = _state.value.log
                val idx = logList.indexOfLast { it.status == LogLevel.RUNNING }
                if (idx >= 0) {
                    val entry = logList[idx]
                    val updated = entry.copy(
                        status = if (event.ok) LogLevel.OK else LogLevel.ERROR,
                        text = event.text ?: entry.text,
                    )
                    _state.value = _state.value.copy(
                        log = logList.toMutableList().apply { set(idx, updated) },
                    )
                } else if (event.text != null) {
                    // Незакрытых шагов нет — итог выводим новой строкой, иначе он потеряется
                    _state.value = _state.value.copy(
                        log = logList + LogEntry(
                            ++logId,
                            event.text,
                            if (event.ok) LogLevel.OK else LogLevel.ERROR,
                        ),
                    )
                }
            }
            is FlowEvent.Download -> _state.value = _state.value.copy(
                downloadProgress = if (event.total > 0) event.read.toFloat() / event.total else null,
            )
            is FlowEvent.Success -> {
                appendDoneLine()
                saveLogHistory(success = true, failReason = null)
                // Софт-ребут рекомендуем только когда KSU реально загрузился
                if (event.softRebootRecommended) markSoftRebootPending()
                _state.value = _state.value.copy(
                    flowResult = FlowResult.Success,
                    rootState = RootState.ROOTED,
                    downloadProgress = null,
                    softRebootPrompt = event.softRebootRecommended,
                    lastLog = _state.value.log,
                )
            }
            FlowEvent.NeedsSoftReboot -> {
                appendDoneLine()
                saveLogHistory(success = true, failReason = null)
                markSoftRebootPending()
                _state.value = _state.value.copy(
                    flowResult = FlowResult.Success,
                    rootState = RootState.ROOTED,
                    downloadProgress = null,
                    softRebootPrompt = true,
                    lastLog = _state.value.log,
                )
            }
            is FlowEvent.Failure -> {
                saveLogHistory(success = false, failReason = event.reason.name)
                _state.value = _state.value.copy(
                    flowResult = FlowResult.Failure(event.reason),
                    rootState = RootState.FAILED,
                    downloadProgress = null,
                    lastLog = _state.value.log,
                )
            }
        }
    }

    // ─────────── Настройки ───────────

    private fun phaseName(phase: Phase?): String = getApplication<Application>().getString(
        when (phase) {
            Phase.CATALOG -> R.string.phase_catalog
            Phase.PAYLOAD -> R.string.phase_payload
            Phase.DOWNLOAD -> R.string.phase_download
            Phase.DEPLOY -> R.string.phase_deploy
            Phase.EXPLOIT -> R.string.phase_exploit
            Phase.ROOT_WAIT -> R.string.phase_root_wait
            Phase.KSU -> R.string.phase_ksu
            null -> R.string.flow_running
        },
    )

    fun selectKsu(variant: KsuVariant) {
        prefs.selectedKsu = variant.id
        _state.value = _state.value.copy(selectedKsu = variant)
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        val new = transform(_state.value.settings)
        prefs.saveSettings(new)
        catalog.url = new.catalogUrl
        _state.value = _state.value.copy(settings = new)
    }

    fun setCatalogUrl(url: String) {
        updateSettings { it.copy(catalogUrl = url.trim().ifEmpty { Catalog.DEFAULT_URL }) }
        detectDevice()
    }

    // ─────────── Диагностика ───────────

    fun adbSelfTest() {
        viewModelScope.launch {
            addLog(getApplication<Application>().getString(R.string.log_adb_test))
            withContext(Dispatchers.IO) { Transport.adbSelfTest(getApplication()) }
                .forEach { addLog(it) }
            refreshTransport()
        }
    }

    private fun addLog(text: String) {
        _state.value = _state.value.copy(
            log = _state.value.log + LogEntry(++logId, text, com.rootmyvivo.root.LogLevel.PLAIN),
        )
    }

    companion object {
        private const val TAG = "NeoVM"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_DOWNLOAD_URL = "https://github.com/RikkaApps/Shizuku/releases"
    }
}
