package com.rootmyvivo.vm

import com.rootmyvivo.data.DeviceInfo
import com.rootmyvivo.data.PayloadEntry
import com.rootmyvivo.data.Settings
import com.rootmyvivo.root.KsuVariant
import com.rootmyvivo.root.LogLevel
import com.rootmyvivo.root.Phase
import com.rootmyvivo.shell.TransportState

enum class RootState { UNKNOWN, CHECKING, NOT_ROOTED, ROOTED, FAILED }
enum class CatalogState { LOADING, READY, ERROR }

data class LogEntry(val id: Long, val text: String, val status: LogLevel, val kind: LogKind = LogKind.NORMAL)

/** Тип строки лога: обычная или шаг эксплойта (раскрываемый аккордеон). */
enum class LogKind { NORMAL, EXPLOIT }

/** Строка живого лога с устойчивым id — для анимации появления. */
data class LiveLogLine(val id: Long, val text: String)

/** Живой лог эксплойта: накопленный вывод бинаря для аккордеона. */
data class ExploitLiveState(
    val attempt: Int? = null,
    val max: Int? = null,
    val lines: List<LiveLogLine> = emptyList(),
)

sealed interface FlowResult {
    data object Success : FlowResult
    data class Failure(val reason: com.rootmyvivo.root.FlowEvent.Reason) : FlowResult
}

data class UiState(
    // устройство и каталог
    val device: DeviceInfo? = null,
    val payload: PayloadEntry? = null,
    val catalogState: CatalogState = CatalogState.LOADING,
    // транспорт и root
    val transport: TransportState = TransportState.None,
    val rootState: RootState = RootState.UNKNOWN,
    // настройки
    val settings: Settings = Settings(),
    val selectedKsu: KsuVariant = KsuVariant.RESUKISU,
    // процесс рута
    val flowRunning: Boolean = false,
    val flowPhase: Phase? = null,
    val stepIndex: Int = 0,
    val stepTotal: Int = 6,
    val downloadProgress: Float? = null,
    val log: List<LogEntry> = emptyList(),
    val exploitLive: ExploitLiveState = ExploitLiveState(),
    val flowResult: FlowResult? = null,
    val softRebootPrompt: Boolean = false,
    /** Root есть, но soft reboot ещё не выполнен — показать кнопку на главной */
    val needsSoftReboot: Boolean = false,
    /** Сохранённый последний лог для просмотра */
    val lastLog: List<LogEntry> = emptyList(),
    val logViewerOpen: Boolean = false,
)
