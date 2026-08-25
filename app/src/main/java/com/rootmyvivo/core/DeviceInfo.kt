package com.rootmyvivo.core

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Информация об устройстве — определяет модель, ядро, KMI,
 * проверяет поддержку эксплойта.
 */
data class DeviceInfo(
    val model: String,
    val marketName: String,
    val brand: String,
    val rom: String,
    val kernel: String,
    val kernelShort: String,
    val kmi: String,
    val securityPatch: String,
    val fingerprint: String,
    val soc: String,
    val arch: String,
) {
    val configId: String get() = "$model-${kernel.hashCode().toString(16).take(8)}"
    
    companion object {
        fun detect(): DeviceInfo {
            val kernel = System.getProperty("os.arch")?.let { _ ->
                Runtime.getRuntime().exec("uname -r").inputStream.bufferedReader().readText().trim()
            } ?: ""
            
            val short = kernel.split("-").take(3).joinToString(".").substringBeforeLast(".")
                .let { v -> 
                    // "6.6.89-android15" → "6.6.89"
                    Regex("""(\d+\.\d+\.\d+)""").find(kernel)?.groupValues?.get(1) ?: v
                }
            
            val kmi = Regex("""android\d+-\d+\.\d+""").find(kernel)?.value ?: ""
            
            return DeviceInfo(
                model = Build.DEVICE,
                marketName = Build.MODEL,
                brand = Build.BRAND.lowercase(),
                rom = Build.DISPLAY,
                kernel = kernel,
                kernelShort = short,
                kmi = kmi,
                securityPatch = Build.VERSION.SECURITY_PATCH,
                fingerprint = Build.FINGERPRINT,
                soc = Build.HARDWARE,
                arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "",
            )
        }
    }
}

/** Результат проверки поддержки */
data class SupportCheck(
    val supported: Boolean,
    val rootAlready: Boolean,
    val issues: List<String>,
)

fun checkSupport(info: DeviceInfo): SupportCheck {
    val issues = mutableListOf<String>()
    var supported = true
    
    val alreadyRoot = try {
        ProcessBuilder("su", "-c", "true").start().waitFor() == 0
    } catch (_: Exception) { false }
    
    // Проверка версии ядра против фикса CVE-2026-43499
    val minor = info.kernelShort.split(".").getOrNull(2)?.toIntOrNull() ?: 0
    when {
        info.kernelShort.startsWith("6.6.") && minor >= 140 -> {
            issues += "Ядро ≥6.6.140: CVE-2026-43499 закрыт"
            supported = false
        }
        info.kernelShort.startsWith("5.15.") -> {
            issues += "5.15: GhostLock ограничен, нужен Adreno-вектор (в разработке)"
        }
        info.kernelShort.startsWith("6.1.") && minor >= 145 -> {
            issues += "6.1.145+: возможен бэкпорт фикса, проверь вручную"
        }
    }
    
    if (!info.kernel.contains("android")) {
        issues += "Не GKI ядро — поддержка ограничена"
    }
    
    return SupportCheck(supported, alreadyRoot, issues)
}

/** Выполнить команду через su или напрямую */
suspend fun execCommand(vararg cmd: String): Pair<Int, String> = 
    withContext(Dispatchers.IO) {
        try {
            val proc = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            Pair(proc.waitFor(), output)
        } catch (e: Exception) {
            Pair(-1, e.message ?: "error")
        }
    }

suspend fun isSuAvailable(): Boolean = withContext(Dispatchers.IO) {
    try {
        ProcessBuilder("su", "-c", "true")
            .start()
            .waitFor() == 0
    } catch (_: Exception) { false }
}
