package com.rootmyvivo.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.MainViewModel
import com.rootmyvivo.RootStatus
import com.rootmyvivo.UiState
import com.rootmyvivo.core.DeviceInfo
import com.rootmyvivo.core.KsuVariant
import com.rootmyvivo.core.PayloadEntry
import com.rootmyvivo.core.SupportCheck
import com.rootmyvivo.ui.components.RootButton

/**
 * Главный экран: устройство, статус рута, кнопка ROOT.
 * Лог показывается на отдельном экране джейлбрейка.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: UiState, vm: MainViewModel, onRootStarted: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        
        // Header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("RootMyVivo", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            PulsingIcon()
        }

        // Root status banner (animated)
        AnimatedVisibility(
            visible = state.rootStatus == RootStatus.Active,
            enter = expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = shrinkVertically(),
        ) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    Text("Root активен! KernelSU работает.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // Payload found banner
        AnimatedVisibility(visible = state.payload != null, enter = expandVertically()) {
            state.payload?.let { p ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bolt, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        Column {
                            Text(p.displayName, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            if (p.verifiedBy != null)
                                Text("verified: ${p.verifiedBy}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
        
        // Not supported banner
        if (state.payload == null && !state.isRunning && state.deviceInfo != null && state.rootStatus != RootStatus.Checking) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer.copy(alpha=0.5f)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                    Text("Пейлоад для этого устройства не найден в каталоге",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }

        // Device card
        DeviceCard(state.deviceInfo)

        // Issues
        IssuesCard(state.supportCheck)

        // ROOT Button → navigates to Jailbreak screen
        val enabled = state.supportCheck?.supported == true && 
                      state.payload != null && 
                      state.rootStatus != RootStatus.Active &&
                      !state.isRunning
        
        RootButton(
            text = when {
                state.isRunning -> "Выполняется..."
                state.rootStatus == RootStatus.Active -> "Рут активен ✓"
                state.payload == null -> "Не поддерживается"
                else -> "ПОЛУЧИТЬ ROOT"
            },
            icon = Icons.Rounded.Bolt,
            enabled = enabled,
            isRunning = false,
            onClick = { 
                vm.startRoot()
                onRootStarted()
            },
        )

        // KSU Selector
        OutlinedButton(
            onClick = { vm.toggleKsuSheet() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(Icons.Rounded.Settings, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("KernelSU: ${state.selectedKsu.displayName}")
        }

        Spacer(Modifier.height(32.dp))
    }

    // KSU Bottom Sheet
    if (state.ksuSheetOpen) {
        ModalBottomSheet(onDismissRequest = { vm.toggleKsuSheet() }) {
            Column(Modifier.padding(20.dp)) {
                Text("Выбери KernelSU", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text("Все варианты работают через late-load на залоченном BL.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                KsuVariant.entries.forEach { v ->
                    ListItem(
                        headlineContent = { Text(v.displayName, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(v.description) },
                        leadingContent = { RadioButton(selected = state.selectedKsu == v, onClick = { vm.selectKsu(v) }) },
                        modifier = Modifier.clickable { vm.selectKsu(v) },
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Пульсирующая иконка щита */
@Composable
private fun PulsingIcon() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ), label = "shield"
    )
    Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(38.dp).scale(scale))
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
            InfoRow("ROM", info.rom.take(30))
            InfoRow("Ядро", info.kernelShort)
            InfoRow("KMI", info.kmi.ifEmpty { "—" })
            InfoRow("Патч", info.securityPatch)
            InfoRow("SoC", info.soc)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.take(26), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun IssuesCard(check: SupportCheck?) {
    if (!check?.issues.isNullOrEmpty()) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                check!!.issues.forEach { issue ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Warning, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(issue, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}
