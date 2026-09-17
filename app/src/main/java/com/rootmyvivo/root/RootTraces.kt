package com.rootmyvivo.root

import android.content.Context
import com.rootmyvivo.shell.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Временные su-файлы эксплойта: клиент /data/local/tmp/su, его сокет и лог,
 * стейджинг-папка ota и /data/adb/su. Их ищут античиты и банковские приложения.
 *
 * Удаляем только когда KernelSU реально работает: shell-домен отвечает
 * `uid=0` на PATH-`su` — это может дать только живой ksud (наш клиент лежит
 * вне PATH). Пока root держится лишь на клиенте демона, удаление срезало бы
 * единственный канал root.
 *
 * Не трогаем: /data/adb/rmv и /data/adb/service.d (закрепление KSU после
 * перезагрузки) и /data/local/tmp/rmv/ksud (пере-инжекция модуля).
 */
object RootTraces {

    enum class Outcome { CLEANED, NO_KSUD, FAILED }

    private const val GATE_CMD = "su -c id 2>/dev/null"

    private const val CLEAN_CMD =
        "su -c 'rm -f /data/local/tmp/su /data/adb/su /data/local/tmp/live.log /data/local/tmp/temp_su.sock 2>/dev/null; " +
            "rm -rf /data/local/tmp/ota 2>/dev/null; echo RMV_CLEAN'"

    suspend fun clean(ctx: Context): Outcome = withContext(Dispatchers.IO) {
        val (_, gate) = Transport.exec(ctx, GATE_CMD, timeoutSec = 20)
        if (!gate.contains("uid=0")) return@withContext Outcome.NO_KSUD
        val (code, out) = Transport.exec(ctx, CLEAN_CMD, timeoutSec = 30)
        if (code == 0 && out.contains("RMV_CLEAN")) Outcome.CLEANED else Outcome.FAILED
    }
}
