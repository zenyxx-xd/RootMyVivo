package com.rootmyvivo.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Каталог пейлоадов с сервера.
 * Приложение НЕ содержит эксплойтов — оно их скачивает из каталога.
 */
data class PayloadCatalog(
    val payloads: List<PayloadEntry>,
)

data class ExploitInfo(
    val description: String,
    val maxFixedKernel: Map<String, String?>,
)

data class PayloadEntry(
    val payloadId: String,
    val displayName: String,
    val models: List<String>,
    val marketNames: List<String>,
    val kernelVersions: List<String>,
    val exploit: String,
    val enabled: Boolean,
    val structLayout: String,
    val verifiedBy: String?,
    val offsets: Map<String, String>,
    val files: Map<String, FileEntry>,
)

data class FileEntry(
    val url: String,
    val sha256: String?,
    val size: Long,
)

/**
 * Клиент каталога — загружает список устройств с GitHub.
 */
class CatalogClient(var catalogUrl: String = DEFAULT_URL) {

    companion object {
        const val DEFAULT_URL =
            "https://raw.githubusercontent.com/zenyxx-xd/RootMyVivo-Payloads/main/support/targets-vivo.json"
        private const val CACHE_FILE = "payload_catalog.json"
        private const val CACHE_MAX_AGE_MS = 3600_000L
    }

