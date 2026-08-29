package com.rootmyvivo.core

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.annotation.Keep
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * UserService Shizuku: выполняется в shell-домене (uid=2000, u:r:shell:s0).
 * Канонический паттерн: extends IShellService.Stub, конструктор с Context + @Keep.
 */
class ShellServiceImpl : IShellService.Stub {

    constructor()

    @Keep
    constructor(context: Context) {
        // Контекст доступен, но ContentResolver/registerReceiver тут не работают
    }

    override fun destroy() {
        System.exit(0)
    }

    override fun exit() {
        System.exit(0)
    }

    override fun ping(): String = "pong uid=${Os.getuid()}"

    override fun exec(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exit = process.waitFor()
            "EXIT=$exit\n$output"
        } catch (e: Exception) {
            "EXIT=-1\n${e.message ?: "error"}"
        }
    }

    override fun writeFileChunk(path: String, offset: Long, data: ByteArray): Boolean {
        return try {
            val f = File(path)
            f.parentFile?.mkdirs()
            RandomAccessFile(f, "rw").use { raf ->
                raf.seek(offset)
                raf.write(data)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
