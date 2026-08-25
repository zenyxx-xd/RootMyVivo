package com.rootmyvivo.core

import com.rootmyvivo.core.native.NativeLibs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

enum class KsuVariant(
    val id: String,
    val displayName: String,
    val packageName: String,
    val repo: String,
    val description: String,
) {
    KERNELSU("kernelsu", "KernelSU", "me.weishu.kernelsu", "tiann/KernelSU", "Оригинал. Стабильный."),
    KSU_NEXT("ksunext", "KernelSU Next", "com.rifsxd.ksunext", "rifsxd/KernelSU-Next", "Форк с susfs."),
    SUKISU("sukisu", "SukiSU Ultra", "com.suksukernel.sukisu", "SukiSU-Ultra/SukiSU-Ultra", "Активный форк."),
    RESUKISU("resukisu", "ReSukiSU", "com.resukisu.resukisu", "ReSukiSU/ReSukiSU", "GhostLock-совместимый.");

    companion object {
        fun byId(id: String): KsuVariant = entries.find { it.id == id } ?: RESUKISU
    }
}

class KsuInstaller(private val deviceInfo: DeviceInfo) {

    private val workDir = "/data/local/tmp/rmv"
    private val ghApi = "https://api.github.com"

    suspend fun install(variant: KsuVariant, onProgress: suspend (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            onProgress("[*] ${variant.displayName}...")

            File(workDir).mkdirs()
            val koPath = "$workDir/kernelsu_${variant.id}.ko"
            val ksudPath = NativeLibs.getPath("ksud")

            // 1: kernelsu.ko
            onProgress("[1/4] Скачиваю kernelsu.ko (${deviceInfo.kmi})...")
            File(koPath).delete()
            if (!downloadKo(variant, deviceInfo.kmi, koPath)) {
                onProgress("[✗] Не удалось скачать модуль")
                return@withContext false
            }

            // 2: vermagic
            onProgress("[2/4] Адаптация под ядро...")
            if (!patchVermagic(koPath, deviceInfo.kernel)) {
                onProgress("[✗] Vermagic патч не удался")
                return@withContext false
            }

            // 3: late-load
            onProgress("[3/4] Загрузка в ядро...")
            execCommand("chmod", "755", ksudPath)
            execCommand("su", "-c",
                "$ksudPath late-load --allow-shell --package-name ${variant.packageName} $koPath")
            delay(3000)

            // 4: verify + persist
            onProgress("[4/4] Проверка...")
            if (!isSuAvailable()) {
                onProgress("[!] Модуль загружен но su недоступен. Попробуй soft reboot.")
                return@withContext false
            }

            onProgress("[✓✓✓] ROOT АКТИВЕН!")
            setupPersistence(variant, koPath, ksudPath)
            true
        }

    private suspend fun downloadKo(variant: KsuVariant, kmi: String, outPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                when (variant.id) {
                    "kernelsu" -> {
                        val tag = fetchTag(variant.repo)
                        downloadFile(
                            "https://github.com/tiann/KernelSU/releases/download/$tag/${kmi}_kernelsu.ko",
                            outPath)
                    }
                    "ksunext" -> {
                        val tag = fetchTag(variant.repo)
                        for (p in listOf("${kmi}_kernelsu_next.ko", "${kmi}_kernelsu.ko")) {
                            if (tryDl("https://github.com/${variant.repo}/releases/download/$tag/$p", outPath)) return@withContext true
                        }
                        false
                    }
                    "sukisu" -> {
                        val tag = fetchTag(variant.repo)
                        for (p in listOf("${kmi}_kernelsu_sukisu.ko", "${kmi}_kernelsu.ko")) {
                            if (tryDl("https://github.com/${variant.repo}/releases/download/$tag/$p", outPath)) return@withContext true
                        }
                        false
                    }
                    "resukisu" -> {
                        val tag = fetchTag("cctv18/ReSukiSU_CI")
                        val zip = "$workDir/lkm.zip"
                        downloadFile("https://github.com/cctv18/ReSukiSU_CI/releases/download/$tag/lkm-all.zip", zip)
                        execCommand("sh", "-c",
                            "cd $workDir && unzip -o lkm.zip '${kmi}_kernelsu.ko' && mv '${kmi}_kernelsu.ko' '$outPath'")
                        File(outPath).exists() && File(outPath).length() > 0L
                    }
                    else -> false
                }
            } catch (_: Exception) { false }
        }

