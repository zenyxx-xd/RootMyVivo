package com.rootmyvivo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.rootmyvivo.data.Prefs
import com.rootmyvivo.root.KsuVariant
import com.rootmyvivo.shell.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Авто-восстановление рута после полной перезагрузки.
 *
 * На заблокированном загрузчике модуль нельзя встроить в загрузку системы —
 * единственная рабочая модель (так делают все тулы экосистемы GhostLock,
 * включая ghostlock-anchor) — перезапустить эксплойт после каждого ребута.
 * Всё нужное переживает перезагрузку: пейлоад и ksud в /data/local/tmp/rmv,
 * пропатченный модуль в /data/adb/rmv, adb-порт закреплён
 * (persist.adb.tcp.port + ключ в adb_keys).
 */
class BootRootService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification(getString(R.string.notif_boot_restore_running)))
        scope.launch {
            try {
                restore()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "boot restore failed", e)
                notifyResult(ok = false)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun restore() {
        val prefs = Prefs(this)

        // Бутлуп-гард: если устройство перезагрузилось вскоре после прошлой
        // попытки — вероятно, эксплойт уронил ядро. Автоматически не
        // эксплойтим, ждём ручного запуска из приложения.
        val sinceLast = System.currentTimeMillis() - prefs.bootRestoreLastAttempt
        if (sinceLast in 1 until BOOTLOOP_WINDOW_MS) {
            notifyResult(ok = false)
            return
        }

        // Дать системе подняться и ОСТЫТЬ. Ночные прогоны на PD2520:
        // pselect-маршрут CFI стабильно промахивается на «горячем» ядре
        // первых минут после загрузки (попытки 1-3 почти всегда fail,
        // успех приходит на 3-6-й, лучше всего — после ~8 мин uptime).
        // 20 секунд было слишком рано; 8 минут — эмпирический оптимум
        // между надёжностью и ожиданием пользователя.
        delay(BOOT_SETTLE_MS)

        // Рут на месте (например, это soft reboot — модуль жив, ядро то же)
        // — восстанавливать нечего, тихо выходим без уведомлений
        if (rootActive()) {
            return
        }

        // Adb-транспорт (закреплённый порт) может подниматься дольше загрузки
        var adbUp = false
        for (i in 0 until 30) {
            val (code, out) = Transport.exec(this, "echo RMV_OK", timeoutSec = 10)
            if (code == 0 && out.contains("RMV_OK")) {
                adbUp = true
                break
            }
            delay(3000)
        }
        if (!adbUp) {
            notifyResult(ok = false)
            return
        }

        // Прогон эксплойта с ретраями: один запуск preload делает до
        // N попыток внутри (RMV_ATTEMPTS из каталога), но на свежем ядре
        // иногда не хватает и их — повторяем запуск целиком с паузой.
        prefs.bootRestoreLastAttempt = System.currentTimeMillis()
        var rooted = false
        for (round in 1..EXPLOIT_ROUNDS) {
            if (!launchExploit()) {
                notifyResult(ok = false)
                return
            }
            // Внутренние попытки идут до ~10 мин; опрашиваем su
            for (i in 0 until 200) {
                if (rootActive()) {
                    rooted = true
                    break
                }
                delay(3000)
            }
            if (rooted) break
            // ретрай-цикл с растущей паузой: слэбу нужно время «остыть»
            delay(round * 60_000L)
        }
        if (!rooted) {
            notifyResult(ok = false)
            return
        }

        notifyResult(ok = loadCachedModule())
    }

    /** Пейлоад на месте? Перезапускаем эксплойт в фоне, как основной флоу. */
    private suspend fun launchExploit(): Boolean {
        val remoteDir = "/data/local/tmp/rmv"
        val remotePreload = "$remoteDir/preload.so"
        Transport.exec(
            this,
            "mkdir -p $remoteDir && rm -f $remoteDir/DONE $remoteDir/live.log",
            timeoutSec = 15,
        )
        // пейлоад мог потеряться (чистка /data/local/tmp) — перекладываем из app-хранилища
        if (!remoteFileExists(remotePreload)) {
            val local = File(filesDir, "payloads/preload.so")
            if (!local.exists() || !Transport.deploy(this, local.absolutePath, remotePreload)) {
                return false
            }
        }
        Transport.exec(
            this,
            "cd $remoteDir && (LD_PRELOAD=$remotePreload /system/bin/true > $remoteDir/live.log 2>&1 &)",
            timeoutSec = 15,
        )
        return true
    }

    private suspend fun remoteFileExists(path: String): Boolean {
        val (code, out) = Transport.exec(this, "[ -f $path ] && echo RMV_YES", timeoutSec = 10)
        return code == 0 && out.contains("RMV_YES")
    }

    /**
     * Модуль из кэша: /data/adb/rmv (писал setupPersistence при руте), при
     * отсутствии — деплой из filesDir приложения. ksud аналогично.
     */
    private suspend fun loadCachedModule(): Boolean {
        val (_, mods) = Transport.exec(this, "grep -i kernelsu /proc/modules 2>/dev/null")
        if (mods.isNotBlank()) return true

        val prefs = Prefs(this)
        val koLocal = File(filesDir, "payloads/kernelsu_${prefs.selectedKsu}.ko")
        var ko = "/data/adb/rmv/kernelsu.ko"
        if (!remoteFileExists(ko)) {
            if (!koLocal.exists() ||
                !Transport.deploy(this, koLocal.absolutePath, "/data/local/tmp/rmv/kernelsu.ko")
            ) {
                return false
            }
            ko = "/data/local/tmp/rmv/kernelsu.ko"
        }
        val pkg = prefs.managerPackage.ifEmpty {
            KsuVariant.byId(prefs.selectedKsu).packageName
        }
        // ksud'ы по списку: из установленного менеджера (свежий, с kallsyms-
        // insmod), затем кэшированные копии
        val ksudPaths = buildList {
            com.rootmyvivo.root.KsuInstaller
                .findManagerKsud(this@BootRootService, listOf(pkg, KsuVariant.byId(prefs.selectedKsu).packageName))
                ?.let { add(it) }
            if (remoteFileExists("/data/adb/rmv/ksud")) add("/data/adb/rmv/ksud")
            if (remoteFileExists("/data/local/tmp/rmv/ksud")) add("/data/local/tmp/rmv/ksud")
        }
        if (ksudPaths.isEmpty()) return false
        Transport.exec(this, "chmod 755 ${ksudPaths.joinToString(" ")}", timeoutSec = 15)
        com.rootmyvivo.root.KsuInstaller.loadModule(this, ko, pkg, ksudPaths)

        val (_, mods2) = Transport.exec(this, "grep -i kernelsu /proc/modules 2>/dev/null")
        return mods2.isNotBlank()
    }

    /** Рут жив: демон эксплойта отвечает, KSU-модуль в ядре или su в системе. */
    private fun rootActive(): Boolean {
        val su = try {
            val (code, out) = Transport.suLocal("id", timeoutSec = 5)
            code == 0 && out.contains("uid=0")
        } catch (_: Exception) {
            false
        }
        if (su) return true
        if (File("/system/bin/su").exists()) return true
        return try {
            File("/proc/modules").readText().contains("kernelsu", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun notifyResult(ok: Boolean) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                getString(
                    if (ok) R.string.notif_boot_restore_ok_title else R.string.notif_boot_restore_fail_title,
                ),
            )
            .setContentText(
                getString(
                    if (ok) R.string.notif_boot_restore_ok_text else R.string.notif_boot_restore_fail_text,
                ),
            )
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_RESULT_ID, notification) }
        // фоновое уведомление процесса снимаем
        nm.cancel(NOTIF_ID)
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val TAG = "BootRoot"
        private const val CHANNEL_ID = "root_status"
        private const val NOTIF_ID = 1002
        private const val NOTIF_RESULT_ID = 1003

        /** Окно бутлуп-гарда: ребут раньше этого срока после попытки = подозрение на панику. */
        private const val BOOTLOOP_WINDOW_MS = 10 * 60 * 1000L

        /** Пауза после загрузки до эксплойта: см. комментарий в restore(). */
        private const val BOOT_SETTLE_MS = 8 * 60 * 1000L

        /** Сколько раз запускать эксплойт целиком (внутри — ещё N попыток preload). */
        private const val EXPLOIT_ROUNDS = 2

        fun start(ctx: Context) {
            try {
                ctx.startForegroundService(Intent(ctx, BootRootService::class.java))
            } catch (_: Exception) {
                // FGS из BOOT_COMPLETED может быть ограничен — фон тоже сойдёт
                runCatching { ctx.startService(Intent(ctx, BootRootService::class.java)) }
            }
        }
    }
}
