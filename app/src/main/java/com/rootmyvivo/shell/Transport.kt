package com.rootmyvivo.shell

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.rootmyvivo.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Состояние транспорта для запуска эксплойта. */
sealed interface TransportState {
    /** ADB tcp 5555 через wire-клиент (после первого root) */
    data object Adb : TransportState
    /** Shizuku запущен и разрешение выдано */
    data object Shizuku : TransportState
    /** Shizuku запущен, разрешение не выдано */
    data object ShizukuNeedsPermission : TransportState
    data object None : TransportState
}

/**
 * Мост для выполнения команд в shell-домене (uid=2000, u:r:shell:s0).
 *
 * Политика: до первого успешного root — только Shizuku UserService.
 * После (firstRootDone) — ADB tcp 5555 через AdbWire, Shizuku не нужен.
 */
object Transport {

    private const val TAG = "NeoTransport"
    private const val SHIZUKU_CHUNK = 512 * 1024

    lateinit var prefs: Prefs
    var onBinderStateChanged: (() -> Unit)? = null

    // ─────────── Shizuku binder lifecycle ───────────

    @Volatile
    var shizukuAlive: Boolean = false
        private set

    private var registered = false

    /** Зарегистрировать листенеры (однократно, из Application). */
    fun initShizuku(context: Context) {
        if (registered) return
        registered = true
        try {
            Shizuku.addBinderReceivedListenerSticky(Shizuku.OnBinderReceivedListener {
                shizukuAlive = true
                Log.i(TAG, "Shizuku binder received")
                onBinderStateChanged?.invoke()
            })
            Shizuku.addBinderDeadListener(Shizuku.OnBinderDeadListener {
                shizukuAlive = false
                shizukuService = null
                Log.i(TAG, "Shizuku binder dead")
                onBinderStateChanged?.invoke()
            })
            Shizuku.addRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener { _, granted ->
                Log.i(TAG, "Shizuku permission granted=$granted")
            })
        } catch (e: Exception) {
            Log.e(TAG, "initShizuku failed", e)
        }
    }

    fun shizukuPermissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    /** Запросить разрешение; onGranted вызовется после подтверждения пользователем. */
    fun requestShizukuPermission(requestCode: Int = 100, onGranted: () -> Unit): Boolean = try {
        when {
            Shizuku.getVersion() < 11 -> false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                onGranted()
                true
            }
            Shizuku.shouldShowRequestPermissionRationale() -> false
            else -> {
                Shizuku.addRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                    if (grantResult == PackageManager.PERMISSION_GRANTED) onGranted()
                })
                Shizuku.requestPermission(requestCode)
                false
            }
        }
    } catch (e: Throwable) {
        Log.e(TAG, "requestPermission failed", e)
        false
    }

    // ─────────── Shizuku UserService ───────────

    @Volatile
    private var shizukuService: IShellService? = null

    fun bindShizukuService(context: Context): Boolean {
        shizukuService?.let { return true }
        if (!shizukuAlive || !shizukuPermissionGranted()) return false
        return try {
            var binder: IBinder? = null
            val latch = CountDownLatch(1)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
                    binder = b
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    shizukuService = null
                }
            }
            Shizuku.bindUserService(
                Shizuku.UserServiceArgs(ComponentName(context, ShellServiceImpl::class.java))
                    .daemon(false)
                    .processNameSuffix("service")
                    .version(1),
                conn,
            )
            latch.await(15, TimeUnit.SECONDS)
            val b = binder
            shizukuService = if (b != null && b.pingBinder()) IShellService.Stub.asInterface(b) else null
            shizukuService != null
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService failed", e)
            false
        }
    }

    private fun execShizuku(ctx: Context, command: String): Pair<Int, String> = try {
        val svc = shizukuService ?: bindShizukuService(ctx)?.let { shizukuService }
            ?: return -1 to "Shizuku service not connected"
        val output = svc.exec(command)
        val exit = Regex("EXIT=(-?\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        exit to output.removePrefix("EXIT=$exit\n")
    } catch (e: Exception) {
        Log.e(TAG, "shizuku exec failed", e)
        shizukuService = null
        -1 to (e.message ?: "shizuku error")
    }

    private fun deployShizuku(ctx: Context, localPath: String, remotePath: String): Boolean {
        return try {
            val svc = shizukuService ?: bindShizukuService(ctx)?.let { shizukuService } ?: return false
            val data = File(localPath).readBytes()
            var offset = 0L
            while (offset < data.size) {
                val len = minOf(SHIZUKU_CHUNK, (data.size - offset).toInt()).toLong()
                if (!svc.writeFileChunk(remotePath, offset, data.copyOfRange(offset.toInt(), (offset + len).toInt()))) {
                    return false
                }
                offset += len
            }
            svc.exec("chmod 644 $remotePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "shizuku deploy failed", e)
            false
        }
    }

    // ─────────── ADB wire ───────────

    private fun adbAlive(ctx: Context): Boolean = try {
        val (code, out) = AdbWire.shell(ctx, "echo RMV_OK", timeoutMs = 8000, allowDialog = false)
        code == 0 && out.contains("RMV_OK")
    } catch (_: Exception) {
        false
    }

    // ─────────── Общий API ───────────

    /**
     * Команда su: наш пейлоад кладёт клиент в /data/local/tmp/su (не в PATH),
     * upstream — в /apex (в PATH). Пробуем оба: сначала локальный, потом PATH.
     */
    const val SU = "/data/local/tmp/su"
    const val SU_CMD = "$SU -c"

    /**
     * Прямой вызов su-клиента эксплойта из процесса приложения — без транспорта.
     * Клиент сам ходит в демон на unix-сокете, так что работает даже когда
     * Shizuku мёртв, а adb-порт ещё не закреплён.
     */
    fun suLocal(command: String, timeoutSec: Int = 60): Pair<Int, String> = try {
        val proc = Runtime.getRuntime().exec(arrayOf(SU, "-c", command))
        val out = buildString {
            proc.inputStream.bufferedReader().useLines { lines -> lines.forEach { append(it).append('\n') } }
            proc.errorStream.bufferedReader().useLines { lines -> lines.forEach { append(it).append('\n') } }
        }
        val code = if (proc.waitFor(timeoutSec.toLong(), TimeUnit.SECONDS)) proc.exitValue() else {
            proc.destroy()
            -1
        }
        code to out
    } catch (e: Exception) {
        -1 to (e.message ?: "local su error")
    }

    /** KSU уже перехватил su? После загрузки модуля локальный клиент форвардится
     *  в /system/bin/su, который не авторизован для приложения (и ждёт ответа
     *  менеджера) — в этом режиме только через транспорт (shell-домен, allow_shell). */
    private fun ksuSuPresent(): Boolean = File("/system/bin/su").exists()

    /** Выполнить команду через su: напрямую из приложения, затем через транспорт. */
    suspend fun su(ctx: Context, command: String, timeoutSec: Int = 300): Pair<Int, String> {
        // 1. Напрямую через демон эксплойта — пока KSU не перехватил su
        if (!ksuSuPresent()) {
            val local = suLocal(command, timeoutSec = minOf(timeoutSec, 60))
            if (local.first == 0) return local
        }
        // 2. Через транспорт (shell-домен): наш su, а после KSU — PATH-su (allow_shell)
        val r1 = exec(ctx, "$SU -c '$command'", timeoutSec)
        if (r1.first == 0) return r1
        return exec(ctx, "su -c '$command'", timeoutSec)
    }

    fun detectBlocking(ctx: Context): TransportState {
        if (prefs.firstRootDone && adbAlive(ctx)) return TransportState.Adb
        return when {
            shizukuAlive && shizukuPermissionGranted() -> TransportState.Shizuku
            shizukuAlive -> TransportState.ShizukuNeedsPermission
            else -> TransportState.None
        }
    }

    /** Выполнить команду в shell-домене по политике транспорта. */
    suspend fun exec(ctx: Context, command: String, timeoutSec: Int = 300): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            when {
                prefs.firstRootDone && adbAlive(ctx) ->
                    try {
                        AdbWire.shell(ctx, command, timeoutMs = timeoutSec * 1000L, allowDialog = true)
                    } catch (e: Exception) {
                        -1 to (e.message ?: "adb error")
                    }

                shizukuAlive && shizukuPermissionGranted() -> execShizuku(ctx, command)
                else -> -1 to "no transport"
            }
        }

    /** Передать файл в /data/local/tmp по политике транспорта. */
    suspend fun deploy(ctx: Context, localPath: String, remotePath: String): Boolean =
        withContext(Dispatchers.IO) {
            when {
                prefs.firstRootDone && adbAlive(ctx) -> AdbWire.deploy(ctx, localPath, remotePath)
                shizukuAlive && shizukuPermissionGranted() -> deployShizuku(ctx, localPath, remotePath)
                else -> false
            }
        }

    /**
     * Пост-root закрепление: persist-порт + свой ключ в adb_keys —
     * бесшерстная авторизация после любых перезагрузок.
     *
     * Порт пробуем двумя путями: shell-домен (Shizuku/AdbWire — основной) и
     * su-демон эксплойта (фолбэк: на части прошивок property service пускает
     * и его контекст, а транспорт после soft reboot бывает мёртв). adbd
     * перечитывает persist-порт только на старте — рестарим и ждём, пока
     * порт 5555 реально начнёт слушаться.
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
        // (init перезапустит его; на транспорте Shizuku это безопасно)
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

    /**
     * Детектор сломанной системы (холодный старт через ProbeActivity).
     * Временно выключен: включить обратно — выставить true.
     */
    private const val SYSTEM_HEALTH_DETECTION = false

    /**
     * Проверка здоровья системы: холодный старт (ProbeActivity в отдельном
     * процессе). Перед пробей убиваем кэшированный :probe — иначе активити
     * стартует в живом процессе без форка от зиготы и проба всегда «успешна».
     * am kill трогает только кэшированные процессы: главный процесс приложения
     * (activity + foreground service) не затрагивается.
     * Если эксплойт зациклил зиготу — процесс не ответвится, команда зависнет
     * или упадёт → система повреждена.
     */
    suspend fun systemHealthy(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        if (!SYSTEM_HEALTH_DETECTION) return@withContext true
        try {
            val (code, out) = exec(
                ctx,
                "am kill com.rootmyvivo; am start -W -n com.rootmyvivo/.ProbeActivity",
                timeoutSec = 30,
            )
            code == 0 && out.contains("Status: ok")
        } catch (_: Exception) {
            false
        }
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
        val ok = AdbWire.deploy(ctx, tmp.absolutePath, "/data/local/tmp/neo_selftest.bin")
        val (_, md5) = AdbWire.shell(ctx, "md5sum /data/local/tmp/neo_selftest.bin | cut -d' ' -f1; rm -f /data/local/tmp/neo_selftest.bin")
        val local = java.security.MessageDigest.getInstance("MD5").digest(payload).joinToString("") { "%02x".format(it) }
        log += "[adb] deploy 64KB: $ok, md5 ok: ${md5.trim() == local}"
        tmp.delete()
        log.forEach { Log.i(TAG, it) }
        log
    } catch (e: Exception) {
        Log.i(TAG, "[adb] ERROR: ${e.message}")
        listOf("[adb] ERROR: ${e.message}")
    }
}
