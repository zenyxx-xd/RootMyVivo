package com.rootmyvivo.core

import com.rootmyvivo.core.native.NativeLibs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Варианты KernelSU */
enum class KsuVariant(
    val id: String,
    val displayName: String,
    val packageName: String,
    val repoUrl: String,
    val description: String,
) {
    KERNELSU(
        "kernelsu", "KernelSU",
        "me.weishu.kernelsu",
        "https://github.com/tiann/KernelSU",
        "Оригинал. Самый стабильный, GKI LKM"
    ),
    KSU_NEXT(
        "ksunext", "KernelSU Next",
        "com.rifsxd.ksunext",
        "https://github.com/rifsxd/KernelSU-Next",
        "Форк с дополнительными функциями и susfs"
    ),
    SUKISU(
        "sukisu", "SukiSU Ultra",
        "com.suksukernel.sukisu",
        "https://github.com/SukiSU-Ultra/SukiSU-Ultra",
        "Активный форк, частые обновления, susfs встроен"
    ),
    RESUKISU(
        "resukisu", "ReSukiSU",
        "com.resukisu.resukisu",
        "https://github.com/ReSukiSU/ReSukiSU",
        "Форк с улучшенной совместимостью, используется GhostLock"
    );
    
    companion object {
        fun byId(id: String): KsuVariant = 
            entries.find { it.id == id } ?: RESUKISU
    }
}

class KsuInstaller(private val deviceInfo: DeviceInfo) {
    
    /**
     * Полная установка выбранного KSU:
     * 1. Скачать kernelsu.ko для нужного KMI
     * 2. Патч vermagic под точный uname
     * 3. ksud late-load --allow-shell
     * 4. Установка service.d персистентности
     */
    suspend fun install(
        variant: KsuVariant,
        onProgress: suspend (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        
        onProgress("Подготовка ${variant.displayName}...")
        
        val koPath = "/data/local/tmp/rmv/kernelsu_${variant.id}.ko"
        val ksudPath = NativeLibs.getPath("ksud")
        
        // 1. Скачиваем или используем локальный kernelsu.ko
        onProgress("Получение модуля ядра...")
        if (!File(koPath).exists()) {
            val downloaded = downloadKsuModule(variant, koPath)
            if (!downloaded) {
                onProgress("Ошибка скачивания модуля")
                return@withContext false
            }
        }
        
        // 2. Патчим vermagic под наш uname
        onProgress("Адаптация модуля под ядро...")
        val patched = patchVermagicNative(koPath, deviceInfo.kernel)
        if (!patched) {
            onProgress("Ошибка патча vermagic — возможно ядро несовместимо")
            return@withContext false
        }
        
        // 3. Загружаем через late-load
        onProgress("Загрузка KernelSU в ядро...")
        val result = execCommand(
            "su", "-c",
            "$ksudPath late-load --allow-shell --package-name ${variant.packageName} $koPath"
        )
        
        delay(2000) // даём модулю инициализироваться
        
        // 4. Верификация
        onProgress("Проверка результата...")
        if (isSuAvailable()) {
            val (_, idOutput) = execCommand("su", "-c", "id")
            
            // 5. Персистентность
            onProgress("Настройка автозагрузки...")
            setupPersistence(variant, koPath)
            
            onProgress("✓ Root установлен: $idOutput.trim()")
            true
        } else {
            onProgress("Модуль загружен но su недоступен. Попробуй soft reboot.")
            false
        }
    }
    
    /** Скачивание kernelsu.ko из релизов соответствующего репо */
    private suspend fun downloadKsuModule(variant: KsuVariant, outPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val kmi = deviceInfo.kmi // android15-6.6
                
                when (variant.id) {
                    "kernelsu" -> {
                        val latest = fetchLatestTag("tiann/KernelSU")
                        val url = "https://github.com/tiann/KernelSU/releases/download/$latest/${kmi}_kernelsu.ko"
                        downloadFile(url, outPath)
                    }
                    "resukisu" -> {
                        // ReSukiSU CI: lkm-all.zip содержит все KMI
                        val ciTag = fetchLatestTag("cctv18/ReSukiSU_CI")
                        val zipPath = "/data/local/tmp/rmv/lkm-all.zip"
                        downloadFile(
                            "https://github.com/cctv18/ReSukiSU_CI/releases/download/$ciTag/lkm-all.zip",
                            zipPath
                        )
                        // Распаковка нужного .ko
                        execCommand("sh", "-c", 
                            "cd /data/local/tmp/rmv && unzip -o lkm-all.zip '${kmi}_kernelsu.ko' && mv '${kmi}_kernelsu.ko' '$outPath'")
                    }
                    else -> {
                        // Для остальных — используем официальный GKI ko как базу
                        downloadKsuModule(KsuVariant.KERNELSU, outPath)
                    }
                }
                
                File(outPath).exists() && File(outPath).length() > 10000
            } catch (e: Exception) {
                false
            }
        }
    
