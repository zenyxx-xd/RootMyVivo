package com.rootmyvivo.core

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Мост для выполнения команд в shell-домене (uid=2000, u:r:shell:s0).
 *
 * Режимы (по приоритету):
 * 1. ADB TCP 5555: после первого рута включаем persist.adb.tcp.port —
 *    приложение ходит само на localhost, Shizuku больше не нужен.
 * 2. Shizuku UserService (каноническая интеграция, AIDL + bindUserService).
 */
object ShellBridge {

    private const val TAG = "RootMyVivo"
    private const val ADB_HOST = "127.0.0.1"
    private const val ADB_PORT = 5555
    private const val CHUNK_SIZE = 512 * 1024  // 512KB — безопасно для binder-буфера

    sealed class Transport {
        data object Adb : Transport()
        data object Shizuku : Transport()
        /** Shizuku запущен, но разрешение не выдано — можно попросить */
        data object ShizukuNeedsPermission : Transport()
        data object None : Transport()
    }

    // ─────────── Shizuku: binder lifecycle (канонический паттерн) ───────────

    /** Текущее состояние binder Shizuku (обновляется sticky-листенерами) */
    @Volatile
    var shizukuBinderAlive: Boolean = false
        private set

    private var binderReceivedListener: Shizuku.OnBinderReceivedListener? = null
    private var binderDeadListener: Shizuku.OnBinderDeadListener? = null
    private var permissionListener: Shizuku.OnRequestPermissionResultListener? = null
    private var listenersRegistered = false
    private var onPermissionGranted: (() -> Unit)? = null

