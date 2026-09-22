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

/**
 * Каталог пейлоадов схемы v5: `builds` — сборки ядра (GKI git-id → Image →
 * бинарь), `devices` — физические тела с картой известных сборок ядра.
 * Приложение не содержит эксплойтов — всё скачивается по описанию.
 *
 * Статусы билда: ready — заявлено рабочим; off — бинарь есть, не заявлено;
 * patched — CVE закрыт в этой сборке; unsupported — пейлоада нет.
 */

/** Файл пейлоада: деплоится под именем [name]; [mirrors] на случай
 *  недоступного ассет-домена GitHub. */
data class PayloadFile(
    val name: String,
    val url: String,
    val mirrors: List<String>,
    val sha256: String?,
    val size: Long,
)

/** Одна сборка ядра: один Image = один бинарь. */
data class KernelBuild(
    val id: String,
    /** Паттерны uname: полная GKI-строка строже короткой версии, «.*» — префикс. */
    val match: List<String>,
    val exploit: String,
    val status: String,
    val file: PayloadFile?,
    val env: Map<String, String>,
    val note: String,
) {
    val ready: Boolean get() = status == "ready" && file != null
    val label: String get() = match.firstOrNull()?.let { kernelTag(it) } ?: id

    /** 2 — совпал GKI-паттерн (строже), 1 — короткая версия, 0 — мимо. */
    fun specificity(actualFull: String): Int {
        var best = 0
        for (m in match) {
            val hit = if (m.endsWith(".*")) actualFull.startsWith(m.removeSuffix("*")) else actualFull.contains(m)
            if (!hit) continue
            val score = if (shortVersion(m).isNotEmpty()) 1 else 2
            if (score > best) best = score
        }
        return best
    }
}

/** Физическое тело: маркет-нейм + V-код, алиасы Build.DEVICE/Build.MODEL. */
data class CatalogDevice(
    val id: String,
    val marketName: String,
    val code: String,
    val models: List<String>,
    val names: List<String>,
    val kernels: List<KernelRef>,
) {
    val title: String get() = if (code.isNotEmpty()) "$marketName • $code" else marketName
}

data class KernelRef(
    val buildId: String,
    val note: String,
)

/** Матч тела и живой сборки — то, что скачивает и деплоит движок. */
data class PayloadMatch(
    val device: CatalogDevice,
    val build: KernelBuild,
) {
    val env: Map<String, String> get() = build.env
    /** Человекочитаемое имя для логов и карточки на главной. */
    val displayName: String get() = "${device.title} · ${build.label}"
}

/** Билд в привязке к карточке-владельцу (для экрана устройств). */
data class DeviceKernel(
    val build: KernelBuild,
    val note: String,
)

data class PayloadCatalog(
    val schemaVersion: Int,
    val builds: Map<String, KernelBuild>,
    val devices: List<CatalogDevice>,
) {
    fun kernelsOf(device: CatalogDevice): List<DeviceKernel> =
        device.kernels.mapNotNull { kr -> builds[kr.buildId]?.let { DeviceKernel(it, kr.note) } }

    fun isSupported(device: CatalogDevice): Boolean =
        kernelsOf(device).any { it.build.ready }
}

/** Короткая версия x.y.z из строки, '' если не читается. */
fun shortVersion(s: String): String =
    Regex("""^\d+\.\d+\.\d+""").find(s)?.value ?: ""

/**
 * Метка сборки для UI: «6.6.89-gb57a» — версия, «-g» и первые 4 символа
 * git-хэша (5 знаков после дефиса; полная GKI-строка не нужна).
 * Без git-суффикса — версия как есть.
 */
fun kernelTag(s: String): String {
    val ver = shortVersion(s)
    val hex = Regex("""-g([0-9a-f]{5,})""").find(s)?.groupValues?.get(1)
    return if (ver.isNotEmpty() && hex != null) "$ver-g${hex.take(4)}" else ver.ifEmpty { s }
}

/**
 * Каталог: 24 ч свежести; при сетевой ошибке — кэш любого возраста.
 * Парсится только v5: старый файл (v4) даёт пустой каталог, и UI показывает
 * «не найдено», а не чужой пейлоад.
 */
class Catalog(var url: String = DEFAULT_URL) {

