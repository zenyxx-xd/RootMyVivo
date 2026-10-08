package com.rootmyvivo.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Найденное обновление приложения. */
data class AppUpdate(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val apkSize: Long,
    val body: String,
    val htmlUrl: String,
)

/**
 * Проверка обновлений RootMyVivo: свежий release в GitHub-репозитории
 * (не pre-release), у которого versionCode выше установленного.
 * Скачивание — во внешний кэш с прогрессом, установка — FileProvider.
 */
object AppUpdater {

    private const val TAG = "NeoUpdater"
    const val REPO = "zenyxx-xd/RootMyVivo"
    private const val API = "https://api.github.com/repos/$REPO/releases"
    private val ASSET_RE = Regex("""RootMyVivo-v?[\w.\-]+\.apk""", RegexOption.IGNORE_CASE)

    /**
     * Чистка кеша обновлений при старте: скачанные APK (rmv-update-*.apk)
     * остаются в кеше после установки и скапливаются — удаляем все при
     * запуске приложения. Файл текущей сессии установки не трогаем до
     * следующего старта (установщик читает его при подтверждении).
     */
    fun sweepUpdateCache(ctx: Context) {
        val dir = ctx.externalCacheDir ?: ctx.cacheDir ?: return
        val files = try {
            dir.listFiles() ?: return
        } catch (_: Exception) {
            return
        }
        for (f in files) {
            if (f.name.startsWith("rmv-update-") && f.name.endsWith(".apk")) {
                try {
                    f.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Текущий versionCode установки. */
    fun currentVersionCode(ctx: Context): Long = try {
        // minSdk 31 — longVersionCode доступен всегда
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        pi.longVersionCode
    } catch (_: Exception) {
        0L
    }

    /** Найти обновление: без бета-канала — только стабильные (beta-теги и
     *  pre-release пропускаются), с каналом — всё. */
    suspend fun check(ctx: Context, betaChannel: Boolean = false): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return@withContext null
            val arr = conn.inputStream.bufferedReader().readText()
            val releases = JSONObject("{\"r\":$arr}").getJSONArray("r")
            val current = currentVersionCode(ctx)
            for (i in 0 until releases.length()) {
                val rel = releases.getJSONObject(i)
                if (rel.optBoolean("draft")) continue
                if (!betaChannel &&
                    (rel.optBoolean("prerelease") ||
                        rel.optString("tag_name").contains("-beta", ignoreCase = true))
                ) continue
                val tag = rel.optString("tag_name", "")
                val ver = tag.trimStart('v', 'V')
                val assets = rel.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    val name = a.optString("name", "")
                    if (!ASSET_RE.matches(name)) continue
                    val url = a.optString("browser_download_url", "")
                    if (!url.startsWith("http")) continue
                    // versionCode из имени APK: RootMyVivo-v1.0.10.apk → 10010
                    val code = versionNameToCode(ver)
                    if (code > current) {
                        return@withContext AppUpdate(
                            versionName = ver,
                            versionCode = code,
                            apkUrl = url,
                            apkSize = a.optLong("size", 0L),
                            body = rel.optString("body", "").trim(),
                            htmlUrl = rel.optString("html_url", ""),
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "update check failed: ${e.message}")
            null
        }
    }

    /**
     * Схема версий MMmmpp: 1.0.10 → 10010 (как versionCode в gradle).
     * Бета-суффикс различает бетки: 1.2.0-beta2 → 10200 + 2 = 10202
     * (базовый код + номер беты, как versionCode в gradle).
     */
    /**
     * Схема: код = мажор·10⁷ + минор·10⁵ + фиксы·10³ + СЛОТ, где слот
     * 01–98 — номер беты, 99 — стабильная. Бета своей версии ВСЕГДА ниже
     * её стабильного кода (10 200 021 < 10 200 099) — переход «бета →
     * релиз с тем же номером» честно находится; любая стабильная ниже
     * любой беты следующей версии. До 98 бет на версию.
     */
    private fun versionNameToCode(ver: String): Long {
        val beta = Regex("""-beta(\d+)$""", RegexOption.IGNORE_CASE).find(ver)
            ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val base = Regex("""-beta\d+$""", RegexOption.IGNORE_CASE).replace(ver, "")
        val parts = base.split('.').map { it.filter(Char::isDigit).toLongOrNull() ?: 0L }
        val maj = parts.getOrElse(0) { 0L }
        val min = parts.getOrElse(1) { 0L }
        val pat = parts.getOrElse(2) { 0L }
        // Стабильная занимает верхний слот (99), беты — 01..98
        val slot = if (beta > 0L) beta.coerceIn(1L, 98L) else 99L
        return maj * 10_000_000L + min * 100_000L + pat * 1_000L + slot
    }

    /**
     * Скачать APK во внешний кэш (доступен FileProvider без storage-разрешений).
     * Прогресс — доля [0..1] или null, если размер неизвестен.
     */
    suspend fun download(
        update: AppUpdate,
        ctx: Context,
        onProgress: suspend (read: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val dir = ctx.externalCacheDir ?: ctx.cacheDir
        val dest = File(dir, "rmv-update-${update.versionCode}.apk")
        val tmp = File(dest.absolutePath + ".part")
        try {
            var conn = URL(update.apkUrl).openConnection() as HttpURLConnection
            var code = conn.responseCode
            var hops = 0
            while (hops < 5 && code in 301..308) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("no Location")
                conn.disconnect()
                conn = URL(URL(update.apkUrl), loc).openConnection() as HttpURLConnection
                code = conn.responseCode
                hops++
            }
            if (code !in 200..299) throw IOException("HTTP $code")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.apkSize
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
            if (total > 0 && tmp.length() != total) throw IOException("incomplete download")
            // renameTo молча падает на переходе между ФС (внутренний кэш → внешний):
            // тогда переписываем поток и чистим tmp, иначе установщик получит пустоту
            if (!tmp.renameTo(dest)) {
                tmp.inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                tmp.delete()
            }
            dest
        } catch (e: Exception) {
            tmp.delete()
            dest.delete()
            throw e
        }
    }

    /** Открыть системный установщик APK (требует ACTION_INSTALL_PACKAGE). */
    suspend fun install(apk: File, ctx: Context): Boolean = withContext(Dispatchers.Main) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "install failed: ${e.message}")
            false
        }
    }
}
