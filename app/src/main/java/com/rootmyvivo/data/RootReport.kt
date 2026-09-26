package com.rootmyvivo.data

import android.util.Log
import com.rootmyvivo.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/**
 * Отчёт «пейлоад валидирован»: когда пользователь реально получает root
 * через experimental-билд, приложение отправляет отчёт в payloads-репо
 * (GitHub repository_dispatch). Workflow в репо проверяет HMAC-подпись
 * из нативной части эксплойта и переводит билд experimental → ready.
 *
 * Антиспуфинг:
 *  — доказательство — строка `RMV-PROOF <hmac>` из live-лога эксплойта;
 *    HMAC-ключ зашит в preload.so, функция вызывается только после
 *    фактического uid==0 в root-стадии; сообщение = hmac(nonce||uname||
 *    payload_id), нонс генерируется приложением на каждый запуск;
 *  — токен отправки (BuildConfig.RMV_DISPATCH_TOKEN) — fine-grained PAT
 *    с правом ТОЛЬКО Actions:write на payloads-репо: украсть его из APK
 *    можно, но прямыми коммитами он не пишет — максимум спам dispatch,
 *    который отбрасывается проверкой HMAC в workflow.
 *
 * Отчёт никогда не мешает основному флоу: любые ошибки молча логируются.
 */
object RootReport {
    private const val TAG = "RootReport"
    private const val ENDPOINT =
        "https://api.github.com/repos/zenyxx-xd/RootMyVivo-Payloads/dispatches"
    private const val EVENT = "payload-validated"
    private const val PROOF_PREFIX = "RMV-PROOF "

    /** Отчёт включён только если в сборку вшит dispatch-токен. */
    val enabled: Boolean get() = BuildConfig.RMV_DISPATCH_TOKEN.isNotEmpty()

    /** Нонс для текущего запуска эксплойта (hex, 32 символа). */
    fun newNonce(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    /** Достаёт доказательство из хвоста live-лога эксплойта. */
    fun extractProof(logTail: String): String? {
        val idx = logTail.lastIndexOf(PROOF_PREFIX)
        if (idx < 0) return null
        val hex = logTail.substring(idx + PROOF_PREFIX.length)
            .trim().lineSequence().firstOrNull() ?: return null
        return hex.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
    }

    /**
     * Отправка отчёта. Вызывается один раз после успешного рута через
     * билд из каталога со статусом experimental. Всё в try/catch —
     * сбой отправки не должен портить пользователю момент получения рута.
     */
    fun reportValidated(
        payloadId: String,
        nonce: String,
        proofHex: String,
        kernel: String,
        model: String,
    ) {
        if (!enabled) {
            Log.i(TAG, "validation report skipped: no dispatch token in build")
            return
        }
        try {
            val code = doReport(payloadId, nonce, proofHex, kernel, model)
            Log.i(TAG, "validation report http=$code payload=$payloadId")
        } catch (t: Throwable) {
            Log.w(TAG, "validation report failed (ignored): ${t.message}")
        }
    }

    private fun doReport(
        payloadId: String,
        nonce: String,
        proofHex: String,
        kernel: String,
        model: String,
    ): Int {
        val clientPayload = JSONObject().apply {
            put("payload_id", payloadId)
            put("nonce", nonce)
            put("proof", proofHex)
            put("kernel", kernel.take(128))
            put("model", model.take(64))
            put("app", BuildConfig.VERSION_NAME)
        }
        val body = JSONObject().apply {
            put("event_type", EVENT)
            put("client_payload", clientPayload)
        }
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer ${BuildConfig.RMV_DISPATCH_TOKEN}")
            setRequestProperty("User-Agent", "RootMyVivoNeo/${BuildConfig.VERSION_NAME}")
        }
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        conn.disconnect()
        return code
    }
}
