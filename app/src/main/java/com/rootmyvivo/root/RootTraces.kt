package com.rootmyvivo.root

import android.content.Context
import com.rootmyvivo.shell.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Полная зачистка следов процесса: временные файлы в filesDir/rmv
 * (пейлоад, логи, DONE) — локально, без root; root-следы (клиент su,
 * сокет, ota-стейджинг, кэш модуля и скрипт service.d) — через su-демона.
 * Античиты и банки ищут именно эти пути.
 *
 * Удаляем только когда KernelSU реально работает: shell-домен отвечает
 * `uid=0` на PATH-`su` — это может дать только живой ksud (наш клиент лежит
 * вне PATH). Пока root держится лишь на клиенте демона, удаление срезало бы
 * единственный канал root.
 *
 * Восстановление после ребута пересобирает всё заново — ksud
 * распаковывается из base.apk менеджера (KsuInstaller.unpackManagerKsud),
 * а модуль и preload лежат в приватном хранилище приложения
 * (filesDir/payloads) и деплоятся при необходимости.
 */
object RootTraces {

    enum class Outcome { CLEANED, NO_KSUD, FAILED }

    private const val GATE_CMD = "su -c id 2>/dev/null"

    private const val CLEAN_CMD =
        "su -c 'rm -f /data/local/tmp/su /data/local/tmp/su_daemon.log /data/local/tmp/temp_su.sock /data/local/tmp/live.log /data/adb/su 2>/dev/null; " +
            "rm -rf /data/local/tmp/ota /data/local/tmp/rmv 2>/dev/null; " +
            "rm -rf /data/adb/rmv 2>/dev/null; " +
            "rm -f /data/adb/service.d/rmv-persist.sh 2>/dev/null; echo RMV_CLEAN'"

    suspend fun clean(ctx: Context): Outcome = withContext(Dispatchers.IO) {
        // Локальные следы — без root, напрямую файлами
        runCatching { java.io.File(ctx.filesDir, "rmv").deleteRecursively() }
        val (_, gate) = Transport.exec(ctx, GATE_CMD, timeoutSec = 20)
        if (!gate.contains("uid=0")) return@withContext Outcome.NO_KSUD
        val (code, out) = Transport.exec(ctx, CLEAN_CMD, timeoutSec = 30)
        if (code == 0 && out.contains("RMV_CLEAN")) Outcome.CLEANED else Outcome.FAILED
    }
}
