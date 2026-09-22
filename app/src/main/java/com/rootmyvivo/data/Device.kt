package com.rootmyvivo.data

import android.os.Build

/** Информация об устройстве. Ядро — из uname(), доступно без root. */
data class DeviceInfo(
    val model: String,
    val marketName: String,
    val brand: String,
    val rom: String,
    val kernel: String,
    val kernelShort: String,
    val kmi: String,
    val securityPatch: String,
    val soc: String,
) {
    companion object {
        fun detect(): DeviceInfo {
            val kernel = try {
                android.system.Os.uname().release.trim()
            } catch (_: Throwable) {
                System.getProperty("os.version")?.trim().orEmpty()
            }
            val kernelShort = Regex("""(\d+\.\d+\.\d+)""").find(kernel)?.groupValues?.get(1) ?: ""

            // uname: "6.6.89-android15-8-g…" → KMI "android15-6.6"
            val kmi = Regex("""android\d+""").find(kernel)?.value?.let { level ->
                val mm = kernelShort.split(".")
                "$level-${mm.getOrNull(0)}.${mm.getOrNull(1) ?: "0"}"
            } ?: if (kernelShort.isNotEmpty()) {
                val mm = kernelShort.split(".")
                "android${Build.VERSION.SDK_INT - 20}-${mm.getOrNull(0)}.${mm.getOrNull(1) ?: "0"}"
            } else ""

            return DeviceInfo(
                model = Build.DEVICE,
                marketName = Build.MODEL,
                brand = Build.BRAND.lowercase(),
                rom = Build.DISPLAY,
                kernel = kernel,
                kernelShort = kernelShort,
                kmi = kmi,
                securityPatch = Build.VERSION.SECURITY_PATCH,
                soc = detectSoc(),
            )
        }

        private fun detectSoc(): String {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL.takeIf { it.isNotEmpty() && it != "unknown" }?.let { return it.uppercase() }
            }
            val board = Build.HARDWARE.lowercase()
            return when {
                board.contains("kera") || board.contains("canary") -> "SM8750"
                board.contains("sun") || board.contains("pineapple") -> "SM8650"
                board.contains("kalama") -> "SM8550"
                board.contains("taro") -> "SM8475"
                board.contains("mt6899") || board.contains("mt6991") -> "Dimensity 9400"
                else -> Build.HARDWARE.uppercase()
            }
        }
    }
}
