package com.rootmyvivo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.core.DeviceInfo
import com.rootmyvivo.core.KsuVariant
import com.rootmyvivo.core.PayloadEntry
import com.rootmyvivo.core.SupportCheck
import com.rootmyvivo.RootStatus
import com.rootmyvivo.ui.components.RootButton

@Composable
fun MainScreen(
    state: com.rootmyvivo.UiState,
    onRootClick: () -> Unit,
    onStopClick: () -> Unit,
    onSelectKsu: (KsuVariant) -> Unit,
    onDismissKsu: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        Header()
        RootBanner(state.rootStatus)
        DeviceCard(state.deviceInfo)
        PayloadCard(state.payload)
        IssuesCard(state.supportCheck)
        
        // Download progress
        state.downloadProgress?.let { progress ->
            Column {
                Text("Скачивание пейлоада...", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
        
        // Step progress
        if (state.isRunning && state.currentStep > 0) {
            StepProgress(state.currentStep, state.totalSteps, state.installStage)
        }

        // ROOT button / STOP button
        if (state.isRunning) {
            OutlinedButton(
                onClick = onStopClick,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(Icons.Rounded.Close, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Остановить")
            }
        } else {
            RootButton(
                text = when (state.rootStatus) {
                    RootStatus.Active -> "Рут активен"
                    else -> "ПОЛУЧИТЬ ROOT"
                },
                icon = Icons.Rounded.Bolt,
                enabled = state.rootStatus != RootStatus.Active,
                isRunning = false,
                onClick = onRootClick,
            )
        }

        // KSU selector
        OutlinedButton(
            onClick = { /* handled by state */ },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("KernelSU: ${state.selectedKsu.displayName}")
        }

        // Log
        if (state.logLines.isNotEmpty()) LogCard(state.logLines)

        Spacer(Modifier.height(32.dp))
    }

    if (state.ksuSheetOpen) {
        KsuSelectorSheet(state.selectedKsu.displayName, onSelectKsu, onDismissKsu)
    }
}

// ── Header ──
@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Column {
            Text("RootMyVivo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("v0.1.0-alpha · one-click root",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PulsingShieldIcon()
    }
}

@Composable
private fun PulsingShieldIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "pulse"
    )
    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp).scale(scale))
}

// ── Root Status Banner ──
@Composable
private fun RootBanner(status: RootStatus) {
    AnimatedVisibility(
        visible = status == RootStatus.Active || status == RootStatus.Failed,
        enter = expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val (color, icon, text) = when (status) {
            RootStatus.Active -> Triple(MaterialTheme.colorScheme.primaryContainer,
                Icons.Rounded.CheckCircle, "Root активен!")
            RootStatus.Failed -> Triple(MaterialTheme.colorScheme.errorContainer,
                Icons.Rounded.Error, "Не удалось получить рут")
            else -> return@AnimatedVisibility
        }
        Surface(shape = MaterialTheme.shapes.extraLarge, color = color) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(text, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// ── Device Card ──
@Composable
private fun DeviceCard(info: DeviceInfo?) {
    ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Smartphone, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Устройство", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { /* refresh */ }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (info == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }
            InfoRow("Модель", "${info.marketName} (${info.model})")
            InfoRow("ROM", info.rom.take(30))
            InfoRow("Ядро", info.kernelShort)
            InfoRow("KMI", info.kmi)
            InfoRow("Патч", info.securityPatch)
            InfoRow("SoC", info.soc)
        }
    }
}

// ── Payload Card ──
@Composable
private fun PayloadCard(payload: PayloadEntry?) {
    AnimatedVisibility(visible = payload != null, enter = expandVertically()) {
        payload?.let { p ->
            Surface(shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                    Column {
                        Text(p.displayName, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text("verified by ${p.verifiedBy ?: "unknown"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

// ── Issues Card ──
@Composable
private fun IssuesCard(check: SupportCheck?) {
    if (!check?.issues.isNullOrEmpty()) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
            Column(Modifier.padding(14.dp)) {
                check!!.issues.forEach { issue ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(issue, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

// ── Step Progress ──
@Composable
private fun StepProgress(current: Int, total: Int, label: String) {
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("$current/$total", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { current.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.take(24), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

// ── Log Card ──
@Composable
fun LogCard(lines: List<String>) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }
    
    Surface(shape = MaterialTheme.shapes.large, color = Color(0xFF1A1B2E)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).padding(12.dp)) {
            items(lines) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = when {
                        line.contains("[✓✓✓]") -> Color(0xFF00E676)
                        line.contains("[✓]") -> Color(0xFF64FFDA)
                        line.contains("[✗]") -> Color(0xFFFF5252)
                        line.contains("[*]") -> Color(0xFF7C4DFF)
                        line.contains("[↓]") -> Color(0xFF40C4FF)
                        else -> Color(0xFFB0BEC5)
                    })
            }
        }
    }
}

// ── KSU Selector Sheet ──
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KsuSelectorSheet(selected: String, onSelect: (KsuVariant) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text("Выбери KernelSU", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("Все варианты работают через late-load на залоченном BL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            KsuVariant.entries.forEach { v ->
                ListItem(
                    headlineContent = { Text(v.displayName, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(v.description) },
                    leadingContent = { RadioButton(selected = selected == v.displayName, onClick = { onSelect(v) }) },
                    modifier = Modifier.clickable { onSelect(v) },
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
