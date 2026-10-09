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
    /**
     * DirtyFrag (CVE-2026-43284):
     * OK — применим; PATCHED — фикс порога ветки уже в ядре (можно
     * разрешить вручную, ko под KMI есть); UNSUPPORTED — DF невозможен
     * в принципе: non-GKI, 6.1.x (примитив мёртв) или ветка без ko.
     */
    enum class DfCompat { OK, PATCHED, UNSUPPORTED }

    /** Ветка ядра без ko-модуля в приложении — DF недоступен вообще. */
    private fun dfHasKo(maj: Int, min: Int): Boolean =
        (maj == 5 && min == 10) || (maj == 5 && min == 15) ||
            (maj == 6 && (min == 6 || min == 12 || min == 18))

    /**
     * Решение по ядру (таблица порогов фикса от 2026-05-08, SKBFL_SHARED_FRAG):
     *  - весь 6.1.x — мёртв: accidental mitigation, ko в приложении нет;
     *  - ядро ≥ порога своей ветки — пропатчено: 5.10→5.10.255,
     *    5.15→5.15.205, 6.6→6.6.127 (по полевым логам vivo), 6.12→6.12.87, 6.18→6.18.29;
     *  - non-GKI (нет androidNN в uname) — не поддерживается;
     *  - иначе — применим (экспериментально: ko собираем сами, vivo не
     *    тестировано).
     */
    fun dirtyfragCompatible(): DfCompat {
        val parts = kernelShort.split(".").map { it.toIntOrNull() ?: 0 }
        val maj = parts.getOrNull(0) ?: 0
        val min = parts.getOrNull(1) ?: 0
        // 6.1 и ветки без ko — недоступны при любых настройках
        if (maj == 6 && min == 1) return DfCompat.UNSUPPORTED
        if (!dfHasKo(maj, min)) return DfCompat.UNSUPPORTED
        val patchedFrom = when {
            maj == 5 && min == 10 -> 255
            maj == 5 && min == 15 -> 205
            maj == 6 && min == 6 -> 127
            maj == 6 && min == 12 -> 87
            maj == 6 && min == 18 -> 29
            else -> 0
        }
        val patch = parts.getOrNull(2) ?: 0
        if (patchedFrom in 1..patch) return DfCompat.PATCHED
        return DfCompat.OK
    }

    /**
     * Итоговое «запускать ли DF»: OK — да; PATCHED — только если юзер
     * разрешил «DF на всех ядрах» в настройках (ko его KMI есть, риск
     * верификации на нём); UNSUPPORTED — никогда.
     */
    fun dfAllowed(allowPatched: Boolean): Boolean = when (dirtyfragCompatible()) {
        DfCompat.OK -> true
        DfCompat.PATCHED -> allowPatched
        DfCompat.UNSUPPORTED -> false
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
