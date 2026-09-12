package com.rootmyvivo.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class FileEntry(
    val url: String,
    val sha256: String?,
    val size: Long,
    /** Зеркала: пробуются по очереди, если основной URL недоступен
     *  (GitHub release assets живут на release-assets.githubusercontent.com,
     *  который у части пользователей заблокирован, когда сам GitHub работает). */
    val mirrors: List<String> = emptyList(),
)

data class PayloadEntry(
    val id: String,
    val displayName: String,
    val models: List<String>,
    val marketNames: List<String>,
    val kernelVersions: List<String>,
    val enabled: Boolean,
    val verifiedBy: String?,
    val files: Map<String, FileEntry>,
    /** Переменные окружения эксплойта (RMV_ATTEMPTS, RMV_RETRY_DELAY, …) */
    val env: Map<String, String> = emptyMap(),
)

data class PayloadCatalog(val payloads: List<PayloadEntry>)

/**
 * Каталог пейлоадов: приложение не содержит эксплойтов — скачивает их по описанию.
 * Кэш: 24 ч свежести; при ошибке сети используется кэш любого возраста.
 */
class Catalog(var url: String = DEFAULT_URL) {

    companion object {
        const val DEFAULT_URL =
            "https://raw.githubusercontent.com/zenyxx-xd/RootMyVivo-Payloads/main/support/targets-vivo.json"
        private const val TAG = "NeoCatalog"
        private const val CACHE_FILE = "catalog.json"
        private const val CACHE_FRESH_MS = 24 * 3600_000L
        private var cacheDir: File? = null

        fun initCache(dir: File) {
            cacheDir = dir
        }

        private fun cacheFile(): File? = cacheDir?.let { File(it, CACHE_FILE) }
    }

