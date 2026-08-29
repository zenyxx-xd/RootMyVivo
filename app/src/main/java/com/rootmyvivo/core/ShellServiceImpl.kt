package com.rootmyvivo.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import java.io.File

/**
 * Сервис, выполняющийся внутри процесса Shizuku (shell-домен, uid=2000).
 * Shizuku инстанцирует его по UserServiceArgs и отдаёт binder приложению.
 */
class ShellServiceImpl : IShellService.Stub() {

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
}
