package com.rootmyvivo

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import java.io.File

/**
 * Автозапуск после перезагрузки — как в оригинальном DirtyFrag:
 * ресивер по умолчанию ВЫКЛЮЧЕН, включается тумблером в настройках
 * (PackageManager.setComponentEnabledSetting — состояние компонента и
 * есть хранилище настройки, как в DF). На BOOT/LOCKED_BOOT_COMPLETED:
 * ядро после перезагрузки чистое — перезапускаем эксплойт, бутстрап
 * снова поднимает late-load/userspace/adb (df.conf и ksud лежат в
 * device-protected папке и переживают перезагрузку).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RmvBoot"

        /** Компонент включён? (состояние настройки автозапуска). */
        fun isEnabled(ctx: Context): Boolean {
            val state = ctx.packageManager.getComponentEnabledSetting(
                ComponentName(ctx, BootReceiver::class.java),
            )
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        fun setEnabled(ctx: Context, enabled: Boolean) {
            ctx.packageManager.setComponentEnabledSetting(
                ComponentName(ctx, BootReceiver::class.java),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        // Рут уже поднят этой загрузкой (маркер бутстрапа живёт до ребута)
        if (File("/dev/dfm5").exists()) {
            Log.i(TAG, "boot: already rooted this boot, skipping")
            return
        }
        // Стейджинга нет — устройство никогда не рутилось, перезапускать
        // нечего (и до разблокировки CE-префы недоступны: решаем только
        // по файлам в DE-хранилище, как df.conf у бутстрапа)
        val deCtx = context.createDeviceProtectedStorageContext()
        val dp = deCtx.filesDir.parentFile ?: return
        if (!File(dp, "ksud").isFile || !File(dp, "df.conf").isFile) {
            Log.i(TAG, "boot: no staged ksud/df.conf, skipping")
            return
        }
        Log.i(TAG, "boot: $action — re-running DirtyFrag")

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "rootmyvivo:boot")
        wl.acquire(10 * 60 * 1000L)
        Thread({
            try {
                // Вариант нужен только стейджингу (источник ksud); на буте
                // всё уже застейджено — запускается натив, бутстрап делает
                // остальное (late-load + userspace + adb + soft-reboot)
                val runner = com.rootmyvivo.root.DirtyFragRunner(
                    com.rootmyvivo.root.KsuVariant.entries.first(),
                )
                val rc = runner.run(deCtx) { }
                Log.i(TAG, "boot: exploit rc=$rc")
            } catch (e: Exception) {
                Log.e(TAG, "boot: exploit failed", e)
            } finally {
                wl.release()
            }
        }, "rmv-boot").start()
    }
}
