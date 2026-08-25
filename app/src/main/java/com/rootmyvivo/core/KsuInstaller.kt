package com.rootmyvivo.core

import com.rootmyvivo.core.native.NativeLibs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Варианты KernelSU */
enum class KsuVariant(
    val id: String,
    val displayName: String,
    val packageName: String,
    val repo: String,
    val description: String,
    val koUrlTemplate: String,      // {tag} и {kmi} подставляются
    val ksudAssetName: String?,     // null если нет в релизах
) {
    KERNELSU(
        "kernelsu", "KernelSU",
        "me.weishu.kernelsu",
        "tiann/KernelSU",
        "Оригинал. Самый стабильный.",
        "https://github.com/tiann/KernelSU/releases/download/{tag}/{kmi}_kernelsu.ko",
        null  // ksud внутри APK менеджера
    ),
    KSU_NEXT(
        "ksunext", "KernelSU Next",
        "com.rifsxd.ksunext",
        "rifsxd/KernelSU-Next",
        "Форк с susfs и доп. функциями.",
        "https://github.com/rifsxd/KernelSU-Next/releases/download/{tag}/{kmi}_kernelsu_next.ko",
        null
    ),
    SUKISU(
        "sukisu", "SukiSU Ultra",
        "com.suksukernel.sukisu",
        "SukiSU-Ultra/SukiSU-Ultra",
        "Активный форк, частые обновления.",
        "https://github.com/SukiSU-Ultra/SukiSU-Ultra/releases/download/{tag}/{kmi}_kernelsu_sukisu.ko",
        null
    ),
    RESUKISU(
        "resukisu", "ReSukiSU",
        "com.resukisu.resukisu",
        "ReSukiSU/ReSukiSU",
        "Форк GhostLock-совместимый, улучшенный late-load.",
        "",  // используется CI через lkm-all.zip
        null
    );
    
    companion object {
        fun byId(id: String): KsuVariant =
            entries.find { it.id == id } ?: RESUKISU
    }
}

class KsuInstaller(private val deviceInfo: DeviceInfo) {

    private val workDir = "/data/local/tmp/rmv"
    private val ghApi = "https://api.github.com"

