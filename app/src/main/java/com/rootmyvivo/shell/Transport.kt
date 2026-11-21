package com.rootmyvivo.shell

import android.content.Context
import android.util.Log
import com.rootmyvivo.data.Prefs
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

    private fun adbAlive(ctx: Context): Boolean = try {
        val (code, out) = AdbWire.shell(ctx, "echo RMV_OK", timeoutMs = 8000, allowDialog = false)
        code == 0 && out.contains("RMV_OK")
    } catch (_: Exception) {
        false
    }

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
    fun suLocal(ctx: Context, command: String, timeoutSec: Int = 60): Pair<Int, String> = try {
        val suBin = File(ctx.filesDir, "rmv/su")
        val bin = if (suBin.isFile && suBin.canExecute()) suBin.absolutePath else SU
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
    suspend fun su(ctx: Context, command: String, timeoutSec: Int = 300): Pair<Int, String> {
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
        while (attempt < 3) {
            last = exec(ctx, chain, timeoutSec)
            if (last.first == 0) return last
            // Нет транспорта (порт не закреплён) — ретраи бессмысленны:
            // три попытки с паузами держали проверку состояния по 5 секунд
            if (!adbUsable(ctx)) break
            // код -1 = транспортная авария; adbd мог ещё не подняться
            delay(1500L)
            attempt++
        }
        return last
    }

    fun detectBlocking(ctx: Context): TransportState {
        if (prefs.firstRootDone && adbUsable(ctx)) return TransportState.Adb
        return TransportState.None
    }

    /**
     * Быстрая локальная проверка «root есть» без транспорта. KSU после
     * late-load биндит su в /system/bin/su (как в DirtyFrag) — это первый
     * и самый надёжный сигнал: работает даже когда форк прячет модуль из
     * /proc/modules (susfs) и когда adb-канал мёртв.
     */
    fun rootActiveQuick(ctx: Context): Boolean {
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
            if (prefs.firstRootDone && adbUsable(ctx)) {
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
            if (prefs.firstRootDone && adbUsable(ctx)) {
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
    suspend fun persistAfterRoot(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        // 1. persist-порт: shell-домен, затем su-демон
        exec(ctx, "setprop persist.adb.tcp.port 5555", timeoutSec = 15)
        var port = exec(ctx, "getprop persist.adb.tcp.port", timeoutSec = 10).second.trim()
        if (port != "5555") {
            Log.i(TAG, "setprop via transport failed (port=$port), trying su daemon")
            su(ctx, "setprop persist.adb.tcp.port 5555", timeoutSec = 15)
            port = su(ctx, "getprop persist.adb.tcp.port", timeoutSec = 10).second.trim()
        }
        val portOk = port == "5555"
        Log.i(TAG, "persist port: $port ($portOk)")
        // 2. ключ в adb_keys (su: файловые операции из демона работают)
        AdbWire.publicKeyAndroid(ctx)?.let { key ->
            val (kc, kout) = su(ctx, "echo \"$key rootmyvivo\" >> /data/misc/adb/adb_keys")
            Log.i(TAG, "adb_keys install: code=$kc ${kout.take(60)}")
        }
        // 3. adbd перечитывает persist-порт только на старте — рестарт
        if (portOk) {
            su(ctx, "pkill -x adbd", timeoutSec = 10)
            // init поднимает adbd не мгновенно: ждём и проверяем живой порт,
            // при необходимости дёргаем ещё раз (первый pkill мог прийтись
            // на момент, когда persist-свойство ещё не долетело до adbd)
            repeat(4) { attempt ->
                delay(1500)
                if (adbAlive(ctx)) {
                    Log.i(TAG, "adbd listening on 5555 after restart (attempt ${attempt + 1})")
                    return@withContext true
                }
            }
            // порт не поднялся — перепроверяем свойство и рестартим ещё раз
            val recheck = su(ctx, "getprop persist.adb.tcp.port", timeoutSec = 10).second.trim()
            if (recheck == "5555") {
                su(ctx, "pkill -x adbd", timeoutSec = 10)
                delay(3000)
                if (adbAlive(ctx)) {
                    Log.i(TAG, "adbd listening on 5555 after second restart")
                    return@withContext true
                }
            }
            Log.w(TAG, "adbd did not come up on 5555 (port prop=$recheck)")
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