    suspend fun fetch(): Result<PayloadCatalog> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(catalogUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val catalog = parseCatalog(body)
                saveCache(body)
                Result.success(catalog)
            } else {
                loadCache()?.let { Result.success(it) }
                    ?: Result.failure(Exception("HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            loadCache()?.let { Result.success(it) }
                ?: Result.failure(e)
        }
    }

    fun findPayload(catalog: PayloadCatalog, info: DeviceInfo): PayloadEntry? {
        return catalog.payloads.firstOrNull { p ->
            p.enabled &&
                (p.models.contains(info.model) || p.marketNames.contains(info.marketName)) &&
                matchesKernel(p.kernelVersions, info.kernelShort)
        } ?: catalog.payloads.firstOrNull { p ->
            p.enabled && p.models.contains(info.model)
        }
    }

    private fun matchesKernel(supported: List<String>, actual: String): Boolean {
        if (supported.isEmpty()) return true
        return supported.any { pattern ->
            if (pattern.endsWith(".*")) actual.startsWith(pattern.removeSuffix("*"))
            else actual == pattern
        }
    }

    suspend fun downloadPayloadFile(
        fileEntry: FileEntry,
        destPath: String,
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> },
        onError: suspend (String) -> Unit = { _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        val destination = File(destPath)
        val temporary = File("$destPath.part")
        try {
            destination.parentFile?.mkdirs()
            temporary.delete()

            // GitHub release сначала отдаёт 302 на objects.githubusercontent.com.
            // Вручную следуем redirect, чтобы одинаково работать на Android/OriginOS.
            var cur = URL(fileEntry.url).openConnection() as HttpURLConnection
            cur.instanceFollowRedirects = false
            cur.connectTimeout = 30000
            cur.readTimeout = 120000
            cur.setRequestProperty("User-Agent", "RootMyVivo/0.1")
            cur.setRequestProperty("Accept", "application/octet-stream")
            var code = cur.responseCode
            var hops = 0
            while (hops < 5 && (code == 301 || code == 302 || code == 303 || code == 307 || code == 308)) {
                val loc = cur.getHeaderField("Location")
                    ?: throw java.io.IOException("HTTP $code без Location")
                cur.disconnect()
                cur = URL(URL(fileEntry.url), loc).openConnection() as HttpURLConnection
                cur.instanceFollowRedirects = false
                cur.connectTimeout = 30000
                cur.readTimeout = 120000
                cur.setRequestProperty("User-Agent", "RootMyVivo/0.1")
                cur.setRequestProperty("Accept", "application/octet-stream")
                code = cur.responseCode
                hops++
            }
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code для ${fileEntry.url}")
            }

            val totalSize = cur.contentLengthLong.takeIf { it > 0 } ?: fileEntry.size

            cur.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buf = ByteArray(65536)
                    var read_total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read_total += n
                        onProgress(read_total, totalSize)
                    }
                }
            }

            if (!temporary.exists() || temporary.length() == 0L) {
                throw java.io.IOException("пустой ответ (${temporary.length()} bytes)")
            }
            if (fileEntry.size > 0L && temporary.length() != fileEntry.size) {
                throw java.io.IOException(
                    "неполный файл: ${temporary.length()} из ${fileEntry.size} bytes"
                )
            }

            fileEntry.sha256?.let { expected ->
                val actual = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(temporary.readBytes())
                    .joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, true)) {
                    throw java.io.IOException("SHA-256 не совпал: $actual")
                }
            }

            if (!temporary.renameTo(destination)) {
                throw java.io.IOException("не удалось переместить файл в ${destination.path}")
            }
            true
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            android.util.Log.e("RootMyVivo", "download $destPath failed: $reason", e)
            onError(reason)
            temporary.delete()
            destination.delete()
            false
        }
    }

    // ── JSON парсинг (org.json — без внешних зависимостей) ──

    fun parseCatalog(body: String): PayloadCatalog {
        val root = JSONObject(body)
        val payloadsArr = root.optJSONArray("payloads") ?: return PayloadCatalog(emptyList())
        
        val entries = mutableListOf<PayloadEntry>()
        for (i in 0 until payloadsArr.length()) {
            val obj = payloadsArr.getJSONObject(i)
            
            val models = mutableListOf<String>()
            obj.optJSONArray("models")?.let { arr ->
                for (j in 0 until arr.length()) models.add(arr.getString(j))
            }
            
            val marketNames = mutableListOf<String>()
            obj.optJSONArray("marketNames")?.let { arr ->
                for (j in 0 until arr.length()) marketNames.add(arr.getString(j))
            }
            
            val kernelVersions = mutableListOf<String>()
            obj.optJSONArray("kernelVersions")?.let { arr ->
                for (j in 0 until arr.length()) kernelVersions.add(arr.getString(j))
            }
            
            val offsets = mutableMapOf<String, String>()
            obj.optJSONObject("offsets")?.let { offs ->
                offs.keys().forEach { key -> offsets[key] = offs.getString(key) }
            }
            
            val files = mutableMapOf<String, FileEntry>()
            obj.optJSONObject("files")?.let { filesObj ->
                filesObj.keys().forEach { key ->
                    val fileObj = filesObj.optJSONObject(key) ?: return@forEach
                    // Пропускаем не-http записи (например notes)
                    val url = fileObj.optString("url", "")
                    if (!url.startsWith("http")) return@forEach
                    files[key] = FileEntry(
                        url = url,
                        sha256 = fileObj.optString("sha256", null),
                        size = fileObj.optLong("size", 0),
                    )
                }
            }
            
            entries.add(PayloadEntry(
                payloadId = obj.getString("payloadId"),
                displayName = obj.optString("displayName", obj.getString("payloadId")),
                models = models,
                marketNames = marketNames,
                kernelVersions = kernelVersions,
                exploit = obj.optString("exploit", ""),
                enabled = obj.optBoolean("enabled", true),
                structLayout = obj.optString("structLayout", "6.6"),
                verifiedBy = obj.optString("verifiedBy", null),
                offsets = offsets,
                files = files,
            ))
        }
        
        return PayloadCatalog(entries)
    }

    private fun saveCache(body: String) {
        try {
            File(com.rootmyvivo.RmvApp.instance.filesDir, CACHE_FILE).writeText(body)
        } catch (_: Exception) {}
    }

    private fun loadCache(): PayloadCatalog? {
        return try {
            val f = File(com.rootmyvivo.RmvApp.instance.filesDir, CACHE_FILE)
            if (!f.exists()) return null
            if (System.currentTimeMillis() - f.lastModified() > CACHE_MAX_AGE_MS) return null
            parseCatalog(f.readText())
        } catch (_: Exception) { null }
    }
}
