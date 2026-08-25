package com.rootmyvivo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rootmyvivo.core.*
import com.rootmyvivo.ui.components.*

/**
 * Главный экран RootMyVivo.
 * Material You 3 Expressive: карточки, крупная кнопка, живой лог.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    deviceInfo: DeviceInfo?,
    supportCheck: SupportCheck?,
    isRunning: Boolean,
    logLines: List<String>,
    rootStatus: String?,
    onRootClick: () -> Unit,
    onSelectKsu: () -> Unit,
    selectedKsu: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        
        // ── Заголовок ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("RootMyVivo", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "v0.1.0-alpha",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.Shield,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        
        // ── Статус рута (анимированная карточка) ──
        AnimatedVisibility(
            visible = rootStatus != null || supportCheck?.rootAlready == true,
            enter = expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, 
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        rootStatus ?: "Рут уже активен!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        
        // ── Инфо об устройстве ──
        DeviceCard(deviceInfo)
        
        // ── Проблемы поддержки ──
        if (!supportCheck?.issues.isNullOrEmpty()) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(Modifier.padding(14.dp)) {
                    supportCheck!!.issues.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
        
        // ── ГЛАВНАЯ КНОПКА ROOT ──
        RootButton(
            text = when {
                supportCheck?.rootAlready == true → "Рут активен"
                else → "ПОЛУЧИТЬ ROOT"
            },
            icon = Icons.Rounded.Bolt,
            enabled = supportCheck?.supported ?: false,
            isRunning = isRunning,
            onClick = onRootClick,
        )
        
        // ── Выбор KSU варианта ──
        OutlinedButton(
            onClick = onSelectKsu,
            modifier = Modifier.fillMaxHeight().height(48.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("KernelSU вариант: $selectedKsu")
        }
        
        // ── Лог в реальном времени ──
        if (logLines.isNotEmpty()) {
            LogCard(logLines)
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

/** Карточка с информацией об устройстве */
@Composable
private fun DeviceCard(info: DeviceInfo?) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Smartphone, null, 
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Устройство", style = MaterialTheme.typography.titleMedium)
            }
            
            if (info == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Определение...", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            
            InfoRow("Модель", "${info.marketName} (${info.model})")
            InfoRow("ROM", info.rom.take(28))
            InfoRow("Ядро", info.kernelShort)
            InfoRow("KMI", info.kmi)
            InfoRow("Патч", info.securityPatch)
            InfoRow("SoC", info.soc)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}

/** Карточка лога с автоскроллом вниз */
@Composable
private fun LogCard(lines: List<String>) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }
    
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(12.dp),
        ) {
            items(lines.size) { i ->
                Text(
                    lines[i],
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = when {
                        lines[i].contains("[✓]") -> MaterialTheme.colorScheme.primary
                        lines[i].contains("[✗]") || lines[i].contains("ERROR") -> 
                            MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
