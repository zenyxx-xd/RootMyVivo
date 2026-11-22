package com.rootmyvivo.shell

import android.content.Context
import android.util.Log
import com.rootmyvivo.data.Prefs
import com.rootmyvivo.root.LocalRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Выполнение команд от root — БЕЗ adb-механики вообще (как в 1.4):
 * эксплойт запускается напрямую из приложения (LocalRunner), рут-команды —
 * через su-демон эксплойта (GL) или биндинг KSU /system/bin/su (запрос
 * подтверждается в менеджере). ADB-порт, persist-проперти и adb_keys
 * вырезаны полностью.
 */
object Transport {

    private const val TAG = "NeoTransport"

    lateinit var prefs: Prefs

    // ─────────── Общий API ───────────

    /**
     * Команда su: наш пейлоад кладёт клиент в RMV_HOME/su (см. LocalRunner),
     * путь знает приложение. Пробуем оба: сначала локальный, потом PATH.
     */
    const val SU = "/data/local/tmp/su"

    /**
     * Прямой вызов su-клиента эксплойта из процесса приложения — без транспорта.
     * Клиент сам ходит в демон на unix-сокете, так что работает всегда,
     * пока жив демон.
     */
    fun suLocal(ctx: Context, command: String, timeoutSec: Int = 60): Pair<Int, String> {
        val suBin = File(ctx.filesDir, "rmv/su")
        val bin = if (suBin.isFile && suBin.canExecute()) suBin.absolutePath else SU
        // Демон мёртв (сокета нет) — не запускаем клиент: его собственный
        // таймаут на мёртвом сокете оттягивал финал флоу на секунды
        if (!File("/data/local/tmp/temp_su.sock").exists() &&
            !File(ctx.filesDir, "rmv/temp_su.sock").exists() &&
            !File(ctx.filesDir, "rmv/su.sock").exists()
        ) return -1 to "su daemon not running"
        return suVia(bin, command, timeoutSec)
    }

    /**
     * su через конкретный бинарь: /system/bin/su (биндинг KSU) из приложения
     * вызывает ЗАПРОС В МЕНЕДЖЕРЕ — пользователь подтверждает один раз,
     * дальше ядро пускает наш uid. Канал работает без adb вообще; ждёт
     * подтверждения (таймаут), чтение в отдельных потоках — не вешается.
     */
    fun suVia(bin: String, command: String, timeoutSec: Int): Pair<Int, String> = try {
        val proc = Runtime.getRuntime().exec(arrayOf(bin, "-c", command))
        // Чтение stdout/stderr в отдельных потоках: blocking-read до EOF в
        // useLines зависал навсегда, если KSU-су ждёт авторизации менеджера
        // (что и есть «лог застревает на проверке su») — до waitFor с таймаутом
        // дело не доходило даже при timeoutSec=5.
        var outBuf = ""
        var errBuf = ""
        val outT = Thread {
            runCatching {
                outBuf = proc.inputStream.bufferedReader().readText()
            }
        }.apply { isDaemon = true; start() }
        val errT = Thread {
            runCatching {
                errBuf = proc.errorStream.bufferedReader().readText()
            }
        }.apply { isDaemon = true; start() }
        val finished = proc.waitFor(timeoutSec.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            proc.destroy()
            // даём reader'ам шанс дочитать буфер после destroy
            outT.join(500); errT.join(500)
            return -1 to (outBuf + errBuf).trim()
        }
        outT.join(1000); errT.join(1000)
        val out = buildString {
            append(outBuf)
            if (errBuf.isNotBlank()) append(errBuf)
        }
        proc.exitValue() to out
    } catch (e: Exception) {
        -1 to (e.message ?: "local su error")
    }

    /**
     * Канал менеджеров: /system/bin/su из приложения. Если наш uid уже
     * авторизован — выполняется мгновенно, без диалога; если нет —
     * менеджер показывает root-запрос (менеджер должен быть запущен).
     */
    fun suBind(command: String, timeoutSec: Int): Pair<Int, String> =
        suVia("/system/bin/su", command, timeoutSec)

