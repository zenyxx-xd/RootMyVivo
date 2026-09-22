package com.rootmyvivo.ui.flow

import android.content.Context
import com.rootmyvivo.R
import com.rootmyvivo.root.FlowEvent
import com.rootmyvivo.root.LogLevel
import com.rootmyvivo.root.Phase
import com.rootmyvivo.vm.ExploitLiveState
import com.rootmyvivo.vm.FlowResult
import com.rootmyvivo.vm.LiveLogLine
import com.rootmyvivo.vm.LogEntry
import com.rootmyvivo.vm.LogKind
import com.rootmyvivo.vm.RootState
import com.rootmyvivo.vm.UiState

/**
 * Чистое состояние потока рута: FlowEvent → UiState (лог, шаги, живой лог).
 * Один редьюсер на MainViewModel (боевой процесс) и на демо из «Другое»,
 * чтобы демо показывало ровно тот же UI, что реальный запуск.
 * Побочные эффекты (уведомление сервиса, история, флаги) — у вызывающего.
 */
class FlowUiReducer(private val ctx: Context) {

    private var logId = 0L
    private var liveIdx = 0L

    /** Следующий устойчивый id строки лога (ключ анимаций и LazyList). */
    fun nextLogId(): Long = ++logId

    fun phaseText(phase: Phase?): String = ctx.getString(
        when (phase) {
            Phase.CATALOG -> R.string.phase_catalog
            Phase.PAYLOAD -> R.string.phase_payload
            Phase.DOWNLOAD -> R.string.phase_download
            Phase.DEPLOY -> R.string.phase_deploy
            Phase.EXPLOIT -> R.string.phase_exploit
            Phase.KSU -> R.string.phase_ksu
            null -> R.string.flow_running
        },
    )

    /** Текст строки-эксплойта по событию live-лога: счётчик попыток или старт. */
    fun exploitText(attempt: Int?, max: Int?): String =
        if (max != null && attempt != null) {
            ctx.getString(R.string.log_exploit_counter, attempt, max)
        } else {
            ctx.getString(R.string.log_exploit_start)
        }

    fun apply(s: UiState, event: FlowEvent): UiState = when (event) {
        is FlowEvent.Step -> s.copy(
            flowPhase = event.phase,
            stepIndex = event.index,
            stepTotal = event.total,
            downloadProgress = null, // новый шаг — прогресс загрузки сброшен
        )

        is FlowEvent.Log -> s.copy(
            log = s.log + LogEntry(nextLogId(), event.line, event.level),
        )

        is FlowEvent.Progress -> {
            // Новый шаг начинается — предыдущие RUNNING закрываются галочкой,
            // иначе они остаются крутиться вечно
            val closed = s.log.map {
                if (it.status == LogLevel.RUNNING) it.copy(status = LogLevel.OK) else it
            }
            val kind = if (event.exploit) LogKind.EXPLOIT else LogKind.NORMAL
            s.copy(log = closed + LogEntry(nextLogId(), event.text, LogLevel.RUNNING, kind))
        }

        is FlowEvent.ProgressUpdate -> {
            val idx = s.log.indexOfLast { it.status == LogLevel.RUNNING }
            if (idx < 0) s else s.copy(
                log = s.log.toMutableList().apply { set(idx, s.log[idx].copy(text = event.text)) },
            )
        }

        is FlowEvent.Complete -> {
            val idx = s.log.indexOfLast { it.status == LogLevel.RUNNING }
            if (idx >= 0) {
                val entry = s.log[idx]
                val updated = entry.copy(
                    status = if (event.ok) LogLevel.OK else LogLevel.ERROR,
                    text = event.text ?: entry.text,
                )
                s.copy(log = s.log.toMutableList().apply { set(idx, updated) })
            } else if (event.text != null) {
                // Незакрытых шагов нет — итог выводим новой строкой, иначе он потеряется
                s.copy(
                    log = s.log + LogEntry(
                        nextLogId(),
                        event.text,
                        if (event.ok) LogLevel.OK else LogLevel.ERROR,
                    ),
                )
            } else {
                s
            }
        }

        is FlowEvent.Download -> s.copy(
            downloadProgress = if (event.total > 0) event.read.toFloat() / event.total else null,
        )

        is FlowEvent.ExploitLive -> {
            val idx = s.log.indexOfLast { it.status == LogLevel.RUNNING && it.kind == LogKind.EXPLOIT }
            // Живой лог: движок присылает окно из хвоста — накапливаем с
            // устойчивыми id, чтобы каждая строка анимировалась один раз
            val prev = s.exploitLive.lines
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
            val fresh = incoming.drop(k).map { LiveLogLine(++liveIdx, it) }
            val acc = (prev + fresh).takeLast(500)
            val text = exploitText(event.attempt, event.max)
            s.copy(
                log = if (idx >= 0) {
                    s.log.toMutableList().apply { set(idx, s.log[idx].copy(text = text)) }
                } else {
                    s.log
                },
                exploitLive = ExploitLiveState(event.attempt, event.max, acc),
            )
        }

        is FlowEvent.Success -> s.copy(
            flowResult = FlowResult.Success,
            rootState = RootState.ROOTED,
            downloadProgress = null,
            softRebootPrompt = event.softRebootRecommended,
        )

        FlowEvent.NeedsSoftReboot -> s.copy(
            flowResult = FlowResult.Success,
            rootState = RootState.ROOTED,
            downloadProgress = null,
            softRebootPrompt = true,
        )

        is FlowEvent.Failure -> s.copy(
            flowResult = FlowResult.Failure(event.reason),
            rootState = RootState.FAILED,
            downloadProgress = null,
        )
    }
}