    companion object {
        const val DEFAULT_URL =
            "https://raw.githubusercontent.com/zenyxx-xd/RootMyVivo-Payloads/main/catalog/devices.json"
        private const val TAG = "NeoCatalog"
        private const val CACHE_FILE = "catalog-v5.json"
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
                cached()?.let { Result.success(it) } ?: Result.failure(IOException("network error"))
            } else {
                cacheFile()?.writeText(body)
                Result.success(parse(body))
            }
        } catch (e: Exception) {
            Log.w(TAG, "catalog fetch failed: ${e.message}")
            cached(ignoreAge = true)?.let { Result.success(it) } ?: Result.failure(e)
        }
    }

    private fun cached(ignoreAge: Boolean = false): PayloadCatalog? = try {
        val f = cacheFile()
        if (f == null || !f.exists()) null
        else if (!ignoreAge && System.currentTimeMillis() - f.lastModified() > CACHE_FRESH_MS) null
        else parse(f.readText())
    } catch (_: Exception) {
        null
    }

    /**
     * Тело из каталога по модели/нейму устройства — без проверки ядра.
     * Отсутствующая запись = устройство неизвестно каталогу.
     */
    fun findDevice(catalog: PayloadCatalog, info: DeviceInfo): CatalogDevice? =
        catalog.devices.firstOrNull { d ->
            (info.model.isNotEmpty() && d.models.any { it.equals(info.model, true) }) ||
                (info.marketName.isNotEmpty() && d.names.any { it.equals(info.marketName, true) })
        }

    /**
     * Живой пейлоад для устройства: тело по моделям/неймам, затем только ready-
     * сборки с точным совпадением версии (или подстрокой для GKI-паттерна).
     * Fallback на чужую сборку той же модели отсутствует.
     */
    fun findPayload(catalog: PayloadCatalog, info: DeviceInfo): PayloadMatch? {
        val device = findDevice(catalog, info) ?: return null
        var best: Pair<KernelBuild, Int>? = null
        for (dk in catalog.kernelsOf(device)) {
            val b = dk.build
            if (!b.ready) continue
            val s = b.specificity(info.kernel)
            if (s > 0 && (best == null || s > best!!.second)) best = b to s
        }
        return best?.let { PayloadMatch(device, it.first) }
    }

    /**
     * Скачивание файла: основной URL, затем зеркала. Редиректы, проверка
     * размера и SHA-256, атомарная запись (.part → rename).
     */
    suspend fun downloadFile(
        file: PayloadFile,
        dest: File,
        onProgress: suspend (read: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val urls = listOf(file.url) + file.mirrors
        var last: Throwable? = null
        for (u in urls) {
            val res = downloadFrom(u, file, dest, onProgress)
            if (res.isSuccess) return@withContext res
            last = res.exceptionOrNull()
            Log.w(TAG, "download ${dest.name} from $u failed: ${last?.message}")
        }
        Result.failure(last ?: IOException("all sources failed"))
    }

    private suspend fun downloadFrom(
        url: String,
        file: PayloadFile,
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
            val loc = conn.getHeaderField("Location") ?: throw IOException("HTTP $code without Location")
            conn.disconnect()
            conn = open(URL(URL(url), loc).toString())
            code = conn.responseCode
            hops++
        }
        if (code !in 200..299) throw IOException("HTTP $code")

        val total = conn.contentLengthLong.takeIf { it > 0 } ?: file.size
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
        if (file.size > 0 && tmp.length() != file.size) {
            throw IOException("incomplete: ${tmp.length()} of ${file.size} bytes")
        }
        file.sha256?.let { want ->
            val got = sha256(tmp)
            if (!got.equals(want, ignoreCase = true)) throw IOException("sha256 mismatch: $got")
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

    // ── Парсинг v5 (org.json, без зависимостей) ──

    fun parse(body: String): PayloadCatalog {
        val root = JSONObject(body)
        if (root.optInt("schemaVersion", 0) < 5) return PayloadCatalog(0, emptyMap(), emptyList())

        val builds = LinkedHashMap<String, KernelBuild>()
        root.optJSONObject("builds")?.let { bo ->
            for (id in bo.keys()) {
                val o = bo.optJSONObject(id) ?: continue
                val match = o.optJSONArray("match").strings().ifEmpty {
                    o.optString("match", "").let { listOf(it) }
                }.filter { it.isNotEmpty() }
                val file = o.optJSONObject("file")?.let { fo ->
                    val u = fo.optString("url", "")
                    if (!u.startsWith("http")) null else PayloadFile(
                        name = fo.optString("name", "preload.so"),
                        url = u,
                        mirrors = fo.optJSONArray("mirrors").strings().filter { it.startsWith("http") },
                        sha256 = fo.optString("sha256", null),
                        size = fo.optLong("size", 0),
                    )
                }
                val env = LinkedHashMap<String, String>()
                o.optJSONObject("env")?.let { eo ->
                    eo.keys().forEach { k -> env[k] = eo.optString(k) }
                }
                builds[id] = KernelBuild(
                    id = id,
                    match = match,
                    exploit = o.optString("exploit", ""),
                    status = o.optString("status", "off"),
                    file = file,
                    env = env,
                    note = o.optString("matchCondition", ""),
                )
            }
        }

        val devices = mutableListOf<CatalogDevice>()
        val da = root.optJSONArray("devices")
        if (da != null) for (i in 0 until da.length()) {
            val o = da.optJSONObject(i) ?: continue
            val kernels = mutableListOf<KernelRef>()
            val ka = o.optJSONArray("kernels")
            if (ka != null) for (k in 0 until ka.length()) {
                val ko = ka.optJSONObject(k) ?: continue
                val buildId = ko.optString("build", "")
                if (buildId.isNotEmpty()) kernels += KernelRef(buildId, ko.optString("note", ""))
            }
            devices += CatalogDevice(
                id = o.optString("id", "dev-$i"),
                marketName = o.optString("marketName", ""),
                code = o.optString("code", ""),
                models = o.optJSONArray("models").strings(),
                names = o.optJSONArray("names").strings(),
                kernels = kernels,
            )
        }
        return PayloadCatalog(5, builds, devices)
    }

    private fun org.json.JSONArray?.strings(): List<String> {
        if (this == null) return emptyList()
        return List(length()) { optString(it) }.filter { it.isNotEmpty() }
    }

    private fun sha256(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes())
            .joinToString("") { "%02x".format(it) }
}