    /** KSU уже перехватил su? После загрузки модуля локальный клиент форвардится
     *  в /system/bin/su, который не авторизован для приложения (и ждёт ответа
     *  менеджера) — в этом режиме только через транспорт (shell-домен, allow_shell). */
    private fun ksuSuPresent(): Boolean = File("/system/bin/su").exists()

    /** Команда в одинарных кавычках с корректным экранированием. */
    private fun shQuote(cmd: String): String = "'" + cmd.replace("'", "'\\''") + "'"

    /**
     * Выполнить команду через su — железобетонная версия.
     *
     *  1. Локальный su-клиент демона эксплойта (GL-модель, filesDir/rmv/su)
     *     — без транспорта, пока KSU не перехватил su.
     *  2. ADB-канал: ОДНА shell-команда со ВСЕМИ известными su-путями
     *     (su, /system/bin/su, /data/adb/ksu/bin/su, su 0 …) — первый
     *     живой su и отвечает; каждый путь глушится по stderr, exit 0 —
     *     только если команда реально выполнилась.
     *  2. Биндинг KSU /system/bin/su (только по явному запросу): менеджер
     *     показывает root-запрос, пользователь подтверждает.
     *  ADB-цепочка удалена: приложение работает без adb-порта.
     */
    suspend fun su(
        ctx: Context,
        command: String,
        timeoutSec: Int = 300,
        interactive: Boolean = false,
        /** Проверка статуса: одна попытка, без ретраев и пауз —
         *  статус-чеки не должны держать UI/флоу на секундах */
        single: Boolean = false,
    ): Pair<Int, String> {
        // 1) локальный клиент демона эксплойта — быстрый путь
        if (!ksuSuPresent()) {
            val local = suLocal(ctx, command, timeoutSec = minOf(timeoutSec, 60))
            if (local.first == 0) return local
        }

        // 2) биндинг KSU: только по явному запросу (диалог подтверждения
        // в менеджере); так выполняют root-команды сами менеджеры
        if (interactive && File("/system/bin/su").exists()) {
            // exists(), не canExecute(): виртуальный su-файл KSU может не
            // отдавать x-бит — проверка canExecute молча убивала канал
            val viaBind = suVia("/system/bin/su", command, timeoutSec = minOf(timeoutSec, 60))
            if (viaBind.first == 0) return viaBind
        }
        return -1 to "no transport (adb removed)"
    }


    /**
     * Быстрая локальная проверка «root есть» без транспорта. Приоритет:
     *  1. /dev/dfm5 — маркер бутстрапа DirtyFrag «late-load прошёл»;
     *     живёт в devtmpfs до перезагрузки — рут текущей загрузки.
     *  2. /system/bin/su — биндинг KSU (семантика DirtyFrag).
     *  3. suLocal — демон GL-модели; 4. модуль в /proc/modules (форки
     *     с susfs его прячут — поэтому он последний).
     */
    fun rootActiveQuick(ctx: Context): Boolean {
        if (java.io.File("/dev/dfm5").exists()) return true
        if (File("/system/bin/su").canExecute()) return true
        if (!File("/system/bin/su").exists()) {
            val (code, out) = try {
                suLocal(ctx, "id", timeoutSec = 5)
            } catch (_: Exception) {
                -1 to ""
            }
            return code == 0 && out.contains("uid=0")
        }
        return try {
            File("/proc/modules").readText().contains("kernelsu", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    /** Команда в shell-домене. ADB-механика вырезана: команды от root
     *  идут через su() (демон эксплойта / биндинг KSU). */
    suspend fun exec(ctx: Context, command: String, timeoutSec: Int = 300): Pair<Int, String> =
        -1 to "no transport (adb removed)"

    /** Передача файла на устройство. ADB-механика вырезана: пейлоады
     *  деплоятся локально (LocalRunner.copy в filesDir/rmv). */
    suspend fun deploy(ctx: Context, localPath: String, remotePath: String): Pair<Boolean, String> =
        false to "no transport (adb removed)"

}
