package com.rootmyvivo.core.native

import android.content.Context
import com.rootmyvivo.RmvApp
import java.io.File

/**
 * Управление нативными бинарниками: распаковка из assets/jniLibs,
 * копирование в filesDir, chmod 755.
 */
object NativeLibs {
    
    data class Binary(
        val name: String,
        val assetPath: String?,      // null если из jniLibs
        val jniLibName: String? = null,
    )
    
    val binaries = listOf(
        Binary("ghostlock", assetPath = "binaries/ghostlock"),
        Binary("ghostlock-helper", assetPath = "binaries/ghostlock-helper"),
        Binary("ksud", assetPath = "binaries/ksud"),
    )
    
    private var loaded = false
    
    fun load(): Boolean {
        if (loaded) return true
        val ctx = RmvApp.instance
        
        for (bin in binaries) {
            val f = binaryFile(ctx, bin.name)
            if (!f.exists() || !f.canExecute()) {
                extract(ctx, bin, f)
            }
        }
        loaded = true
        return true
    }
    
    fun binaryFile(ctx: Context, name: String): File =
        File(ctx.filesDir, "bin/$name").also { parent ->
            parent.parentFile?.mkdirs()
        }
    
    private fun extract(ctx: Context, bin: Binary, dest: File) {
        bin.assetPath?.let { asset ->
            ctx.assets.open(asset).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        } ?: bin.jniLibName?.let { libName ->
            // Из JNI libs: /data/app/.../lib/arm64/lib{X}.so
            ctx.applicationInfo.nativeLibraryDir
        }?.let { libDir ->
            val src = File(libDir, "lib${bin.jniLibName}.so")
            if (src.exists()) src.copyTo(dest, overwrite = true)
        }
        
        dest.setExecutable(true, false)
        dest.setReadable(true, false)
        dest.setWritable(false)
    }
    
    /** Возвращает путь к бинарнику для ProcessBuilder */
    fun getPath(name: String): String = 
        binaryFile(RmvApp.instance, name).absolutePath
}
