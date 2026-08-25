package com.rootmyvivo.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Выполнить команду через ProcessBuilder */
suspend fun execCommand(vararg cmd: String): Pair<Int, String> =
    withContext(Dispatchers.IO) {
        try {
            val proc = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            Pair(proc.waitFor(), output)
        } catch (e: Exception) {
            Pair(-1, e.message ?: "error")
        }
    }

/** Проверка доступности su */
suspend fun isSuAvailable(): Boolean =
    withContext(Dispatchers.IO) {
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "true")).waitFor() == 0
        } catch (_: Exception) { false }
    }

/** Табы нижней навигации */
enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Главная", Icons.Rounded.Home),
    SETTINGS("Настройки", Icons.Rounded.Settings),
}
