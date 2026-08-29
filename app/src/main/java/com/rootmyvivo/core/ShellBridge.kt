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
 * UserService-интерфейс: выполняется ВНУТРИ процесса Shizuku (shell-домен, uid=2000).
 */

/**
 * Мост для выполнения команд в shell-домене (uid=2000, u:r:shell:s0).
 *
 * Режимы:
 * 1. ADB TCP 5555 (приоритет): после первого рута включаем persist.adb.tcp.port —
 *    приложение ходит само на localhost, Shizuku больше не нужен.
 * 2. Shizuku UserService: официальный API, запуск shell-команд через сервис в shell-домене.
 */
object ShellBridge {

    private const val TAG = "RootMyVivo"
    private const val ADB_HOST = "127.0.0.1"
    private const val ADB_PORT = 5555

    sealed class Transport {
        data object Adb : Transport()
        data object Shizuku : Transport()
        data object None : Transport()
    }

    fun availableTransport(): Transport = when {
        isAdbAlive() -> Transport.Adb
        isShizukuAlive() && isShizukuPermissionGranted() -> Transport.Shizuku
        else -> Transport.None
    }

    // ── ADB TCP (smart-socket, shell v2) ──

    private fun isAdbAlive(): Boolean = try {
        java.net.Socket().use { s ->
            s.soTimeout = 1500
            s.connect(java.net.InetSocketAddress(ADB_HOST, ADB_PORT), 1500)
            true
        }
    } catch (_: Exception) { false }

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

    // ── Shizuku UserService ──

    private var shellService: IShellService? = null

    private fun isShizukuAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) { false }

    fun isShizukuPermissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun requestShizukuPermission() {
        try {
            Shizuku.requestPermission(0)
        } catch (e: Exception) {
            Log.e(TAG, "shizuku requestPermission failed", e)
        }
    }

    /**
     * Подключить UserService Shizuku (процесс в shell-домене).
     */
    fun bindShizukuService(context: Context): Boolean {
        if (shellService != null) return true
        if (!isShizukuAlive() || !isShizukuPermissionGranted()) return false
        return try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context, ShellServiceImpl::class.java)
            ).daemon(false)
            var binder: IBinder? = null
            val latch = CountDownLatch(1)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
                    binder = b; latch.countDown()
                }
                override fun onServiceDisconnected(name: ComponentName?) { }
            }
            Shizuku.bindUserService(args, conn)
            latch.await(15, TimeUnit.SECONDS)
            shellService = binder?.let { IShellService.asInterface(it) }
            shellService != null
        } catch (e: Exception) {
            Log.e(TAG, "bindUserService failed", e)
            false
        }
    }

    private fun execShizuku(context: Context, command: String): Pair<Int, String> = try {
        val svc = shellService ?: if (!bindShizukuService(context)) {
            return Pair(-1, "Shizuku сервис не подключен")
        } else shellService ?: return Pair(-1, "Shizuku сервис не готов")
        val output = svc.exec(command)
        // Парсим "EXIT=N\n..." из ShellServiceImpl
        val exit = Regex("EXIT=(-?\\d+)").find(output)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val body = output.removePrefix("EXIT=$exit\n")
        Pair(exit, body)
    } catch (e: Exception) {
        Log.e(TAG, "shizuku exec failed", e)
        Pair(-1, e.message ?: "shizuku error")
    }

    // ── Общий API ──

    suspend fun exec(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val ctx = com.rootmyvivo.RmvApp.instance
        when {
            isAdbAlive() -> execAdb(command)
            isShizukuAlive() && isShizukuPermissionGranted() -> execShizuku(ctx, command)
            else -> Pair(-1, "нет транспорта: adb tcp:5555 недоступен, Shizuku не активен или нет разрешения")
        }
    }

    /** Включить постоянный adb tcp (persist.adb.tcp.port=5555) через root. */
    suspend fun enablePersistentAdb(): Boolean = withContext(Dispatchers.IO) {
        val (code, out) = exec("su -c 'setprop persist.adb.tcp.port 5555'")
        Log.i(TAG, "persist adb tcp: code=$code out=${out.take(100)}")
        code == 0
    }
}
