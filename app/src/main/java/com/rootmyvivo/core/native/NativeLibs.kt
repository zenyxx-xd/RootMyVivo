package com.rootmyvivo.core.native

import android.content.Context
import android.util.Log
import com.rootmyvivo.RmvApp
import java.io.File

/**
 * Управление нативными бинарниками.
 * Ассеты опциональны: основные файлы (preload.so, kernelsu.ko, ksud)
 * скачиваются из каталога пейлоадов в filesDir/payloads (доступно приложению).
 * Вшитые в assets бинарники распаковываются туда же.
 */
object NativeLibs {

    private const val TAG = "RootMyVivo"

    /** Рабочая директория — filesDir приложения (записываемая без root) */
    fun workDir(): File =
        File(RmvApp.instance.filesDir, "payloads").apply { mkdirs() }

    private var initialised = false

    fun init(): Boolean {
        if (initialised) return true
        val ctx = RmvApp.instance

        try {
            val workDir = workDir()

            // Ассеты опциональны: бинарники обычно качаются из каталога.
            val bundled = listOf(
                "binaries/preload_pd2520.so" to "preload.so",
                "binaries/kernelsu_android15-6.6.ko" to "kernelsu.ko",
                "binaries/ksud" to "ksud",
            )
            bundled.forEach { (asset, name) ->
                val dest = File(workDir, name)
                if (dest.exists()) return@forEach  // скачанный приоритетнее
                try {
                    ctx.assets.open(asset).use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest.setReadable(true, false)
                    if (name == "ksud") dest.setExecutable(true, false)
                } catch (_: Exception) { /* нет вшитого — ок */ }
            }

            Log.i(TAG, "Бинарники готовы в ${workDir.absolutePath} " +
                "(вшитые: ${workDir.listFiles()?.size ?: 0})")

            initialised = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации: ${e.message}")
            return false
        }
    }

    fun getPath(name: String): String =
        when (name) {
            "preload" -> File(workDir(), "preload.so").absolutePath
            "kernelsu_ko" -> File(workDir(), "kernelsu.ko").absolutePath
            "ksud" -> File(workDir(), "ksud").absolutePath
            else -> File(workDir(), name).absolutePath
        }
}
