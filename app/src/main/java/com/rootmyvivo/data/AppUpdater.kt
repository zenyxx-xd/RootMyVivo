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
import java.util.concurrent.TimeUnit

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

    /** Текущий versionCode установки. */
    fun currentVersionCode(ctx: Context): Long = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    } catch (_: Exception) {
        0L
    }

    /** Найти обновление: None, если новых стабильных версий нет. */
    suspend fun check(ctx: Context): AppUpdate? = withContext(Dispatchers.IO) {
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
                if (rel.optBoolean("prerelease") || rel.optBoolean("draft")) continue
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

    /** Схема версий MMmmpp: 1.0.10 → 10010 (как versionCode в gradle). */
    private fun versionNameToCode(ver: String): Long {
        val parts = ver.split('.').map { it.filter(Char::isDigit).toLongOrNull() ?: 0L }
        val maj = parts.getOrElse(0) { 0L }
        val min = parts.getOrElse(1) { 0L }
        val pat = parts.getOrElse(2) { 0L }
        return maj * 10000L + min * 100L + pat
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
            tmp.renameTo(dest)
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

    /** Отдавать время сборки APK для диагностики (не используется в UI). */
    fun apkAgeMs(f: File): Long = TimeUnit.MILLISECONDS.convert(
        System.currentTimeMillis() - f.lastModified(), TimeUnit.MILLISECONDS)
}
