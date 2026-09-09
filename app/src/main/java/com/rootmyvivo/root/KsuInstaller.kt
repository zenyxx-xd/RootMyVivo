package com.rootmyvivo.root

import android.content.Context
import android.util.Log
import com.rootmyvivo.R
import com.rootmyvivo.data.DeviceInfo
import com.rootmyvivo.shell.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Результат установки KernelSU. */
enum class KsuResult { ACTIVE, NEEDS_REBOOT, PARTIAL, FAILED }

/** Установка KernelSU: скачивание .ko → vermagic-патч → late-load → менеджер → закрепление. */
class KsuInstaller(
    private val ctx: Context,
    private val device: DeviceInfo,
    private val workDir: File,
    private val onEvent: suspend (FlowEvent) -> Unit,
) {

    private suspend fun log(id: Int, level: LogLevel, vararg args: Any?) {
        onEvent(FlowEvent.Log(ctx.getString(id, *args), level))
    }

    private suspend fun progress(id: Int, vararg args: Any?) {
        onEvent(FlowEvent.Progress(ctx.getString(id, *args)))
    }

    private suspend fun complete(ok: Boolean, id: Int? = null, vararg args: Any?) {
        onEvent(FlowEvent.Complete(ok, id?.let { ctx.getString(it, *args) }))
    }

    suspend fun install(variant: KsuVariant): KsuResult =
        withContext(Dispatchers.IO) {
            val prefs = com.rootmyvivo.data.Prefs(ctx)
            // Пакет для перезапуска менеджера из late-load: у spoofed-сборок
            // он рандомный — берём фактический из прошлой установки
            val restartPkg = prefs.managerPackage.ifEmpty { variant.packageName }

            // ── 1. Аудит: что уже есть на устройстве, до любых скачиваний ──
            val (_, preMods) = Transport.exec(ctx, "grep -i kernelsu /proc/modules 2>/dev/null")
            val moduleLoaded = preMods.isNotBlank()
            val loadedOf = prefs.loadedModuleVariant
            if (moduleLoaded && loadedOf.isNotEmpty() && loadedOf != variant.id) {
                log(R.string.log_ksu_other_variant, LogLevel.WARN, variant.displayName)
            } else if (moduleLoaded) {
                log(R.string.log_ksu_already_loaded, LogLevel.WARN)
            }
            val managerKsud = findManagerKsud(ctx, listOf(variant.packageName, prefs.managerPackage))
            val (_, probe) = Transport.exec(
                ctx,
                "[ -f /data/adb/rmv/kernelsu.ko ] && echo RMV_CACHE; [ -f $REMOTE_KSUD ] && echo RMV_KSUD",
            )
            val koCached = probe.contains("RMV_CACHE")
            val remoteKsud = probe.contains("RMV_KSUD")

            // ── 2. ksud: скачиваем только если нет ни менеджерного, ни remote ──
            if (managerKsud == null && !remoteKsud) {
                progress(R.string.log_ksud_download)
                if (downloadKsud(variant)) {
                    onEvent(FlowEvent.Complete(true))
                } else {
                    complete(false, R.string.log_ksud_fail)
                    // Менеджер всё равно ставим; root-демон уже активен
                    installManager(variant)
                    return@withContext KsuResult.PARTIAL
                }
            }

            // ── 3. Модуль: без скачивания, если он уже в ядре и кэш на месте ──
            val koPath = File(workDir, "kernelsu_${variant.id}.ko").absolutePath
            var out = ""
            if (moduleLoaded && koCached && (loadedOf.isEmpty() || loadedOf == variant.id)) {
                // Модуль уже работает, закрепление записано — только userspace-
                // настройка при наличии ksud
                if (managerKsud != null || remoteKsud) {
                    out = Transport.su(
                        ctx,
                        "${managerKsud ?: REMOTE_KSUD} late-load --allow-shell --package-name $restartPkg",
                    ).second
                }
            } else {
                File(koPath).delete()
                progress(R.string.log_ksu_download, device.kmi)
                if (!downloadKo(variant, koPath)) {
                    complete(false, R.string.log_ksu_download_fail)
                    installManager(variant)
                    return@withContext KsuResult.PARTIAL
                }

                progress(R.string.log_ksu_vermagic)
                if (!patchVermagic(koPath, device.kernel)) {
                    complete(false, R.string.log_ksu_vermagic_fail)
                    installManager(variant)
                    return@withContext KsuResult.PARTIAL
                }

                if (!moduleLoaded) {
                    progress(R.string.log_ksu_load)
                    // Модуль грузим прямо из приватного каталога приложения:
                    // root-домен читает его (проверено KernelSU Next), а файловый
                    // сканер vivo /data/local/tmp прочёсывает — там .ko успевал
                    // схватить write-дескриптором (ETXTBSY) или вовсе удалить.
                    //
                    // ksud'ы пробуем по списку: первым — из установленного менеджера
                    // (lib/arm64/libksud.so, свежая версия с kallsyms-insmod:
                    // модулям SukiSU нужны неэкспортируемые символы SELinux,
                    // которые резолвит только их загрузчик), затем скачанный с GitHub
                    val ksudPaths = buildList {
                        managerKsud?.let { add(it) }
                        add(REMOTE_KSUD)
                    }
                    val (ok, o) = loadModule(ctx, koPath, restartPkg, ksudPaths)
                    out = o
                    Log.i(TAG, "module load: loaded=$ok ${out.take(160)}")
                    if (ok) {
                        prefs.loadedModuleVariant = variant.id
                    } else {
                        complete(false, R.string.log_ksu_module_fail)
                        // диагностика: что ответила каждая попытка загрузки
                        if (out.isNotBlank()) {
                            onEvent(FlowEvent.Log(out.trim().takeLast(400), LogLevel.PLAIN))
                        }
                        installManager(variant)
                        // закрепление всё равно пишем: на чистом ядре после полной
                        // перезагрузки insmod пройдёт и рут вернётся
                        setupPersistence(koPath)
                        return@withContext KsuResult.PARTIAL
                    }
                } else {
                    // Другой вариант в ядре: скачанный модуль уйдёт в кэш
                    // закрепления и поднимется после следующей перезагрузки
                    progress(R.string.log_ksu_load)
                }
            }

            // ── 4. Проверка su, менеджер, закрепление ──
            progress(R.string.log_ksu_verify)
            val (vCode, vOut) = Transport.su(ctx, "id")
            val rooted = vCode == 0 && vOut.contains("uid=0")
            // Менеджер ставится скриптом через su (без проверок vivo) в обоих случаях
            installManager(variant)
            if (rooted) {
                complete(true, R.string.log_ksu_active, variant.displayName)
                setupPersistence(koPath)
                KsuResult.ACTIVE
            } else {
                complete(true, R.string.log_ksu_soft_reboot)
                KsuResult.NEEDS_REBOOT
            }
        }

    /**
     * Скачать ksud и развернуть в /data/local/tmp/rmv/ksud.
     * Имена ассетов у форков разные и меняются: точного `ksud` в свежих релизах
     * больше нет — ищем `ksud-aarch64-linux-android` (SukiSU/Next) и
     * `ksud-aarch64-linux-android.zip` (ReSukiSU CI, распаковываем) по последним
     * релизам. Для семейства Next/SukiSU/ReSukiSU есть фолбэк на CI-сборку.
     */
    private suspend fun downloadKsud(variant: KsuVariant): Boolean {
        return try {
            val family = variant.id in setOf("ksunext", "sukisu", "resukisu")
            val repos = buildList {
                add(if (variant.id == "resukisu") CI_REPO else variant.repo)
                if (family && variant.id != "resukisu") add(CI_REPO)
            }
            val url = repos.firstNotNullOfOrNull { repo ->
                findAssetUrl(repo, "ksud")
                    ?: findAssetUrl(repo, "ksud-aarch64-linux-android")
                    ?: findAssetUrl(repo, "ksud-aarch64-linux-android.zip")
            } ?: return false

            val ksud = File(workDir, "ksud")
            ksud.delete()
            val tmp = File(workDir, "ksud.dl")
            if (!download(url, tmp.absolutePath)) {
                tmp.delete()
                return false
            }
            val prepared = if (url.endsWith(".zip")) unzipEntry(tmp, ksud, "ksud") else tmp.renameTo(ksud)
            tmp.delete()
            prepared && ksud.length() > 0 &&
                Transport.deploy(ctx, ksud.absolutePath, REMOTE_KSUD) &&
                Transport.exec(ctx, "chmod 755 $REMOTE_KSUD").first == 0
        } catch (e: Exception) {
            Log.e(TAG, "downloadKsud failed", e)
            false
        }
    }

    /** Поиск ассета с точным именем в последних релизах репозитория. */
    private fun findAssetUrl(repo: String, name: String): String? = try {
        val c = URL("https://api.github.com/repos/$repo/releases?per_page=10").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 15_000
        val body = c.inputStream.bufferedReader().readText()
        Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(body)
            .map { it.groupValues[1] }
            .firstOrNull { it.substringAfterLast('/') == name }
    } catch (_: Exception) {
        null
    }

    /** Достать файл из zip-архива (первый подходящий по имени). */
    private fun unzipEntry(zip: File, out: File, entryName: String): Boolean = try {
        java.util.zip.ZipInputStream(zip.inputStream()).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory && (e.name == entryName || e.name.endsWith("/$entryName"))) {
                    out.outputStream().use { zis.copyTo(it) }
                    return true
                }
                e = zis.nextEntry
            }
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "unzipEntry failed", e)
        false
    }

    /** Закрепление: скрипт в /data/adb/service.d пере-поднимает KSU после ребута. */
    private suspend fun setupPersistence(koPath: String) {
        val ksud = File(workDir, "ksud")
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# RootMyVivo Neo persistence")
            appendLine("DIR=/data/adb/rmv")
            appendLine("mkdir -p \$DIR")
            appendLine("cp -f $koPath \$DIR/kernelsu.ko 2>/dev/null")
            if (ksud.exists()) {
                appendLine("cp -f ${ksud.absolutePath} \$DIR/ksud 2>/dev/null")
                appendLine("chmod 755 \$DIR/ksud 2>/dev/null")
            }
            appendLine("grep -qi kernelsu /proc/modules 2>/dev/null || insmod \$DIR/kernelsu.ko allow_shell=1 2>/dev/null")
            if (ksud.exists()) appendLine("[ -x \$DIR/ksud ] && \$DIR/ksud post-fs-data 2>/dev/null")
            appendLine("exit 0")
        }
        // Скрипт пишем через транспорт: filesDir приложения недоступен root-домену напрямую
        val local = File(ctx.filesDir, "rmv-persist.sh")
        local.writeText(script)
        Transport.deploy(ctx, local.absolutePath, "/data/local/tmp/rmv-persist.sh")
        // su() — наш клиент в /data/local/tmp/su, PATH-su появится только после KSU
        Transport.su(
            ctx,
            "mkdir -p /data/adb/service.d && " +
                "mv /data/local/tmp/rmv-persist.sh /data/adb/service.d/rmv-persist.sh && " +
                "chmod 755 /data/adb/service.d/rmv-persist.sh",
        )
        local.delete()
    }

    /**
     * Скачать менеджер и поставить полноценным скриптом через su:
     * pm install без проверок vivo + автооткрытие менеджера для настройки.
     *
     * Кандидатов несколько: свежий APK может не ставиться (targetSdk новой
     * платформы) или падать на устройстве (vivo валит менеджеры с известными
     * пакетами — для KSU Next предпочитаем spoofed-сборку с рандомным пакетом,
     * ядро принимает её по той же подписи). Фактический пакет читаем из APK:
     * у spoofed-сборок он не совпадает с каноническим.
     */
    private suspend fun installManager(variant: KsuVariant) {
        try {
            val prefs = com.rootmyvivo.data.Prefs(ctx)
            // Уже установлен — не качаем и не ставим повторно. Проверяем и
            // канонический пакет варианта, и фактический прошлой установки
            val knownPkgs = listOf(variant.packageName, prefs.managerPackage)
                .filter { it.isNotEmpty() }.distinct()
            for (pkg in knownPkgs) {
                if (isPackageInstalled(pkg)) {
                    log(R.string.log_manager_already, LogLevel.OK, variant.displayName)
                    return
                }
            }

            progress(R.string.log_manager_download, variant.displayName)
            val candidates = managerApkCandidates(variant)
            if (candidates.isEmpty()) {
                complete(false, R.string.log_manager_download_fail)
                return
            }

            for ((_, url) in candidates) {
                val apk = File(workDir, "manager.apk")
                apk.delete()
                if (!download(url, apk.absolutePath)) {
                    continue
                }
                // Фактический пакет из APK — spoofed сидит в рандомном имени
                val actualPkg = try {
                    ctx.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
                } catch (_: Exception) {
                    null
                } ?: continue
                if (isPackageInstalled(actualPkg)) {
                    // уже стоит (например, ставили вручную) — просто запоминаем
                    prefs.managerPackage = actualPkg
                    log(R.string.log_manager_already, LogLevel.OK, variant.displayName)
                    apk.delete()
                    return
                }

                onEvent(FlowEvent.Complete(true))
                progress(R.string.log_manager_install)
                if (installApkViaRoot(apk) && isPackageInstalled(actualPkg)) {
                    prefs.managerPackage = actualPkg
                    complete(true, R.string.log_manager_install_ok)
                    apk.delete()
                    return
                }
                // этот кандидат не встал — пробуем следующий (диагностика
                // последней попытки уйдёт в лог ниже, перед выходом из цикла)
                Log.w(TAG, "manager candidate failed: $url")
            }
            // pm install пишет причину в setup.log — без него «не удалась»
            // не сказать ничего
            val (_, diag) = Transport.su(
                ctx,
                "tail -c 600 /data/local/tmp/rmv/setup.log 2>/dev/null",
            )
            if (diag.isNotBlank()) {
                onEvent(FlowEvent.Log(diag.trim().takeLast(400), LogLevel.PLAIN))
            }
            complete(false, R.string.log_manager_install_fail)
        } catch (e: Exception) {
            Log.e(TAG, "installManager failed", e)
        }
    }

    private suspend fun isPackageInstalled(pkg: String): Boolean {
        val (_, pathOut) = Transport.exec(ctx, "pm path $pkg", timeoutSec = 30)
        return pathOut.startsWith("package:")
    }

    /** Деплой APK + root-скрипт pm install; true если скрипт отработал. */
    private suspend fun installApkViaRoot(apk: File): Boolean {
        val remoteApk = "/data/local/tmp/rmv/manager.apk"
        val remoteScript = "/data/local/tmp/rmv/setup.sh"
        if (!Transport.deploy(ctx, apk.absolutePath, remoteApk)) {
            onEvent(FlowEvent.Log("deploy manager.apk: failed", LogLevel.PLAIN))
            return false
        }

        // Скрипт: установка менеджера из-под root + запуск для дальнейшей настройки
        val script = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("# RootMyVivo Neo — post-root setup")
            appendLine("LOG=/data/local/tmp/rmv/setup.log")
            appendLine("exec > \$LOG 2>&1")
            appendLine("pm install -r $remoteApk || pm install -r --no-verify $remoteApk")
            appendLine("echo PM_INSTALL_RC=\$?")
            appendLine("rm -f $remoteApk")
            appendLine("sleep 2")
            appendLine("am start -n com.rootmyvivo/.MainActivity")
        }
        val localScript = File(ctx.filesDir, "setup.sh")
        localScript.writeText(script)
        if (!Transport.deploy(ctx, localScript.absolutePath, remoteScript)) {
            localScript.delete()
            return false
        }
        localScript.delete()

        // Через su (root, без проверок vivo); при недоступности su — из shell-домена
        var (code, out) = Transport.su(ctx, "sh $remoteScript")
        if (code != 0) {
            Log.w(TAG, "setup via su failed ($code), falling back to shell: ${out.take(120)}")
            code = Transport.exec(ctx, "sh $remoteScript").first
        }
        return code == 0
    }

    /**
     * Кандидаты на установку менеджера: (имя, url), по убыванию приоритета.
     * Для KSU Next spoofed-сборка идёт первой — vivo валит менеджер с
     * каноническим пакетом com.rifsxd.ksunext, а рандомный пакет spoofed
     * ядро принимает по той же подписи. Остальные варианты: сначала обычные.
     */
    private fun managerApkCandidates(variant: KsuVariant): List<Pair<String, String>> {
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

    /** Все .apk из последних релизов репозитория: (имя, url).
     *  Имя берём из самого URL: в JSON API между "name" ассета и
     *  "browser_download_url" лежит ~1.4КБ объекта uploader — regex с окном
     *  по имени регулярно промахивается, а последний сегмент URL всегда
     *  совпадает с именем ассета. */
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

    /** Поиск asset в последнем релизе: точное имя или предикат. */
    private fun releaseAssetUrl(repo: String, exact: String? = null, predicate: (String) -> Boolean = { false }): String? = try {
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.connectTimeout = 15_000
        val body = c.inputStream.bufferedReader().readText()
        Regex(""""browser_download_url"\s*:\s*"([^"]+)"""")
            .findAll(body)
            .map { it.groupValues[1] }
            .firstOrNull { url ->
                val name = url.substringAfterLast('/')
                name == exact || (exact == null && predicate(name))
            }
    } catch (_: Exception) {
        null
    }

    private fun downloadKo(variant: KsuVariant, outPath: String): Boolean {
        return try {
            val kmi = device.kmi
            val ko = File(outPath)
            when (variant.id) {
                "kernelsu" -> {
                    // v3.3+ переименовали ассеты: lkm-aarch64-<kmi>_kernelsu.ko
                    val tag = latestTag(variant.repo) ?: return false
                    download("https://github.com/${variant.repo}/releases/download/$tag/${kmi}_kernelsu.ko", outPath) ||
                        download("https://github.com/${variant.repo}/releases/download/$tag/lkm-aarch64-${kmi}_kernelsu.ko", outPath)
                }
                "ksunext" -> {
                    val tag = latestTag(variant.repo) ?: return false
                    download("https://github.com/${variant.repo}/releases/download/$tag/${kmi}_kernelsu.ko", outPath) ||
                        download("https://github.com/${variant.repo}/releases/download/$tag/${kmi}_kernelsu_next.ko", outPath)
                }
                "sukisu" -> {
                    val tag = latestTag(variant.repo) ?: return false
                    // v4.2+ модуль лежит zip-архивом aarch64-<kmi>-lkm.zip
                    download("https://github.com/${variant.repo}/releases/download/$tag/${kmi}_kernelsu.ko", outPath) ||
                        downloadZipKo(
                            "https://github.com/${variant.repo}/releases/download/$tag/aarch64-$kmi-lkm.zip",
                            "${kmi}_kernelsu.ko",
                            ko,
                        )
                }
                else -> {
                    // ReSukiSU CI: архив lkm-all.zip, нужный .ko внутри
                    val tag = latestTag(CI_REPO) ?: return false
                    downloadZipKo(
                        "https://github.com/$CI_REPO/releases/download/$tag/lkm-all.zip",
                        "${kmi}_kernelsu.ko",
                        ko,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadKo failed", e)
            false
        }
    }

    /** Скачать zip и достать .ko. Распаковка — своим ZipInputStream:
     *  системный unzip (toybox) не понимает флаг -j. */
    private fun downloadZipKo(url: String, entryName: String, out: File): Boolean {
        val zip = File(out.absolutePath + ".zip")
        if (!download(url, zip.absolutePath)) {
            zip.delete()
            return false
        }
        val ok = unzipEntry(zip, out, entryName)
        zip.delete()
        return ok && out.exists() && out.length() > 0
    }

    /** Патч vermagic: заменить строку vermagic в .ko под текущее ядро. */
    private fun patchVermagic(path: String, release: String): Boolean {
        return try {
            val f = File(path)
            val data = f.readBytes()
            val needle = "vermagic=".toByteArray(Charsets.US_ASCII)
            var pos = -1
            outer@ for (i in 0..data.size - needle.size) {
                for (j in needle.indices) {
                    if (data[i + j] != needle[j]) continue@outer
                }
                pos = i
                break
            }
            if (pos < 0) return false
            val start = pos + needle.size
            var end = start
            while (end < data.size && data[end] != 0.toByte()) end++
            val old = String(data, start, end - start, Charsets.US_ASCII)
            val suffix = old.split(' ').filter { it.isNotEmpty() && !it[0].isDigit() }.joinToString(" ")
            val new = "$release $suffix".toByteArray(Charsets.US_ASCII)
            if (start + new.size + 1 >= data.size) return false
            val out = data.copyOf()
            System.arraycopy(new, 0, out, start, new.size)
            if (new.size < end - start) {
                // влезает в старую запись — добиваем нулями до её конца
                java.util.Arrays.fill(out, start + new.size, end, 0)
            } else {
                // не влезает: пишем поверх соседних записей .modinfo с нуль-терминатором.
                // Ядро терпит обрезанные записи — ровно так патчил оригинальный скрипт
                // (модуль с 94-символьным vermagic поверх 73-символьного успешно грузился)
                out[start + new.size] = 0
            }
            f.writeBytes(out)
            true
        } catch (e: Exception) {
            Log.e(TAG, "patchVermagic failed", e)
            false
        }
    }

    private fun latestTag(repo: String): String? {
        return try {
            val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.connectTimeout = 15_000
            Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(c.inputStream.bufferedReader().readText())
                ?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        }
    }

    private fun download(url: String, path: String): Boolean {
        return try {
            val f = File(path)
            f.parentFile?.mkdirs()
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = true
            c.connectTimeout = 30_000
            c.inputStream.use { input ->
                f.outputStream().use { input.copyTo(it) }
                // Обрезанная загрузка = битый APK/модуль; сверяемся с Content-Length,
                // когда сервер его даёт
                val expected = c.contentLengthLong
                !(expected > 0 && f.length() != expected)
            } && f.exists() && f.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "NeoKsu"
        private const val CI_REPO = "cctv18/ReSukiSU_CI"
        private const val REMOTE_KSUD = "/data/local/tmp/rmv/ksud"

        /**
         * Найти ksud внутри установленного менеджера (lib/arm64/libksud.so).
         * Это самая свежая версия — у форков в релизах ассета ksud часто нет
         * вовсе (SukiSU v4.2+), а их модулям нужен kallsyms-загрузчик
         * («insmod — load a kernel module with kallsyms access») для
         * неэкспортируемых символов SELinux (policydb_*, sidtab_*, uts_sem).
         * pm вызывается через транспорт (shell-домен): su-демон эксплойта
         * живёт в kernel-контексте и binder-сервисов не видит.
         */
        suspend fun findManagerKsud(ctx: android.content.Context, pkgs: List<String>): String? {
            for (pkg in pkgs.filter { it.isNotEmpty() }.distinct()) {
                val (_, out) = Transport.exec(ctx, "pm path $pkg", timeoutSec = 30)
                val apk = out.lineSequence()
                    .firstOrNull { it.startsWith("package:") }
                    ?.removePrefix("package:")?.trim() ?: continue
                val ksud = apk.substringBeforeLast("/") + "/lib/arm64/libksud.so"
                val (_, probe) = Transport.exec(ctx, "[ -f $ksud ] && echo RMV_YES", timeoutSec = 15)
                if (probe.contains("RMV_YES")) return ksud
            }
            return null
        }

        /**
         * Загрузить модуль в ядро с фолбэками под разные CLI ksud:
         *  1. `ksud insmod <ko> allow_shell=1` каждым доступным ksud —
         *     у свежих это kallsyms-загрузчик; после успеха — late-load
         *     для userspace-настройки (sepolicy, перезапуск менеджера)
         *  2. `late-load ... <путь>` — старый CLI с позиционным аргументом
         *  3. Системный `/system/bin/insmod` — модулям без внешних символов
         *
         * Коды возврата su НЕИСПЬЗУЕМ: su-клиент эксплойта в -c-режиме
         * всегда возвращает 0 (client_main завершается return 0), успех
         * каждой попытки проверяем по /proc/modules.
         */
        suspend fun loadModule(
            ctx: android.content.Context,
            koPath: String,
            managerPkg: String,
            ksudPaths: List<String>,
        ): Pair<Boolean, String> {
            suspend fun loaded(): Boolean {
                val viaExec = Transport.exec(ctx, "grep -i kernelsu /proc/modules 2>/dev/null").second
                return viaExec.isNotBlank() ||
                    Transport.su(ctx, "grep -i kernelsu /proc/modules 2>/dev/null").second.isNotBlank()
            }

            val sb = StringBuilder()
            // userspace-настройка (sepolicy, перезапуск менеджера); провал
            // не критичен — модуль уже в ядре
            suspend fun userspaceSetup(ksud: String) {
                val (_, o) = Transport.su(ctx, "$ksud late-load --allow-shell --package-name $managerPkg")
                if (o.isNotBlank()) sb.append("late-load: ").append(o.trim()).append('\n')
            }

            for (ksud in ksudPaths) {
                val (_, o1) = Transport.su(ctx, "$ksud insmod $koPath allow_shell=1")
                sb.append("ksud insmod [${ksud.substringAfterLast('/')}]: ")
                    .append(o1.trim().ifEmpty { "(нет вывода)" }).append('\n')
                if (loaded()) {
                    userspaceSetup(ksud)
                    return true to sb.toString()
                }
            }
            for (ksud in ksudPaths) {
                val (_, o3) = Transport.su(
                    ctx,
                    "$ksud late-load --allow-shell --package-name $managerPkg $koPath",
                )
                sb.append("ksud late-load [${ksud.substringAfterLast('/')}]: ")
                    .append(o3.trim().ifEmpty { "(нет вывода)" }).append('\n')
                if (loaded()) return true to sb.toString()
            }
            // ETXTBSY («Text file busy»): у свежезаписанного файла ещё держится
            // дескриптор на запись (файловый сканер и т.п.) — ядро отказывается
            // грузить модуль. Пауза, затем копия в новый inode
            val (_, o4) = Transport.su(
                ctx,
                "sleep 1; /system/bin/insmod $koPath allow_shell=1 || " +
                    "{ sleep 2; cp -f $koPath $koPath.try && /system/bin/insmod $koPath.try allow_shell=1; }; " +
                    "rm -f $koPath.try",
            )
            sb.append("insmod: ").append(o4.trim().ifEmpty { "(нет вывода)" }).append('\n')
            if (loaded()) {
                ksudPaths.lastOrNull()?.let { userspaceSetup(it) }
                return true to sb.toString()
            }
            return false to sb.toString()
        }
    }
}
