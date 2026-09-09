package com.rootmyvivo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rootmyvivo.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * После перезагрузки: проверяет root и присылает уведомление.
 *  - root активен (su / модуль KernelSU) → «Root получен»
 *  - root сброшен (временный рут не переживает ребут) → «Root сброшен, открой приложение»
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (!prefs.firstRootDone) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (prefs.bootRestoreEnabled) {
                    // Сервис сам проверит рут и тихо выйдет, если он жив
                    // (soft reboot — модуль не умирает): восстановление и
                    // уведомления только когда рут реально исчез
                    BootRootService.start(context)
                } else {
                    val active = isRootActive()
                    withContext(Dispatchers.Main) {
                        showNotification(context, active)
                    }
                }
            } finally {
                result.finish()
            }
        }
    }

    private fun isRootActive(): Boolean {
        // su-демон GhostLock
        val suWorks = try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "true")).waitFor() == 0
        } catch (_: Exception) {
            false
        }
        if (suWorks) return true
        // модуль KernelSU, поднятый persistence-скриптом
        return try {
            File("/proc/modules").readText().contains("kernelsu", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    private fun showNotification(context: Context, rootActive: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        nm.createNotificationChannel(channel)

        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title: Int
        val text: Int
        if (rootActive) {
            title = R.string.notif_root_active_title
            text = R.string.notif_root_active_text
        } else {
            title = R.string.notif_root_lost_title
            text = R.string.notif_root_lost_text
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        // Без разрешения (Android 13+) уведомление просто не покажется — не критично
        runCatching { nm.notify(NOTIF_ID, notification) }
    }

    companion object {
        private const val CHANNEL_ID = "root_status"
        private const val NOTIF_ID = 1001
    }
}
