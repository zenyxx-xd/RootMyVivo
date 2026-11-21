package com.rootmyvivo.root

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Менеджеры рута — не хардкод, как в DirtyFrag: сканируем установленные
 * приложения на libksud.so в nativeLibraryDir. Известные варианты
 * (KsuVariant) показываем всегда — неустановленные можно доустановить
 * автоустановкой через системный установщик.
 */
object KsuManagers {

    /** Найденный на теле менеджер. */
    data class Choice(
        /** Известный вариант или null для стороннего пакета. */
        val variant: KsuVariant?,
        /** Имя приложения из PackageManager (лейбл). */
        val appName: String,
        val pkg: String,
        val installed: Boolean,
    )

    /** У приложения есть libksud.so? (DirtyFrag: единственный критерий) */
    fun hasKsud(ctx: Context, pkg: String): Boolean = try {
        val info = ctx.packageManager.getApplicationInfo(pkg, 0)
        info.nativeLibraryDir != null && File(info.nativeLibraryDir, "libksud.so").isFile
    } catch (_: Exception) {
        false
    }

    /**
     * Список для диалога выбора: установленные (скан по libksud) сверху,
     * затем известные варианты, которых нет — их можно установить.
     */
    fun choices(ctx: Context): List<Choice> {
        val installed = mutableListOf<Choice>()
        try {
            val pm = ctx.packageManager
            for (info in pm.getInstalledApplications(0)) {
                val libDir = info.nativeLibraryDir ?: continue
                if (!File(libDir, "libksud.so").isFile) continue
                val pkg = info.packageName
                val variant = KsuVariant.byPackage(pkg)
                val label = runCatching { pm.getApplicationLabel(info).toString() }
                    .getOrDefault(pkg)
                installed += Choice(variant, label.ifEmpty { pkg }, pkg, true)
            }
        } catch (_: Exception) {
        }
        val installedPkgs = installed.map { it.pkg }.toSet()
        // Известные неустановленные: 5 вариантов по порядку enum
        val missing = KsuVariant.entries
            .filter { it.packageName !in installedPkgs }
            .map { Choice(it, it.displayName, it.packageName, false) }
        // Установленные сортируем: известные варианты первыми
        val known = installed.filter { it.variant != null }
            .sortedBy { KsuVariant.entries.indexOf(it.variant) }
        val custom = installed.filter { it.variant == null }
            .sortedBy { it.appName.lowercase() }
        return known + custom + missing
    }
}

/** Выкачивание APK менеджера из GitHub-релизов (общее для движка и VM). */
object ManagerApks {

    private const val CI_REPO = "cctv18/ReSukiSU_CI"

    fun candidates(variant: KsuVariant): List<Pair<String, String>> {
        val repo = if (variant.id == "resukisu") CI_REPO else variant.repo
        val all = listApkAssets(repo).filter { (name, _) -> goodManagerApk(name) }
        val ordered = if (variant.id == "ksunext") {
            all.sortedBy { (name, _) -> if (name.contains("spoof", true)) 0 else 1 }
        } else {
            all.sortedBy { (name, _) -> if (name.contains("spoof", true)) 1 else 0 }
        }
        return ordered.take(3)
    }

    private fun goodManagerApk(name: String) =
        !name.contains("debug", true) &&
            (name.contains("arm64", true) || name.contains("universal", true) ||
                (!name.contains("armeabi", true) && !name.contains("x86", true) && !name.contains("armv7", true)))

    /** Все .apk из последних релизов репозитория: (имя, url). */
    private fun listApkAssets(repo: String): List<Pair<String, String>> = try {
        val c = URL("https://api.github.com/repos/$repo/releases?per_page=10").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 15_000
        val body = c.inputStream.bufferedReader().readText()
        Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.apk)\"")
            .findAll(body)
            .map { it.groupValues[1].substringAfterLast('/') to it.groupValues[1] }
            .toList()
    } catch (_: Exception) {
        emptyList()
    }

    fun download(url: String, path: String): Boolean {
        return try {
            val f = File(path)
            f.parentFile?.mkdirs()
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.connectTimeout = 30_000
            c.inputStream.use { input ->
                f.outputStream().use { input.copyTo(it) }
                val expected = c.contentLengthLong
                !(expected > 0 && f.length() != expected)
            } && f.exists() && f.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Скачать свежий APK менеджера в out; вернуть фактический пакет
     * (null — не скачалось/битое). Тот же отбор кандидатов, что и раньше
     * в KsuInstaller.managerApkCandidates.
     */
    fun fetch(ctx: Context, variant: KsuVariant, out: File): String? {
        out.delete()
        for ((_, url) in candidates(variant)) {
            if (!download(url, out.absolutePath)) continue
            val pkg = try {
                ctx.packageManager.getPackageArchiveInfo(out.absolutePath, 0)?.packageName
            } catch (_: Exception) {
                null
            }
            if (pkg != null && out.length() > 1_000_000) return pkg
            out.delete()
        }
        return null
    }
}
