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

/** Состояние ADB-канала (закрепление после первого рута). */
sealed interface TransportState {
    /** ADB tcp 5555 через wire-клиент (порт закреплён persist-проперти) */
    data object Adb : TransportState
    data object None : TransportState
}

/**
 * Мост для выполнения команд в shell-домене (uid=2000, u:r:shell:s0).
 *
 * Эксплойт запускается НЕ через транспорт, а напрямую из приложения
 * (LocalRunner + RMV_HOME=filesDir/rmv) — Shizuku и ADB для первого
 * запуска не нужны. Транспорт остался только для ADB tcp 5555:
 * пост-root закрепление (persist.adb.tcp.port + adb_keys) и команды,
 * которым нужен именно shell-домен.
 */
object Transport {

    private const val TAG = "NeoTransport"

    lateinit var prefs: Prefs

    // ─────────── ADB wire ───────────

    /**
     * ADB-канал пригоден к работе. Живой авторизованный сокет — дешевле
     * нового roundtrip-пробеса; проверяем порт (без диалога) только когда
     * сокета нет.
     */
    private fun adbUsable(ctx: Context): Boolean =
        AdbWire.isConnected() || adbAlive(ctx)

    /** Подождать живой ADB-канал (DF: бутстрап только что рестартил adbd).
     *  true — порт 5555 отвечает. */
    suspend fun adbWaitAlive(ctx: Context, timeoutMs: Long = 30_000L): Boolean =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (adbAlive(ctx)) return@withContext true
                delay(1000L)
            }
            adbAlive(ctx)
        }

    private fun adbAlive(ctx: Context): Boolean =
        // probe: один раунд без ретрая — неавторизованный ключ фейлится
        // за ~1.5с вместо двух полных попыток
        AdbWire.probe(ctx)

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
     *  3. Транспорт лечится сам: AdbWire пересоздаёт протухший сокет
     *     (adbd после рестарта выглядит «connected»), здесь — 3 попытки
     *     с паузой на случай, пока adbd поднимается.
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
        // 1) локальный клиент — быстрый путь без транспорта
        if (!ksuSuPresent()) {
            val local = suLocal(ctx, command, timeoutSec = minOf(timeoutSec, 60))
            if (local.first == 0) return local
        }

        // 2) ADB-цепочка по всем su-путям
        val q = shQuote(command)
        val chain = buildString {
            append("su -c ").append(q).append(" 2>/dev/null && exit 0; ")
            append("/system/bin/su -c ").append(q).append(" 2>/dev/null && exit 0; ")
            append("/data/adb/ksu/bin/su -c ").append(q).append(" 2>/dev/null && exit 0; ")
            append("su 0 sh -c ").append(q).append(" 2>/dev/null && exit 0; ")
            append("exit 1")
        }
        var last = -1 to "no transport"
        var attempt = 0
        // adbUsable дорогой (полный раунд adb) — считаем ОДИН раз
        var usable = adbUsable(ctx)
        val maxAttempts = if (single) 1 else 2
        while (attempt < maxAttempts) {
            if (!usable) break
            last = exec(ctx, chain, timeoutSec)
            if (last.first == 0) return last
            if (attempt == 0) {
                // канал мог умереть на этой команде (adbd перезапущен) —
                // перепроверяем ОДИН раз уже после провала
                usable = adbUsable(ctx)
            }
            // Пауза короткая: длинные ретраи откладывали финал (ошибку
            // эксплойта) на 4+ секунды
            delay(600L)
            attempt++
        }
        // Последний канал (только по явному запросу, не в фоновых циклах):
        // биндинг KSU /system/bin/su из приложения — менеджер показывает
        // запрос, пользователь подтверждает, команда выполняется от root.
        // Так делают сами менеджеры; работает и без adb
        if (interactive && File("/system/bin/su").exists()) {
            // exists(), не canExecute(): виртуальный su-файл KSU может не
            // отдавать x-бит — проверка canExecute молча убивала канал
            val viaBind = suVia("/system/bin/su", command, timeoutSec = minOf(timeoutSec, 60))
            if (viaBind.first == 0) return viaBind
        }
        return last
    }

    fun detectBlocking(ctx: Context): TransportState {
        if (adbUsable(ctx)) return TransportState.Adb
        return TransportState.None
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

    /** Выполнить команду в shell-домене через ADB (нужен закреплённый порт). */
    suspend fun exec(ctx: Context, command: String, timeoutSec: Int = 300): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            if (adbUsable(ctx)) {
                try {
                    return@withContext AdbWire.shell(ctx, command, timeoutMs = timeoutSec * 1000L, allowDialog = true)
                } catch (e: Exception) {
                    Log.w(TAG, "adb exec failed: ${e.message}")
                }
            }
            -1 to "no transport (adb not persisted yet)"
        }

    /**
     * Передать файл в /data/local/tmp по ADB.
     * (успех, причина-текст): false — вторая строка объясняет что именно
     * не так — это уходит в лог процесса.
     */
    suspend fun deploy(ctx: Context, localPath: String, remotePath: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            if (adbUsable(ctx)) {
                val err = AdbWire.deploy(ctx, localPath, remotePath)
                if (err == null) return@withContext true to ""
                return@withContext false to err
            }
            false to (ctx.getString(com.rootmyvivo.R.string.deploy_err_no_transport))
        }

    /**
     * Пост-root закрепление: persist-порт + свой ключ в adb_keys —
     * бесшовная авторизация после любых перезагрузок.
     *
     * Порт пробуем двумя путями: shell-домен (AdbWire — основной) и
     * su-демон эксплойта (фолбэк: на части прошивок property service пускает
     * и его контекст). adbd перечитывает persist-порт только на старте —
     * рестарим и ждём, пока порт 5555 реально начнёт слушаться.
     */
    suspend fun persistAfterRoot(ctx: Context, interactive: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            // getprop приложение читает локально — без транспорта
            fun readPort(): String =
                LocalRunner.exec("getprop persist.adb.tcp.port", timeoutSec = 10).second.trim()

            // 1. порт: service (живой adbd переключается на TCP сразу,
            //    без рестарта) + persist (выживает перезагрузку).
            //    Канал: adb-транспорт, а если он мёртв — биндинг KSU
            //    (interactive: менеджер покажет root-запрос)
            suspend fun setPort(): Boolean {
                exec(ctx, "setprop persist.adb.tcp.port 5555; setprop service.adb.tcp.port 5555", timeoutSec = 15)
                if (readPort() != "5555") {
                    su(
                        ctx,
                        "setprop persist.adb.tcp.port 5555; setprop service.adb.tcp.port 5555",
                        timeoutSec = 30,
                        interactive = interactive,
                    )
                }
                return readPort() == "5555"
            }
            val portOk = setPort()
            Log.i(TAG, "persist port: ${readPort()} ($portOk)")
            // 2. ключ в adb_keys (su: файловые операции из root работают)
            AdbWire.publicKeyAndroid(ctx)?.let { key ->
                val (kc, kout) = su(
                    ctx,
                    "echo \"$key rootmyvivo\" >> /data/misc/adb/adb_keys",
                    timeoutSec = 30,
                    interactive = interactive,
                )
                Log.i(TAG, "adb_keys install: code=$kc ${kout.take(60)}")
            }
            // 3. порт из свойства мог подхватить и живой adbd (service-prop);
            //    если нет — рестарт через init. ВСЕ рестарты тоже interactive:
            //    не-interactive su без adb молча фейлится — из-за этого
            //    adbd никогда не перезапускался и порт «не поднимался»
            if (portOk) {
                if (adbAlive(ctx)) {
                    Log.i(TAG, "adbd listening on 5555 without restart (service prop)")
                    return@withContext true
                }
                // ctl.restart — правильный путь через init (pkill мог
                // оставить adbd мёртвым на части прошивок)
                su(ctx, "setprop ctl.restart adbd", timeoutSec = 15, interactive = interactive)
                // init поднимает adbd не мгновенно: ждём и проверяем живой порт
                repeat(4) { attempt ->
                    delay(1500)
                    if (adbAlive(ctx)) {
                        Log.i(TAG, "adbd listening on 5555 after restart (attempt ${attempt + 1})")
                        return@withContext true
                    }
                }
                su(ctx, "setprop ctl.stop adbd; setprop ctl.start adbd", timeoutSec = 15, interactive = interactive)
                delay(3000)
                if (adbAlive(ctx)) {
                    Log.i(TAG, "adbd listening on 5555 after second restart")
                    return@withContext true
                }
                Log.w(TAG, "adbd did not come up on 5555 (port prop=${readPort()})")
            }
            portOk
        }

    /** Диагностика adb-канала (не запускает эксплойт). */
    fun adbSelfTest(ctx: Context): List<String> = try {
        val log = mutableListOf<String>()
        log += "[adb] key: ${AdbWire.publicKeyAndroid(ctx)?.take(24) ?: "?"}…"
        AdbWire.connect(ctx, allowDialog = true, authTimeoutMs = 60_000)
        log += "[adb] CNXN: authorized"
        val (code, out) = AdbWire.shell(ctx, "id")
        log += "[adb] shell: exit=$code ${out.trim().take(60)}"
        val tmp = File(ctx.filesDir, "selftest.bin")
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        tmp.writeBytes(payload)
        val deployErr = AdbWire.deploy(ctx, tmp.absolutePath, "/data/local/tmp/neo_selftest.bin")
        val (_, md5) = AdbWire.shell(ctx, "md5sum /data/local/tmp/neo_selftest.bin | cut -d' ' -f1; rm -f /data/local/tmp/neo_selftest.bin")
        val local = java.security.MessageDigest.getInstance("MD5").digest(payload).joinToString("") { "%02x".format(it) }
        log += "[adb] deploy 64KB: ${deployErr ?: "ok"}, md5 ok: ${md5.trim() == local}"
        tmp.delete()
        log.forEach { Log.i(TAG, it) }
        log
    } catch (e: Exception) {
        Log.i(TAG, "[adb] ERROR: ${e.message}")
        listOf("[adb] ERROR: ${e.message}")
    }
}
