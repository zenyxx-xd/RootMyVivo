package com.rootmyvivo.core.native

import android.content.Context
import android.util.Log
import com.rootmyvivo.RmvApp
import java.io.File

/**
 * Управление нативными бинарниками.
 * Распаковывает из assets в /data/local/tmp/rmv/ (нужно для LD_PRELOAD)
 * и в filesDir/bin/ (для ProcessBuilder).
 */
object NativeLibs {

    private const val TAG = "RootMyVivo"
    private const val WORK_DIR = "/data/local/tmp/rmv"

    private var initialised = false

    /**
     * Распаковка всех бинарников на устройство:
     * - В /data/local/tmp/rmv/ (для эксплойта — нужно shell-доступное место)
     * - В filesDir/bin/ (для ProcessBuilder из app)
     */
    fun init(): Boolean {
        if (initialised) return true
        val ctx = RmvApp.instance

        try {
            // Создаём рабочую директорию
            val workDir = File(WORK_DIR)
            workDir.mkdirs()

            // Ассеты опциональны: бинарники могут качаться из каталога.
            // Распаковываем только то, что реально вшито.
            val bundled = listOf(
                "binaries/preload_pd2520.so" to "$WORK_DIR/preload.so",
                "binaries/kernelsu_android15-6.6.ko" to "$WORK_DIR/kernelsu.ko",
                "binaries/ksud" to "$WORK_DIR/ksud",
            )
            bundled.forEach { (asset, dest) ->
                try {
                    extractToWork(ctx, asset, dest)
                    File(dest).setReadable(true, false)
                    File(dest).setWritable(true, false)
                } catch (_: Exception) { /* нет вшитого — скачаем из каталога */ }
            }

            // ksud в filesDir для ProcessBuilder
            try {
                val binDir = File(ctx.filesDir, "bin")
                binDir.mkdirs()
                copyAsset(ctx, "binaries/ksud", File(binDir, "ksud"))
                File(binDir, "ksud").setExecutable(true, false)
            } catch (_: Exception) { }

            Log.i(TAG, "Бинарники готовы в $WORK_DIR (вшитые: ${
                bundled.count { File(it.second).exists() }}/${bundled.size})")

            initialised = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации: ${e.message}")
            return false
        }
    }

    private fun extractToWork(ctx: Context, assetPath: String, destPath: String) {
        val dest = File(destPath)
        ctx.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun copyAsset(ctx: Context, assetPath: String, dest: File) {
        ctx.assets.open(assetPath).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.setExecutable(true, false)
    }

    fun getPath(name: String): String =
        when (name) {
            "preload" -> "$WORK_DIR/preload.so"
            "kernelsu_ko" -> "$WORK_DIR/kernelsu.ko"
            "ksud" -> "$WORK_DIR/ksud"
            else -> File(RmvApp.instance.filesDir, "bin/$name").absolutePath
        }
}