    /** Зарегистрировать листенеры (вызывать один раз, например из Application) */
    fun initShizukuListeners(context: Context) {
        if (listenersRegistered) return
        listenersRegistered = true
        try {
            val receivedListener = Shizuku.OnBinderReceivedListener {
                shizukuBinderAlive = true
                Log.i(TAG, "Shizuku binder received")
            }
            val deadListener = Shizuku.OnBinderDeadListener {
                shizukuBinderAlive = false
                shellService = null
                Log.i(TAG, "Shizuku binder dead")
            }
            val permListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Shizuku permission granted")
                    onPermissionGranted?.invoke()
                } else {
                    Log.i(TAG, "Shizuku permission denied")
                }
            }
            binderReceivedListener = receivedListener
            binderDeadListener = deadListener
            permissionListener = permListener
            Shizuku.addBinderReceivedListenerSticky(receivedListener)
            Shizuku.addBinderDeadListener(deadListener)
            Shizuku.addRequestPermissionResultListener(permListener)
        } catch (e: Exception) {
            Log.e(TAG, "initShizukuListeners failed", e)
        }
    }

    // ─────────── Shizuku: разрешения (канонический паттерн из demo) ───────────

    fun isShizukuAlive(): Boolean = shizukuBinderAlive

    fun isShizukuPermissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    /**
     * Запросить разрешение Shizuku. onGranted вызовется когда пользователь одобрит.
     * Канонический паттерн: checkSelfPermission → shouldShowRequestPermissionRationale → requestPermission.
     */
    fun requestShizukuPermission(requestCode: Int = 0, onGranted: (() -> Unit)? = null): Boolean {
        onPermissionGranted = onGranted
        return try {
            if (Shizuku.isPreV11()) {
                Log.w(TAG, "Shizuku pre-V11 — не поддерживается")
                return false
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                onGranted?.invoke()
                true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Log.w(TAG, "Пользователь отказал навсегда — нужно в настройках Shizuku")
                false
            } else {
                Shizuku.requestPermission(requestCode)
                false
            }
        } catch (e: Throwable) {
            // "binder haven't been received" и прочее
            Log.e(TAG, "requestPermission failed", e)
            false
        }
    }

    // ─────────── Shizuku UserService (AIDL) ───────────

    @Volatile
    private var shellService: IShellService? = null

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context, ShellServiceImpl::class.java)
        )
            .daemon(false)
            .processNameSuffix("service")
            .version(1)

    /** Подключить UserService Shizuku (процесс в shell-домене). */
    fun bindShizukuService(context: Context): Boolean {
        if (shellService != null) return true
        if (!shizukuBinderAlive || !isShizukuPermissionGranted()) return false
        return try {
            var binder: IBinder? = null
            val latch = CountDownLatch(1)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
                    binder = b
                    latch.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName?) {
                    shellService = null
                }
            }
            Shizuku.bindUserService(userServiceArgs(context), conn)
            latch.await(15, TimeUnit.SECONDS)
            val b = binder
            shellService = if (b != null && b.pingBinder()) IShellService.Stub.asInterface(b) else null
            shellService != null
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService failed", e)
            false
        }
    }

    private fun execShizuku(context: Context, command: String): Pair<Int, String> = try {
        val svc = shellService ?: bindShizukuService(context)?.let { shellService }
        ?: return Pair(-1, "Shizuku сервис не подключен")
        val output = svc.exec(command)
        val exit = Regex("EXIT=(-?\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val body = output.removePrefix("EXIT=$exit\n")
        Pair(exit, body)
    } catch (e: Exception) {
        Log.e(TAG, "shizuku exec failed", e)
        shellService = null  // сервис мог умереть
        Pair(-1, e.message ?: "shizuku error")
    }

    /**
     * Передать файл в /data/local/tmp через AIDL (чанки byte[]).
     * Канонический способ вместо base64-чанков в shell-командах.
     */
    private fun deployFileShizuku(context: Context, localPath: String, remotePath: String): Boolean {
        return try {
            val svc = shellService ?: bindShizukuService(context)?.let { shellService }
            ?: return false
            val data = java.io.File(localPath).readBytes()
            var offset = 0L
            while (offset < data.size) {
                val len = minOf(CHUNK_SIZE, data.size - offset.toInt())
                val chunk = data.copyOfRange(offset.toInt(), offset.toInt() + len)
                if (!svc.writeFileChunk(remotePath, offset, chunk)) return false
                offset += len
            }
            // chmod для LD_PRELOAD
            svc.exec("chmod 644 $remotePath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "deployFile failed", e)
            false
        }
    }

    // ─────────── ADB TCP (smart-socket, shell v2) ───────────

    private fun execAdb(command: String, timeoutSec: Int = 300): Pair<Int, String> = try {
        java.net.Socket(ADB_HOST, ADB_PORT).use { s ->
            s.soTimeout = timeoutSec * 1000
            s.tcpNoDelay = true
            val out = s.getOutputStream()
            val inp = s.getInputStream()

            out.write("shell,v2,$command\n".toByteArray(Charsets.UTF_8))
            out.flush()

            val buf = ByteArray(65536)
            val sb = StringBuilder()
            var exitCode = -1
            read@ while (true) {
                val n = inp.read(buf)
                if (n < 0) break@read
                var i = 0
                while (i < n) {
                    val id = buf[i].toInt()
                    when (id) {
                        2, 3 -> {
                            if (i + 5 > n) { i = n; continue }
                            val len = ((buf[i+1].toInt() and 0xFF) shl 24) or
                                    ((buf[i+2].toInt() and 0xFF) shl 16) or
                                    ((buf[i+3].toInt() and 0xFF) shl 8) or
                                    (buf[i+4].toInt() and 0xFF)
                            val avail = minOf(len, n - i - 5)
                            if (avail > 0) sb.append(String(buf, i + 5, avail, Charsets.UTF_8))
                            i += 5 + len
                        }
                        1 -> { if (i + 1 < n) exitCode = buf[i+1].toInt(); i += 2 }
                        else -> i = n
                    }
                }
            }
            Pair(if (exitCode >= 0) exitCode else 0, sb.toString())
        }
    } catch (e: Exception) {
        Log.e(TAG, "adb exec failed", e)
        Pair(-1, e.message ?: "adb error")
    }

    // ─────────── Общий API ───────────

    /** Суспенд-версия для вызова из coroutine (сокеты — только IO-поток) */
    suspend fun availableTransportSuspending(): Transport = withContext(Dispatchers.IO) {
        availableTransportBlocking()
    }

    fun availableTransportBlocking(): Transport {
        // 1. ADB TCP: реальная команда надёжнее простого connect
        if (execAdb("echo RMV_OK", timeoutSec = 5).second.contains("RMV_OK")) {
            return Transport.Adb
        }
        // 2. Shizuku с разрешением
        if (shizukuBinderAlive && isShizukuPermissionGranted()) {
            return Transport.Shizuku
        }
        // 3. Shizuku жив, но разрешение не выдано
        if (shizukuBinderAlive) {
            return Transport.ShizukuNeedsPermission
        }
        return Transport.None
    }

    suspend fun exec(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val ctx = com.rootmyvivo.RmvApp.instance
        when {
            execAdb("echo RMV_OK", timeoutSec = 5).second.contains("RMV_OK") -> execAdb(command)
            shizukuBinderAlive && isShizukuPermissionGranted() -> execShizuku(ctx, command)
            else -> Pair(-1, "нет транспорта: adb tcp:5555 недоступен, Shizuku не активен или нет разрешения")
        }
    }

    /** Деплой preload.so в /data/local/tmp. Через AIDL (Shizuku) или dd (adb). */
    suspend fun deployFile(localPath: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val ctx = com.rootmyvivo.RmvApp.instance
        when {
            execAdb("echo RMV_OK", timeoutSec = 5).second.contains("RMV_OK") -> {
                // adb: base64 через stdin умнее — используем dd c base64 строкой
                // Файл ~180KB — одна base64-команда на 240KB — в лимите аргументов (2MB)
                val b64 = android.util.Base64.encodeToString(
                    java.io.File(localPath).readBytes(), android.util.Base64.NO_WRAP)
                val (code, _) = execAdb(
                    "echo '$b64' | base64 -d > $remotePath && chmod 644 $remotePath && echo DEPLOY_OK"
                )
                code == 0
            }
            shizukuBinderAlive && isShizukuPermissionGranted() ->
                deployFileShizuku(ctx, localPath, remotePath)
            else -> false
        }
    }

    /** Включить постоянный adb tcp (persist.adb.tcp.port=5555) через root. */
    suspend fun enablePersistentAdb(): Boolean = withContext(Dispatchers.IO) {
        val (code, out) = exec("su -c 'setprop persist.adb.tcp.port 5555'")
        Log.i(TAG, "persist adb tcp: code=$code out=${out.take(100)}")
        code == 0
    }
}
