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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Мост для выполнения команд в shell-домене (uid=2000, u:r:shell:s0).
 *
 * Политика транспорта:
 * 1. До первого успешного root — только Shizuku UserService (AIDL + bindUserService).
 *    Открытый порт 5555 не считается достаточным (мог остаться с прошлых экспериментов).
 * 2. После зафиксированного root (firstRootCompleted) — ADB TCP 5555 через
 *    AdbWireClient (wire-протокол + собственный RSA-ключ, как LADB), Shizuku не нужен.
 */
object ShellBridge {

    private const val TAG = "RootMyVivo"
    private const val CHUNK_SIZE = 512 * 1024  // 512KB — безопасно для binder-буфера
    private const val PREFS = "rootmyvivo"
    private const val KEY_FIRST_ROOT_DONE = "firstRootCompleted"

    /**
     * Первый успешный root зафиксирован.
     * До этого ADB tcp не считается достаточным транспортом — только Shizuku.
     */
    fun isFirstRootCompleted(): Boolean = try {
        com.rootmyvivo.RmvApp.instance.getSharedPreferences(PREFS, 0)
            .getBoolean(KEY_FIRST_ROOT_DONE, false)
    } catch (_: Exception) { false }

    /** Вызывается после первого подтверждённого root (или если root уже активен). */
    fun markFirstRootCompleted() {
        try {
            com.rootmyvivo.RmvApp.instance.getSharedPreferences(PREFS, 0).edit()
                .putBoolean(KEY_FIRST_ROOT_DONE, true).apply()
        } catch (_: Exception) { }
    }

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

    // ─────────── ADB TCP (wire-протокол, как LADB) ───────────

    /** Быстрая проверка живости adb tcp: полный wire-handshake + echo (без диалога). */
    private fun adbAlive(): Boolean = try {
        val (code, out) = AdbWireClient.shell(
            com.rootmyvivo.RmvApp.instance, "echo RMV_OK",
            timeoutMs = 8000, allowDialog = false)
        code == 0 && out.contains("RMV_OK")
    } catch (_: Exception) { false }

    /** Выполнить команду через adb wire-клиент (allowDialog — ждать диалог авторизации). */
    private fun execAdb(command: String, timeoutSec: Int = 300, allowDialog: Boolean = false): Pair<Int, String> =
        try {
            AdbWireClient.shell(
                com.rootmyvivo.RmvApp.instance, command,
                timeoutMs = timeoutSec * 1000, allowDialog = allowDialog)
        } catch (e: Exception) {
            Log.e(TAG, "adb wire exec failed", e)
            Pair(-1, e.message ?: "adb error")
        }

    /**
     * Диагностика adb-канала (интент adb_selftest): wire-handshake + shell + деплой файла.
     * allowDialog=true — при неизвестном ключе adbd покажет системный диалог
     * «Разрешить отладку по USB?» (одноразово, ключ сохранится в adb_keys).
     */
    fun adbSelfTest(): List<String> = try {
        val ctx = com.rootmyvivo.RmvApp.instance
        val log = mutableListOf<String>()
        log += "[adb] публичный ключ: ${(AdbWireClient.androidPubkey(ctx) ?: "?").take(24)}…"
        AdbWireClient.connect(ctx, allowDialog = true, authTimeoutMs = 60_000)
        log += "[adb] CNXN: авторизация пройдена"
        val (code, out) = AdbWireClient.shell(ctx, "id; echo EXIT_CODE_OK")
        log += "[adb] shell: exit=$code out=${out.trim().take(60)}"
        // roundtrip файла
        val tmp = File(ctx.filesDir, "adb_selftest.bin")
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        tmp.writeBytes(payload)
        val ok = AdbWireClient.deployFile(ctx, tmp.absolutePath, "/data/local/tmp/rmv_selftest.bin")
        val (_, md5out) = AdbWireClient.shell(ctx, "md5sum /data/local/tmp/rmv_selftest.bin | cut -d' ' -f1; rm -f /data/local/tmp/rmv_selftest.bin")
        val localMd5 = java.security.MessageDigest.getInstance("MD5").digest(payload)
            .joinToString("") { "%02x".format(it) }
        log += "[adb] deploy 64KB: $ok, md5 совпал: ${md5out.trim() == localMd5}"
        tmp.delete()
        log.forEach { Log.i(TAG, it) }
        log
    } catch (e: Exception) {
        Log.i(TAG, "[adb] ОШИБКА: ${e.message}")
        listOf("[adb] ОШИБКА: ${e.message}")
    }

    // ─────────── Общий API ───────────

    /** Суспенд-версия для вызова из coroutine (сокеты — только IO-поток) */
    suspend fun availableTransportSuspending(): Transport = withContext(Dispatchers.IO) {
        availableTransportBlocking()
    }

    fun availableTransportBlocking(): Transport {
        // Политика: до первого успешного root — только Shizuku.
        // adb tcp 5555 достаточен только после зафиксированного root
        // (порт мог остаться открытым с прошлых экспериментов).
        if (isFirstRootCompleted() && adbAlive()) {
            return Transport.Adb
        }
        return when {
            shizukuBinderAlive && isShizukuPermissionGranted() -> Transport.Shizuku
            shizukuBinderAlive -> Transport.ShizukuNeedsPermission
            else -> Transport.None
        }
    }

    suspend fun exec(command: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val ctx = com.rootmyvivo.RmvApp.instance
        when {
            isFirstRootCompleted() && adbAlive() ->
                execAdb(command, allowDialog = true)
            shizukuBinderAlive && isShizukuPermissionGranted() -> execShizuku(ctx, command)
            else -> Pair(-1, "нет транспорта: до первого root требуется запущенный Shizuku с разрешением")
        }
    }

    /** Деплой preload.so в /data/local/tmp: exec:cat (adb wire) или AIDL-чанки (Shizuku). */
    suspend fun deployFile(localPath: String, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val ctx = com.rootmyvivo.RmvApp.instance
        when {
            isFirstRootCompleted() && adbAlive() ->
                AdbWireClient.deployFile(ctx, localPath, remotePath)
            shizukuBinderAlive && isShizukuPermissionGranted() ->
                deployFileShizuku(ctx, localPath, remotePath)
            else -> false
        }
    }

    /**
     * Пост-root закрепление: включить постоянный adb tcp и записать свой ключ
     * в /data/misc/adb/adb_keys — после любой перезагрузки авторизация бесшовная.
     */
    suspend fun enablePersistentAdb(): Boolean = withContext(Dispatchers.IO) {
        val ctx = com.rootmyvivo.RmvApp.instance
        val (code, out) = exec("su -c 'setprop persist.adb.tcp.port 5555'")
        Log.i(TAG, "persist adb tcp: code=$code out=${out.take(100)}")

        // Свой публичный ключ в adb_keys (через su) — тихая авторизация после ребутов
        val pub = AdbWireClient.androidPubkey(ctx)
        if (pub != null) {
            val (kc, kout) = exec("su -c 'echo \"$pub rootmyvivo\" >> /data/misc/adb/adb_keys'")
            Log.i(TAG, "adb_keys install: code=$kc out=${kout.take(80)}")
        }
        code == 0
    }
}
