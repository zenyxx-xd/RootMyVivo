package com.rootmyvivo.core

import android.os.Build
import java.io.File

/**
 * Информация об устройстве.
 * Ядро читается из /proc/version (работает без root из app контекста).
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
            // Ядро: /proc/version содержит полную строку Linux version
            val procVersion = try {
                File("/proc/version").readText().trim()
            } catch (_: Exception) { "" }

            // Извлекаем uname -r: "Linux version 6.6.89-android15-8-g... (builder@host)"
            val kernel = Regex("""Linux version (\S+)""").find(procVersion)
                ?.groupValues?.get(1) ?: ""

            // Трёхчастная версия: 6.6.89
            val kernelShort = Regex("""(\d+\.\d+\.\d+)""").find(kernel)?.groupValues?.get(1) ?: ""

            // KMI: android15-6.6
            val kmi = Regex("""android\d+-\d+\.\d+""").find(kernel)?.value ?: ""

            return DeviceInfo(
                model = Build.DEVICE,
                marketName = Build.MODEL,
                brand = Build.BRAND.lowercase(),
                rom = Build.DISPLAY,
                kernel = kernel,
                kernelShort = kernelShort,
                kmi = kmi,
                securityPatch = Build.VERSION.SECURITY_PATCH,
                fingerprint = Build.FINGERPRINT,
                soc = getSoC(),
                arch = Build.SUPPORTED_ABIS.firstOrNull() ?: "",
            )
        }

        private fun getSoC(): String {
            val board = Build.HARDWARE.lowercase()
            return when {
                board.contains("kera") || board.contains("canary") -> "SM8750"
                board.contains("sun") || board.contains("pineapple") -> "SM8650"
                board.contains("kalama") -> "SM8550"
                board.contains("taro") -> "SM8475"
                board.contains("mt6899") || board.contains("mt6991") -> "Dimensity 9400"
                else -> Build.HARDWARE
            }
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
        Runtime.getRuntime().exec(arrayOf("su", "-c", "true")).waitFor() == 0
    } catch (_: Exception) { false }

    if (info.kernel.isEmpty()) {
        issues += "Не удалось прочитать версию ядра"
        supported = false
    }

    if (info.kmi.isEmpty() && info.kernel.isNotEmpty()) {
        issues += "Не GKI ядро (KMI не определён)"
    }

    // CVE-2026-43499 фикс в 6.6.140+
    when {
        info.kernelShort.startsWith("6.6.") -> {
            val minor = info.kernelShort.split(".").getOrNull(2)?.toIntOrNull() ?: 0
            if (minor >= 140) {
                issues += "Ядро ≥6.6.140: CVE-2026-43499 закрыт. Эксплойт неприменим."
                supported = false
            }
        }
        info.kernelShort.startsWith("6.1.") -> {
            val minor = info.kernelShort.split(".").getOrNull(2)?.toIntOrNull() ?: 0
            if (minor >= 145) {
                issues += "6.1.145+: возможен бэкпорт фикса"
            }
        }
        info.kernelShort.startsWith("5.15.") -> {
            issues += "5.15: нужен Adreno KGSL вектор (в разработке)"
        }
    }

    return SupportCheck(supported, alreadyRoot, issues)
}

/** Проверка доступности su */
suspend fun isSuAvailable(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        Runtime.getRuntime().exec(arrayOf("su", "-c", "true")).waitFor() == 0
    } catch (_: Exception) { false }
}
