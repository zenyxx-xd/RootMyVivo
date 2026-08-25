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

            // Распаковываем preload.so
            extractToWork(ctx, "binaries/preload_pd2520.so", "$WORK_DIR/preload.so")
            // Распаковываем kernelsu.ko
            extractToWork(ctx, "binaries/kernelsu_android15-6.6.ko", "$WORK_DIR/kernelsu.ko")
            // Распаковываем ksud
            extractToWork(ctx, "binaries/ksud", "$WORK_DIR/ksud")

            // Делаем исполняемыми
            File("$WORK_DIR/preload.so").setReadable(true, false)
            File("$WORK_DIR/preload.so").setWritable(true, false)
            File("$WORK_DIR/ksud").setExecutable(true, false)
            File("$WORK_DIR/ksud").setReadable(true, false)

            Log.i(TAG, "Бинарники распакованы в $WORK_DIR")

            // Также копируем ksud в filesDir для ProcessBuilder
            val binDir = File(ctx.filesDir, "bin")
            binDir.mkdirs()
            copyAsset(ctx, "binaries/ksud", File(binDir, "ksud"))
            File(binDir, "ksud").setExecutable(true, false)

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