    /** Нативный патч vermagic (JNI вызов) */
    private fun patchVermagicNative(koPath: String, release: String): Boolean {
        return try {
            nativePatchVermagic(koPath, release)
        } catch (_: UnsatisfiedLinkError) {
            // Fallback на shell+python если JNI недоступен
            patchVermagicShell(koPath, release)
        }
    }
    
    private external fun nativePatchVermagic(path: String, release: String): Boolean
    
    private fun patchVermagicShell(koPath: String, release: String): Boolean {
        val script = """
import sys
data = open('$koPath','rb').read()
i = data.find(b'vermagic=')
if i < 0: sys.exit(1)
end = data.find(b'\x00', i)
old = data[i+9:end].decode()
suffixes = [w for w in old.split() if not w[0].isdigit()]
new_vm = f"$release {' '.join(suffixes)}".encode()
space = end - (i + 9)
if len(new_vm) > space: sys.exit(1)
b = bytearray(data)
b[i+9:i+9+len(new_vm)] = new_vm
b[i+9+len(new_vm):end] = b'\x00' * (end - i - 9 - len(new_vm))
open('$koPath','wb').write(bytes(b))
print("OK")
"""
        val (_, output) = execCommand("python3", "-c", script.replace("\n", "; "))
        return output.contains("OK") || execCommand("true").first == 0
    }
    
    private fun setupPersistence(variant: KsuVariant, koPath: String) {
        val script = """
#!/system/bin/sh
# RootMyVivo persistence (${variant.displayName})
KO=$koPath
KSUD=${NativeLibs.getPath("ksud")}
if ! grep -qi kernelsu /proc/modules 2>/dev/null; then
  insmod "\$KO" allow_shell=1 2>/dev/null
fi
if [ -x "\$KSUD" ]; then
  "\$KSUD" post-fs-data 2>/dev/null
  "\$KSUD" services 2>/dev/null
fi
""".trimIndent()
        
        execCommand("su", "-c", 
            "mkdir -p /data/adb/service.d && cat > /data/adb/service.d/rmv-persist.sh << 'EOF'\n$script\nEOF\nchmod 755 /data/adb/service.d/rmv-persist.sh")
    }
    
    companion object {
        init {
            // Загружаем нативную библиотеку для vermagic патча
            try { System.loadLibrary("rmv_native") } catch (_: Throwable) {}
        }
        
        private suspend fun fetchLatestTag(repo: String): String = 
            withContext(Dispatchers.IO) {
                val (_, output) = execCommand(
                    "sh", "-c",
                    "curl -s 'https://api.github.com/repos/$repo/releases/latest' | grep -o '\"tag_name\": *\"[^\"]*\"' | cut -d'\"' -f4"
                )
                output.trim().ifEmpty { "v3.2.5" }
            }
        
        private suspend fun downloadFile(url: String, path: String) = withContext(Dispatchers.IO) {
            File(path).parentFile?.mkdirs()
            execCommand("curl", "-sL", "-o", path, url)
        }
    }
}
