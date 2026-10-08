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
    /** DirtyFrag (CVE-2026-43284): применим ли примитив на этом ядре. */
    enum class DfCompat { OK, PATCHED, NON_GKI }

    /**
     * Решение по ядру (таблица порогов фикса от 2026-05-08, SKBFL_SHARED_FRAG):
     *  - весь 6.1.x — не поддерживается (mitigation в ядре, DF-модуля нет);
     *  - ядро ≥ порога своей ветки — пропатчено (esp больше не пишет в page
     *    cache): 5.10→5.10.255, 5.15→5.15.205, 6.1→6.1.171, 6.6→6.6.138,
     *    6.12→6.12.87 (в тегах ≤6.12.60 фикса нет), 6.18→6.18.29, 7.0→7.0.6;
     *  - non-GKI (нет androidNN в uname) — не поддерживается;
     *  - иначе — применим (экспериментально: ko собираем сами, vivo не
     *    тестировано).
     */
    fun dirtyfragCompatible(): DfCompat {
        if (!Regex("""android\d+""").containsMatchIn(kernel)) return DfCompat.NON_GKI
        val parts = kernelShort.split(".").map { it.toIntOrNull() ?: 0 }
        val maj = parts.getOrNull(0) ?: 0
        val min = parts.getOrNull(1) ?: 0
        val patchedFrom = when {
            maj == 5 && min == 10 -> 255
            maj == 5 && min == 15 -> 205
            maj == 6 && min == 6 -> 138
            maj == 6 && min == 12 -> 87
            maj == 6 && min == 18 -> 29
            maj >= 7 -> if (maj == 7 && min == 0) 6 else 0
            else -> 0
        }
        val patch = parts.getOrNull(2) ?: 0
        if (patchedFrom in 1..patch) return DfCompat.PATCHED
        return DfCompat.OK
    }

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