    private suspend fun patchVermagic(path: String, release: String): Boolean =
        withContext(Dispatchers.IO) {
            try { nativePatch(path, release) } catch (_: Throwable) { patchPy(path, release) }
        }

    private external fun nativePatch(path: String, release: String): Boolean

    private fun patchPy(path: String, release: String): Boolean {
        return try {
            val code = "import sys\n" +
                "d=open('$path','rb').read()\n" +
                "i=d.find(b'vermagic=')\n" +
                "if i<0:sys.exit(1)\n" +
                "e=d.find(b'\\x00',i)\n" +
                "old=d[i+9:e].decode()\n" +
                "suf=[w for w in old.split() if not w[0].isdigit()]\n" +
                "new=f\"$release {' '.join(suf)}\".encode()\n" +
                "sp=e-(i+9)\n" +
                "if len(new)>sp:sys.exit(1)\n" +
                "b=bytearray(d);b[i+9:i+9+len(new)]=new;b[i+9+len(new):e]=b'\\x00'*(e-i-9-len(new))\n" +
                "open('$path','wb').write(bytes(b));print('OK')"
            val proc = ProcessBuilder("python3", "-c", code).redirectErrorStream(true).start()
            proc.waitFor() == 0
        } catch (_: Exception) { false }
    }

    private suspend fun setupPersistence(variant: KsuVariant, koPath: String, ksudPath: String) =
        withContext(Dispatchers.IO) {
            execCommand("su", "-c", "mkdir -p /data/adb/service.d")
            // Экранируем $ чтобы Kotlin их не интерполировал
            val scriptBody = "#!/system/bin/sh\n" +
                "# RootMyVivo — " + variant.displayName + "\n" +
                "KO=" + koPath + "\n" +
                "KSUD=" + ksudPath + "\n" +
                "if ! grep -qi kernelsu /proc/modules 2>/dev/null; then\n" +
                "  insmod \"\$KO\" allow_shell=1 2>/dev/null\n" +
                "fi\n" +
                "[ -x \"\$KSUD\" ] && \"\$KSUD\" post-fs-data 2>/dev/null\n"
            execCommand("su", "-c",
                "cat > /data/adb/service.d/rmv-persist.sh << 'RMVEOF'\n$scriptBody\nRMVEOF\nchmod 755 /data/adb/service.d/rmv-persist.sh")
        }

    companion object {
        init { try { System.loadLibrary("rmv_native") } catch (_: Throwable) {} }

        private suspend fun fetchTag(repo: String): String =
            withContext(Dispatchers.IO) {
                try {
                    val conn = URL("$ghApi/repos/$repo/releases/latest").openConnection() as HttpURLConnection
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.connectTimeout = 15000
                    Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(conn.inputStream.bufferedReader().readText())?.groupValues?.get(1) ?: ""
                } catch (_: Exception) { "" }
            }

        private suspend fun downloadFile(url: String, path: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val f = File(path)
                    f.parentFile?.mkdirs()
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 30000
                    conn.instanceFollowRedirects = true
                    conn.inputStream.use { inp ->
                        f.outputStream().use { out -> inp.copyTo(out, 65536) }
                    }
                    f.exists() && f.length() > 0L
                } catch (_: Exception) { false }
            }

        private suspend fun tryDl(url: String, path: String): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.requestMethod = "HEAD"; c.connectTimeout = 10000
                    if (c.responseCode == 200) downloadFile(url, path) else false
                } catch (_: Exception) { false }
            }
    }
}
