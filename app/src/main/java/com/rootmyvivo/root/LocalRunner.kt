package com.rootmyvivo.root

import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Локальное выполнение из процесса приложения — без Shizuku и без ADB.
 * Эксплойт (GhostLock) — чисто userspace-нативный: fork/exec, сокеты,
 * файлы в своём filesDir. Ему хватает обычного app-процесса, shell-
 * привилегии не нужны (так же устроен Yuehong: ProcessBuilder + env
 * GHOSTLOCK_HOME=getFilesDir()).
 */
object LocalRunner {

    private const val TAG = "LocalRunner"

    /** Выполнить shell-команду локально: (exitCode, stdout+stderr). */
    fun exec(command: String, env: Map<String, String> = emptyMap(), timeoutSec: Long = 300): Pair<Int, String> = try {
        val pb = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
        pb.environment().putAll(env)
        val proc = pb.start()
        val out = proc.inputStream.bufferedReader().readText()
        val code = if (proc.waitFor(timeoutSec, TimeUnit.SECONDS)) proc.exitValue() else {
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.destroyForcibly()
            -1
        }
        code to out
    } catch (e: Exception) {
        Log.e(TAG, "exec failed: $command", e)
        -1 to (e.message ?: "local exec error")
    }

    /** Запустить команду в фоне (не ждём): пишет stdout/stderr в logFile. */
    fun execBackground(command: String, env: Map<String, String> = emptyMap(), logFile: File? = null): Boolean = try {
        // Фон — через вложенный sh: команда возвращается сразу,
        // вывод идёт в лог напрямую, без reader-потока.
        val redir = if (logFile != null) " > '${logFile.absolutePath}' 2>&1" else ""
        val full = "($command$redir &)"
        val pb = ProcessBuilder("sh", "-c", full)
        pb.environment().putAll(env)
        pb.start()
        true
    } catch (e: Exception) {
        Log.e(TAG, "execBackground failed: $command", e)
        false
    }

    /** Скопировать файл локально (деплой без транспорта). */
    fun copy(src: File, dst: File): Boolean = try {
        dst.parentFile?.mkdirs()
        src.inputStream().use { inp ->
            dst.outputStream().use { out -> inp.copyTo(out) }
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "copy failed: ${src.absolutePath} -> ${dst.absolutePath}", e)
        false
    }

    /** Последние n строк файла (хвост live.log без shell). */
    fun tail(file: File, n: Int = 30): String = try {
        if (!file.isFile) return ""
        val lines = file.readLines()
        lines.takeLast(n).joinToString("\n")
    } catch (_: Exception) {
        ""
    }

    /** Живой процесс с подгруженным пейлоадом (подхват после перезапуска приложения). */
    fun runningWithPreload(preloadName: String = "rmv/preload.so"): Boolean = try {
        File("/proc").listFiles { f -> f.isDirectory && f.name.all { c -> c.isDigit() } }
            ?.any { pid ->
                runCatching { File(pid, "maps").readText().contains(preloadName) }.getOrDefault(false)
            } == true
    } catch (_: Exception) {
        false
    }
}
