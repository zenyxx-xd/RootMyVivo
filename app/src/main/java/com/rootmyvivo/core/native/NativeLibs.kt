package com.rootmyvivo.core.native

import android.content.Context
import android.util.Log
import com.rootmyvivo.RmvApp
import java.io.File

/**
 * Управление нативными бинарниками: распаковка из assets,
 * копирование в filesDir, chmod 755.
 * Не крашится если бинарники отсутствуют — приложение работает в degraded режиме.
 */
object NativeLibs {

    private const val TAG = "RootMyVivo"

    data class Binary(
        val name: String,
        val assetPath: String?,
    )

    val binaries = listOf(
        Binary("ghostlock", assetPath = "binaries/ghostlock"),
        Binary("ghostlock-helper", assetPath = "binaries/ghostlock-helper"),
        Binary("ksud", assetPath = "binaries/ksud"),
    )

    private var loaded = false
    private val available = mutableSetOf<String>()

    fun load(): Boolean {
        if (loaded) return true
        val ctx = RmvApp.instance

        for (bin in binaries) {
            try {
                val f = binaryFile(ctx, bin.name)
                if (!f.exists() || !f.canExecute()) {
                    extract(ctx, bin, f)
                }
                if (f.exists() && f.canExecute()) {
                    available.add(bin.name)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Бинарник ${bin.name} недоступен: ${e.message}")
            }
        }

        loaded = true
        return true
    }

    fun isAvailable(name: String): Boolean = name in available

    fun binaryFile(ctx: Context, name: String): File =
        File(ctx.filesDir, "bin/$name").also { parent ->
            parent.parentFile?.mkdirs()
        }

    private fun extract(ctx: Context, bin: Binary, dest: File) {
        val assetPath = bin.assetPath ?: return
        try {
            ctx.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.setExecutable(true, false)
            dest.setReadable(true, false)
            dest.setWritable(false)
        } catch (e: Exception) {
            Log.d(TAG, "${bin.name}: не найден в assets (${e.message})")
        }
    }

    /** Возвращает путь к бинарнику для ProcessBuilder */
    fun getPath(name: String): String =
        binaryFile(RmvApp.instance, name).absolutePath
}
