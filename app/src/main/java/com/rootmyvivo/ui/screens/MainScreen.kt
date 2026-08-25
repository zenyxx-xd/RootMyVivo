package com.rootmyvivo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.core.DeviceInfo
import com.rootmyvivo.core.SupportCheck
import com.rootmyvivo.core.KsuVariant
import com.rootmyvivo.ui.components.RootButton

@Composable
fun MainScreen(
    deviceInfo: DeviceInfo?,
    supportCheck: SupportCheck?,
    isRunning: Boolean,
    logLines: List<String>,
    rootStatus: String?,
    selectedKsu: String,
    ksuSheetOpen: Boolean,
    onRootClick: () -> Unit,
    onSelectKsu: (KsuVariant) -> Unit,
    onDismissKsu: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("RootMyVivo", style = MaterialTheme.typography.headlineLarge)
            Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }

        if (rootStatus != null || supportCheck?.rootAlready == true) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(rootStatus ?: "Рут активен!", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        DeviceCard(deviceInfo)

        if (!supportCheck?.issues.isNullOrEmpty()) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                Column(Modifier.padding(14.dp)) {
                    supportCheck!!.issues.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        RootButton(
            text = if (supportCheck?.rootAlready == true) "Root активен" else "ПОЛУЧИТЬ ROOT",
            icon = Icons.Rounded.Bolt,
            enabled = supportCheck?.supported != false,
            isRunning = isRunning,
            onClick = onRootClick,
        )

        OutlinedButton(
            onClick = { /* opens sheet via state */ },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("KernelSU: $selectedKsu")
        }

        if (logLines.isNotEmpty()) LogCard(logLines)

        Spacer(Modifier.height(32.dp))
    }

    if (ksuSheetOpen) KsuSelectorSheet(selectedKsu, onSelectKsu, onDismissKsu)
}

@Composable
private fun DeviceCard(info: DeviceInfo?) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Smartphone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Устройство", style = MaterialTheme.typography.titleMedium)
            }
            if (info == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }
            InfoRow("Модель", "${info.marketName} (${info.model})")
            InfoRow("ROM", info.rom.take(28))
            InfoRow("Ядро", info.kernelShort)
            InfoRow("KMI", info.kmi)
            InfoRow("Патч", info.securityPatch)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LogCard(lines: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
        LazyColumn(state = listState, Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(12.dp)) {
            items(lines) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = when {
                        line.contains("[✓]") -> MaterialTheme.colorScheme.primary
                        line.contains("[✗]") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KsuSelectorSheet(selected: String, onSelect: (KsuVariant) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text("Выбери KernelSU", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            KsuVariant.entries.forEach { v ->
                ListItem(
                    headlineContent = { Text(v.displayName) },
                    supportingContent = { Text(v.description) },
                    leadingContent = { RadioButton(selected = selected == v.displayName, onClick = { onSelect(v) }) },
                    modifier = Modifier.clickable { onSelect(v) },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