    suspend fun fetch(): Result<PayloadCatalog> = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(url, timeout = 15_000)
            if (body == null) {
                val c = cached()
                if (c != null) {
                    Result.success(c)
                } else {
                    Result.failure(IOException("network error"))
                }
            } else {
                cacheFile()?.writeText(body)
                Result.success(parse(body))
            }
        } catch (e: Exception) {
            Log.w(TAG, "catalog fetch failed: ${e.message}")
            cached(ignoreAge = true)?.let { Result.success(it) } ?: Result.failure(e)
        }
    }

    private fun cached(ignoreAge: Boolean = false): PayloadCatalog? {
        return try {
            val f = cacheFile()
            if (f == null || !f.exists()) return null
            if (!ignoreAge && System.currentTimeMillis() - f.lastModified() > CACHE_FRESH_MS) return null
            parse(f.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun findPayload(catalog: PayloadCatalog, info: DeviceInfo): PayloadEntry? {
        val byModel = catalog.payloads.filter {
            it.enabled && (it.models.contains(info.model) || it.marketNames.contains(info.marketName))
        }
        // сначала точное совпадение модели + ядро (полная строка uname, если
        // запись задаёт полный паттерн), потом модель без проверки ядра
        return byModel.firstOrNull { matchesKernel(it.kernelVersions, info.kernelShort, info.kernel) }
            ?: byModel.firstOrNull()
    }

    /**
     * Паттерны ядра: короткий «6.6.89» сравнивается с короткой версией,
     * полный «6.6.89-android15-8-gb57af212129c» — подстрокой uname release
     * (различает сборки ядра одной модели: Neo10 Pro gf2… vs b57…),
     * суффикс «.*» — префикс полной строки.
     */
    private fun matchesKernel(supported: List<String>, actualShort: String, actualFull: String): Boolean {
        if (supported.isEmpty()) return true
        return supported.any { pattern ->
            when {
                pattern.endsWith(".*") ->
                    actualFull.startsWith(pattern.removeSuffix("*")) ||
                        actualShort.startsWith(pattern.removeSuffix("*"))
                pattern.count { it == '.' } > 2 -> actualFull.contains(pattern)
                else -> actualShort == pattern
            }
        }
    }

    /** Скачивание файла пейлоада: основной URL, затем зеркала (если заданы);
     *  редиректы, проверка размера и SHA-256, атомарная запись. */
    suspend fun downloadFile(
        entry: FileEntry,
        dest: File,
        onProgress: suspend (read: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val urls = listOf(entry.url) + entry.mirrors
        var lastError: Throwable? = null
        for (u in urls) {
            val res = downloadFrom(u, entry, dest, onProgress)
            if (res.isSuccess) return@withContext res
            lastError = res.exceptionOrNull()
            Log.w(TAG, "download ${dest.name} from ${u.substringBefore('/' + dest.name)} failed: ${lastError?.message}")
        }
        Result.failure(lastError ?: IOException("all sources failed"))
    }

    private suspend fun downloadFrom(
        url: String,
        entry: FileEntry,
        dest: File,
        onProgress: suspend (read: Long, total: Long) -> Unit,
    ): Result<Unit> = try {
        val tmp = File(dest.absolutePath + ".part")
        dest.parentFile?.mkdirs()
        tmp.delete()

        var conn = open(url)
        var code = conn.responseCode
        var hops = 0
        while (hops < 5 && code in 301..308) {
            val loc = conn.getHeaderField("Location")
                ?: throw IOException("HTTP $code without Location")
            conn.disconnect()
            conn = open(URL(URL(url), loc).toString())
            code = conn.responseCode
            hops++
        }
        if (code !in 200..299) throw IOException("HTTP $code")

        val total = conn.contentLengthLong.takeIf { it > 0 } ?: entry.size
        conn.inputStream.use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(65536)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    read += n
                    onProgress(read, total)
                }
            }
        }

        if (tmp.length() == 0L) throw IOException("empty response")
        if (entry.size > 0L && tmp.length() != entry.size) {
            throw IOException("incomplete: ${tmp.length()} of ${entry.size} bytes")
        }
        entry.sha256?.let { expected ->
            val actual = sha256(tmp)
            if (!actual.equals(expected, ignoreCase = true)) throw IOException("sha256 mismatch: $actual")
        }
        if (!tmp.renameTo(dest)) throw IOException("rename failed")
        dest.setReadable(true, false)
        Result.success(Unit)
    } catch (e: Exception) {
        File(dest.absolutePath + ".part").delete()
        Result.failure(e)
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = 30_000
        c.readTimeout = 120_000
        c.setRequestProperty("User-Agent", "RootMyVivoNeo/${com.rootmyvivo.BuildConfig.VERSION_NAME}")
        c.setRequestProperty("Accept", "application/octet-stream")
        return c
    }

    private fun httpGet(url: String, timeout: Int): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = timeout
            c.readTimeout = 30_000
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally {
            c.disconnect()
        }
    }

    // ── Парсинг (org.json, без зависимостей) ──

    fun parse(body: String): PayloadCatalog {
        val arr = JSONObject(body).optJSONArray("payloads") ?: return PayloadCatalog(emptyList())
        val out = mutableListOf<PayloadEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val files = mutableMapOf<String, FileEntry>()
            o.optJSONObject("files")?.let { fo ->
                fo.keys().forEach { key ->
                    val f = fo.optJSONObject(key) ?: return@forEach
                    val u = f.optString("url", "")
                    if (u.startsWith("http")) {
                        val mirrors = f.optJSONArray("mirrors").strings().filter { it.startsWith("http") }
                        files[key] = FileEntry(u, f.optString("sha256", null), f.optLong("size", 0), mirrors)
                    }
                }
            }
            val env = mutableMapOf<String, String>()
            o.optJSONObject("env")?.let { eo ->
                eo.keys().forEach { k -> env[k] = eo.optString(k) }
            }
            out += PayloadEntry(
                id = o.getString("payloadId"),
                displayName = o.optString("displayName", o.getString("payloadId")),
                models = o.optJSONArray("models").strings(),
                marketNames = o.optJSONArray("marketNames").strings(),
                kernelVersions = o.optJSONArray("kernelVersions").strings(),
                enabled = o.optBoolean("enabled", true),
                verifiedBy = o.optString("verifiedBy", null),
                files = files,
                env = env,
            )
        }
        return PayloadCatalog(out)
    }

    private fun org.json.JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { getString(it) }
    }

    private fun sha256(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes())
            .joinToString("") { "%02x".format(it) }
}