    /**
     * Полная установка выбранного KSU:
     * 1. Скачать последний kernelsu.ko для нашего KMI
     * 2. Скачать последний ksud
     * 3. Патч vermagic под точный uname
     * 4. ksud late-load --allow-shell
     * 5. service.d персистентность
     */
    suspend fun install(
        variant: KsuVariant,
        onProgress: suspend (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {

        onProgress("[*] Подготовка ${variant.displayName}...")

        File("$workDir").mkdirs()

        // ── Шаг 1: скачиваем последний kernelsu.ko ──
        onProgress("[1/5] Скачиваю последний kernelsu.ko (${deviceInfo.kmi})...")
        val koPath = "$workDir/kernelsu_${variant.id}.ko"
        
        // Удаляем старый чтобы скачать свежий
        File(koPath).delete()
        
        if (!downloadLatestKo(variant, deviceInfo.kmi, koPath)) {
            onProgress("[✗] Не удалось скачать kernelsu.ko")
            return@withContext false
        }
        onProgress("[✓] Модуль скачан (${File(koPath).length() / 1024}KB)")

        // ── Шаг 2: скачиваем последний ksud ──
        onProgress("[2/5] Скачиваю ksud...")
        val ksudPath = "$workDir/ksud_${variant.id}"
        if (!downloadLatestKsud(variant, ksudPath)) {
            // Fallback: используем наш встроенный ksud
            val embedded = NativeLibs.getPath("ksud")
            if (File(embedded).exists()) {
                execCommand("cp", embedded, ksudPath)
                onProgress("[!] Использую встроенный ksud")
            } else {
                onProgress("[✗] ksud недоступен")
                return@withContext false
            }
        }
        execCommand("chmod", "755", ksudPath)

        // ── Шаг 3: патч vermagic ──
        onProgress("[3/5] Адаптация модуля под ядро ${deviceInfo.kernel.take(30)}...")
        if (!patchVermagic(koPath, deviceInfo.kernel)) {
            onProgress("[✗] Не удалось пропатчить vermagic")
            return@withContext false
        }
        onProgress("[✓] Vermagic пропатчен")

        // ── Шаг 4: загрузка через ksud ──
        onProgress("[4/5] Загрузка KernelSU в ядро...")
        val (_, loadOutput) = execCommand(
            "su", "-c",
            "$ksudPath late-load --allow-shell --package-name ${variant.packageName} $koPath"
        )
        
        delay(3000)

        // ── Шаг 5: верификация + персистентность ──
        onProgress("[5/5] Проверка результата...")
        
        if (isSuAvailable()) {
            val (_, idOut) = execCommand("su", "-c", "id")
            
            onProgress("[✓✓✓] ROOT АКТИВЕН!")
            onProgress("[✓] $idOut.trim()")
            
            // Устанавливаем персистентность
            setupPersistence(variant, koPath, ksudPath)
            onProgress("[✓] Персистентность настроена")
            
            true
        } else {
            onProgress("[!] Модуль загружен но su недоступен.")
            onProgress("[i] Попробуй SOFT REBOOT — KSU останется в ядре.")
            false
        }
    }

    /**
     * Скачивает ПОСЛЕДНИЙ kernelsu.ko с GitHub Releases
     * Для каждого варианта — своя логика.
     */
    private suspend fun downloadLatestKo(variant: KsuVariant, kmi: String, outPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                when (variant.id) {
                    "kernelsu" -> {
                        val tag = fetchLatestTag("${variant.repo}")
                        if (tag.isEmpty()) return@withContext false
                        val url = variant.koUrlTemplate
                            .replace("{tag}", tag).replace("{kmi}", kmi)
                        downloadFile(url, outPath)
                    }
                    
                    "ksunext" -> {
                        val tag = fetchLatestTag(variant.repo)
                        if (tag.isEmpty()) return@withContext false
                        // KSU-Next может иметь разные названия ассетов — пробуем варианты
                        val patterns = listOf(
                            "${kmi}_kernelsu_next.ko",
                            "${kmi}_kernelsu.ko", 
                            "${kmi}_kernel_su_next.ko"
                        )
                        for (pattern in patterns) {
                            val url = "https://github.com/${variant.repo}/releases/download/$tag/$pattern"
                            if (tryDownload(url, outPath)) return@withContext true
                        }
                        false
                    }
                    
                    "sukisu" -> {
                        val tag = fetchLatestTag(variant.repo)
                        if (tag.isEmpty()) return@withContext false
                        val patterns = listOf(
                            "${kmi}_kernelsu_sukisu.ko",
                            "${kmi}_kernelsu.ko",
                            "${kmi}_susfs_kernelsu.ko"
                        )
                        for (pattern in patterns) {
                            val url = "https://github.com/${variant.repo}/releases/download/$tag/$pattern"
                            if (tryDownload(url, outPath)) return@withContext true
                        }
                        false
                    }
                    
                    "resukisu" -> {
                        // ReSukiSU использует CI репо с lkm-all.zip
                        val ciRepo = "cctv18/ReSukiSU_CI"
                        val tag = fetchLatestTag(ciRepo)
                        if (tag.isEmpty()) return@withContext false
                        
                        val zipPath = "$workDir/lkm-all.zip"
                        downloadFile(
                            "https://github.com/$ciRepo/releases/download/$tag/lkm-all.zip",
                            zipPath
                        )
                        
                        // Распаковываем нужный .ko
                        val (_, unzipOut) = execCommand(
                            "sh", "-c",
                            "cd $workDir && unzip -o lkm-all.zip '${kmi}_kernelsu.ko' && mv '${kmi}_kernelsu.ko' '$outPath' && rm lkm-all.zip"
                        )
                        File(outPath).exists()
                    }
                    
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Скачивает последний ksud бинарь для варианта.
     * Приоритет: собственный ksud варианта > встроенный fallback.
     */
    private suspend fun downloadLatestKsud(variant: KsuVariant, outPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                when (variant.id) {
                    "resukisu" -> {
                        // ReSukiSU CI имеет готовый ksud
                        val tag = fetchLatestTag("cctv18/ReSukiSU_CI")
                        val url = "https://github.com/cctv18/ReSukiSU_CI/releases/download/$tag/ksud-aarch64-linux-android.zip"
                        val zipPath = "$workDir/ksud_dl.zip"
                        downloadFile(url, zipPath)
                        execCommand("sh", "-c",
                            "cd $workDir && unzip -o ksud_dl.zip 'aarch64-linux-android/release/ksud' && mv aarch64-linux-android/release/ksud '$outPath' && rm -rf aarch64-linux-android ksud_dl.zip")
                    }
                    "kernelsu" -> {
                        // Официальный KernelSU: ksud внутри APK как libksud.so
                        val tag = fetchLatestTag(variant.repo)
                        // Пробуем скачать напрямую или извлекаем из APK
                        val apkUrl = "https://github.com/${variant.repo}/releases/download/$tag/KernelSU_v${tag.removePrefix("v")}_${deviceInfo.arch}-release.apk"
                        // Сложный путь — пока используем fallback
                        false
                    }
                    else -> false
                }
            } catch (_: Exception) { false }
        }

    /** Верmagic-патч через нативный код или shell fallback */
    private suspend fun patchVermagic(koPath: String, release: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                nativePatchVermagic(koPath, release)
            } catch (_: Throwable) {
                patchVermagicPython(koPath, release)
            }
        }

    @JvmStatic
    private external fun nativePatchVermagic(path: String, release: String): Boolean

    private suspend fun patchVermagicPython(path: String, release: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val script = """
import sys,os
data=open('$path','rb').read()
i=data.find(b'vermagic=')
if i<0:sys.exit(1)
end=data.find(b'\x00',i)
old=data[i+9:end].decode()
suffix=[w for w in old.split()if not w[0].isdigit()]
new=f"$release {' '.join(suffix)}".encode()
space=end-(i+9)
if len(new)>space:sys.exit(1)
b=bytearray(data)
b[i+9:i+9+len(new)]=new
b[i+9+len(new):end]=b'\x00'*(end-i-9-len(new))
open('$path','wb').write(bytes(b))
print("OK")
"""
                val proc = ProcessBuilder("python3", "-c", script.trim().replace("\n", "; "))
                    .redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor() == 0 && out.contains("OK")
            } catch (_: Exception) { false }
        }

    /** Настройка автозагрузки через /data/adb/service.d */
    private suspend fun setupPersistence(variant: KsuVariant, koPath: String, ksudPath: String) =
        withContext(Dispatchers.IO) {
            execCommand("su", "-c", "mkdir -p /data/adb/service.d")
            val script = """
#!/system/bin/sh
# RootMyVivo persistence — ${variant.displayName}
KO=$koPath
KSUD=$ksudPath
if ! grep -qi kernelsu /proc/modules 2>/dev/null; then
  insmod "\$KO" allow_shell=1 2>/dev/null
fi
[ -x "\$KSUD" ] && { "\$KSUD" post-fs-data 2>/dev/null; "\$KSUD" services 2>/dev/null; }
""".trimIndent()
            execCommand("su", "-c",
                "cat > /data/adb/service.d/rmv-persist.sh << 'RMVEOF'\n$script\nRMVEOF\nchmod 755 /data/adb/service.d/rmv-persist.sh")
        }

    // ═══ Helpers ═══

    private suspend fun fetchLatestTag(repo: String): String =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$ghApi/repos/$repo/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 15000
                val body = conn.inputStream.bufferedReader().readText()
                Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
            } catch (_: Exception) { "" }
        }

    private suspend fun downloadFile(url: String, path: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                File(path).parentFile?.mkdirs()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 30000
                conn.instanceFollowRedirects = true
                conn.inputStream.use { input ->
                    File(path).outputStream().use { output ->
                        input.copyTo(output, bufferSize = 65536)
                    }
                }
                File(path).exists() && File(path).length() > 0
            } catch (_: Exception) { false }
        }

    private suspend fun tryDownload(url: String, path: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 10000
                if (conn.responseCode == 200) {
                    downloadFile(url, path)
                } else false
            } catch (_: Exception) { false }
        }

    companion object {
        init {
            try { System.loadLibrary("rmv_native") } catch (_: Throwable) {}
        }
    }
}
