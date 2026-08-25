package com.rootmyvivo.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Каталог пейлоадов с сервера.
 * Приложение НЕ содержит эксплойтов — оно их скачивает из каталога
 * под конкретное устройство.
 */
@Serializable
data class PayloadCatalog(
    val schemaVersion: Int = 3,
    val payloads: List<PayloadEntry> = emptyList(),
    val exploits: Map<String, ExploitInfo> = emptyMap(),
)

@Serializable
data class ExploitInfo(
    val description: String = "",
    val maxFixedKernel: Map<String, String?> = emptyMap(),
)

@Serializable
data class PayloadEntry(
    val payloadId: String,
    val displayName: String,
    val models: List<String> = emptyList(),
    val marketNames: List<String> = emptyList(),
    val kernelVersions: List<String> = emptyList(),
    val exploit: String = "",
    val enabled: Boolean = true,
    val structLayout: String = "6.6",
    val verifiedBy: String? = null,
    val offsets: Map<String, String> = emptyMap(),
    val files: Map<String, FileEntry> = emptyMap(),
)

@Serializable
data class FileEntry(
    val url: String,
    val sha256: String? = null,
    val size: Long = 0,
)

/**
 * Клиент каталога — загружает и кеширует список устройств с GitHub.
 * Никаких захардкоженных данных — всё с сервера.
 */
class CatalogClient {

    companion object {
        private const val CATALOG_URL =
            "https://raw.githubusercontent.com/zenyxx-xd/RootMyVivo-Payloads/main/support/targets-vivo.json"
        private const val CATALOG_CACHE_FILE = "payload_catalog.json"
        private const val CACHE_MAX_AGE_MS = 3600_000L // 1 час

        private val json = Json { ignoreUnknownKeys = true }
    }

    /**
     * Загружает каталог (сеть или кеш).
     */
    suspend fun fetch(forceRefresh: Boolean = false): Result<PayloadCatalog> =
        withContext(Dispatchers.IO) {
            try {
                // Пробуем сеть
                val conn = URL(CATALOG_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("Accept", "application/json")

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val catalog = json.decodeFromString<PayloadCatalog>(body)
                    saveCache(body)
                    Result.success(catalog)
                } else {
                    // Fallback на кеш
                    loadCache()?.let { Result.success(it) }
                        ?: Result.failure(Exception("HTTP ${conn.responseCode}"))
                }
            } catch (e: Exception) {
                // Fallback на кеш
                loadCache()?.let { Result.success(it) }
                    ?: Result.failure(e)
            }
        }

    /**
     * Находит подходящий пейлоад для устройства.
     * Матчит: модель + версия ядра + enabled=true
     */
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
            // Поддерживаем wildcard: "6.6.*" матчится на "6.6.89"
            if (pattern.endsWith(".*")) {
                actual.startsWith(pattern.removeSuffix("*"))
            } else {
                actual == pattern
            }
        }
    }

    /** Скачивает файл пейлоада (preload.so, ksud и т.д.) */
    suspend fun downloadPayloadFile(
        fileEntry: FileEntry,
        destPath: String,
        onProgress: suspend (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val f = File(destPath)
            f.parentFile?.mkdirs()

            val conn = URL(fileEntry.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30000
            conn.readTimeout = 60000
            conn.instanceFollowRedirects = true

            val totalSize = conn.contentLengthLong.takeIf { it > 0 } ?: fileEntry.size

            conn.inputStream.use { input ->
                f.outputStream().use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        onProgress(bytesRead, totalSize)
                    }
                }
            }

            // Проверка SHA256 если указана
            fileEntry.sha256?.let { expected ->
                val actual = f.inputStream.use { inp ->
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(inp.readBytes())
                        .joinToString("") { "%02x".format(it) }
                }
                if (!actual.equals(expected, ignoreCase = true)) {
                    f.delete()
                    return@withContext false
                }
            }

            f.exists() && f.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    // ── Кеш ──
    private fun cacheFile() = File(
        com.rootmyvivo.RmvApp.instance.filesDir,
        CATALOG_CACHE_FILE
    )

    private fun saveCache(body: String) {
        try { cacheFile().writeText(body) } catch (_: Exception) {}
    }

    private fun loadCache(): PayloadCatalog? {
        return try {
            val f = cacheFile()
            if (!f.exists()) return null
            if (System.currentTimeMillis() - f.lastModified() > CACHE_MAX_AGE_MS) return null
            json.decodeFromString<PayloadCatalog>(f.readText())
        } catch (_: Exception) { null }
    }
}
