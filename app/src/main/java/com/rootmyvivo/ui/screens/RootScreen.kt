package com.rootmyvivo.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.core.KsuVariant
import com.rootmyvivo.core.RootStatus

/**
 * Экран джейлбрейка — полноэкранный лог процесса.
 * Показывает: шаги, прогресс, живой вывод эксплойта.
 */
@Composable
fun RootScreen(
    isRunning: Boolean,
    logLines: List<String>,
    currentStep: Int,
    totalSteps: Int,
    installStage: String,
    downloadProgress: Float?,
    rootStatus: RootStatus,
    onSuccess: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(Modifier.height(48.dp))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("Jailbreak", style = MaterialTheme.typography.headlineMedium)
        }
        
        Spacer(Modifier.height(20.dp))

        // Step indicator
        if (isRunning && currentStep > 0) {
            Surface(shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(installStage.ifEmpty("Выполнение..."),
                            style = MaterialTheme.typography.bodyMedium)
                        Text("$currentStep/$totalSteps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha=0.6f))
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { currentStep.toFloat() / totalSteps },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Download progress
        downloadProgress?.let { progress ->
            Column {
                Text("Скачивание...", style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Success banner
        AnimatedVisibility(visible = rootStatus == RootStatus.Active,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioHighBouncy))) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    Text("ROOT ПОЛУЧЕН!", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("KernelSU активен. Перезагрузи телефон для закрепления.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=0.7f))
                }
            }
        }

        // Failure banner
        AnimatedVisibility(visible = rootStatus == RootStatus.Failed && !isRunning) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.padding(14.dp), spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text("Не удалось получить рут.\nПопробуй ещё раз.",
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Log terminal
        if (logLines.isNotEmpty()) {
            Text("Лог:", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            TerminalLog(logLines, Modifier.weight(1f))
        } else if (isRunning) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // Running indicator at bottom
        if (isRunning) {
            Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Выполняется...", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Терминальный лог с автоскроллом и цветовой подсветкой по теме */
@Composable
fun TerminalLog(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        LazyColumn(state = listState, modifier = modifier.fillMaxWidth().padding(12.dp)) {
            items(lines) { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        line.contains("[✓✓✓]") -> MaterialTheme.colorScheme.primary
                        line.contains("[✓]") -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        line.contains("[✗]") || line.contains("ERROR") -> MaterialTheme.colorScheme.error
                        line.contains("[*]") -> MaterialTheme.colorScheme.tertiary
                        line.contains("[↓]") -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private fun <T> spacedBy(dp: androidx.compose.ui.unit.Dp) = Arrangement.spacedBy(dp)
