package com.rootmyvivo.core

import com.rootmyvivo.core.native.NativeLibs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class KsuVariant(
    val id: String,
    val displayName: String,
    val packageName: String,
    val repo: String,
) {
    KERNELSU("kernelsu", "KernelSU", "me.weishu.kernelsu", "tiann/KernelSU"),
    KSU_NEXT("ksunext", "KernelSU Next", "com.rifsxd.ksunext", "rifsxd/KernelSU-Next"),
    SUKISU("sukisu", "SukiSU Ultra", "com.suksukernel.sukisu", "SukiSU-Ultra/SukiSU-Ultra"),
    RESUKISU("resukisu", "ReSukiSU", "com.resukisu.resukisu", "ReSukiSU/ReSukiSU");

    companion object {
        fun byId(id: String): KsuVariant = entries.find { it.id == id } ?: RESUKISU
    }
}

class KsuInstaller(private val deviceInfo: DeviceInfo) {

    suspend fun install(variant: KsuVariant, onProgress: suspend (String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            onProgress("[*] ${variant.displayName}...")

            // Рабочая директория в filesDir — доступна приложению без root
            val workDir = File(
                com.rootmyvivo.RmvApp.instance.filesDir, "payloads"
            ).apply { mkdirs() }
            val koPath = "$workDir/kernelsu_${variant.id}.ko"
            val ksudPath = NativeLibs.getPath("ksud")

            onProgress("[1/4] Скачиваю kernelsu.ko (${deviceInfo.kmi})...")
            File(koPath).delete()
            if (!downloadKo(variant, deviceInfo.kmi, koPath, workDir)) {
                onProgress("[✗] Не удалось скачать модуль")
                return@withContext false
            }

            onProgress("[2/4] Адаптация под ядро...")
            if (!patchVermagic(koPath, deviceInfo.kernel)) {
                onProgress("[✗] Vermagic патч не удался")
                return@withContext false
            }

            onProgress("[3/4] Загрузка в ядро...")
            execCommand("chmod", "755", ksudPath)
            execCommand("su", "-c",
                "$ksudPath late-load --allow-shell --package-name ${variant.packageName} $koPath")
            delay(3000)

            onProgress("[4/4] Проверка...")
            if (!isSuAvailable()) {
                onProgress("[!] Модуль загружен но su недоступен. Попробуй soft reboot.")
                return@withContext false
            }

            onProgress("[✓✓✓] ROOT АКТИВЕН!")
            setupPersistence(variant, koPath, ksudPath)
            true
        }

    private suspend fun downloadKo(
        variant: KsuVariant, kmi: String, outPath: String, workDir: File
    ): Boolean =
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
                        val tag = fetchTag(CI_REPO)
                        val zip = "$workDir/lkm.zip"
                        downloadFile("https://github.com/$CI_REPO/releases/download/$tag/lkm-all.zip", zip)
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
            try { patchKt(path, release) } catch (_: Throwable) { patchPy(path, release) }
        }

    /** Чистая Kotlin реализация — не требует NDK */
    private fun patchKt(path: String, release: String): Boolean {
        val f = File(path)
        val data = f.readBytes()
        val needle = "vermagic=".toByteArray(Charsets.US_ASCII)

        var pos = -1
        for (i in 0..data.size - needle.size) {
            var match = true
            for (j in needle.indices) {
                if (data[i + j] != needle[j]) { match = false; break }
            }
            if (match) { pos = i; break }
        }
        if (pos < 0) return false

        val valStart = pos + needle.size
        var valEnd = valStart
        while (valEnd < data.size && data[valEnd] != 0.toByte()) valEnd++

        // Старая строка vermagic
        val oldStr = String(data, valStart, valEnd - valStart, Charsets.US_ASCII)
        // Суффикс без версии ядра (SMP preempt mod_unload ...)
        val suffix = oldStr.split(' ').filter { it.isNotEmpty() && !it[0].isDigit() }.joinToString(" ")
        val newVm = "$release $suffix".toByteArray(Charsets.US_ASCII)

        val space = valEnd - valStart
        if (newVm.size > space) return false

        val out = data.copyOf()
        System.arraycopy(newVm, 0, out, valStart, newVm.size)
        java.util.Arrays.fill(out, valStart + newVm.size, valEnd, 0)

        f.writeBytes(out)
        return true
    }

    private fun patchPy(path: String, release: String): Boolean {
        return try {
            val code = listOf(
                "d=open('$path','rb').read()",
                "i=d.find(b'vermagic=')",
                "if i<0:sys.exit(1)",
                "e=d.find(b'\\x00',i)",
                "old=d[i+9:e].decode()",
                "suf=[w for w in old.split() if not w[0].isdigit()]",
                "new=f\"$release {' '.join(suf)}\".encode()",
                "sp=e-(i+9)",
                "if len(new)>sp:sys.exit(1)",
                "b=bytearray(d)",
                "b[i+9:i+9+len(new)]=new",
                "b[i+9+len(new):e]=b'\\x00'*(e-i-9-len(new))",
                "open('$path','wb').write(bytes(b))",
                "print('OK')"
            ).joinToString("\n")
            // Add import at top
            val fullCode = "import sys\n$code"
            val proc = ProcessBuilder("python3", "-c", fullCode)
                .redirectErrorStream(true).start()
            proc.waitFor() == 0
        } catch (_: Exception) { false }
    }

    private suspend fun setupPersistence(variant: KsuVariant, koPath: String, ksudPath: String) =
        withContext(Dispatchers.IO) {
            execCommand("su", "-c", "mkdir -p /data/adb/service.d")
            val scriptBody = buildString {
                appendLine("#!/system/bin/sh")
                appendLine("# RootMyVivo persistence — ${variant.displayName}")
                appendLine("KO=$koPath")
                appendLine("KSUD=$ksudPath")
                appendLine("""if ! grep -qi kernelsu /proc/modules 2>/dev/null; then""")
                appendLine("""  insmod "\${'$'}KO" allow_shell=1 2>/dev/null""")
                appendLine("fi")
                appendLine("""[ -x "\${'$'}KSUD" ] && "\${'$'}KSUD" post-fs-data 2>/dev/null""")
            }
            execCommand("su", "-c",
                "cat > /data/adb/service.d/rmv-persist.sh << 'RMVEOF'\n$scriptBody\nRMVEOF\nchmod 755 /data/adb/service.d/rmv-persist.sh")
        }

    companion object {
        private const val GH_API = "https://api.github.com"
        private const val CI_REPO = "cctv18/ReSukiSU_CI"

        private suspend fun fetchTag(repo: String): String =
            withContext(Dispatchers.IO) {
                try {
                    val conn = URL("$GH_API/repos/$repo/releases/latest").openConnection() as HttpURLConnection
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.connectTimeout = 15000
                    val body = conn.inputStream.bufferedReader().readText()
                    Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: ""
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
                    c.requestMethod = "HEAD"
                    c.connectTimeout = 10000
                    if (c.responseCode == 200) downloadFile(url, path) else false
                } catch (_: Exception) { false }
            }
    }
}
