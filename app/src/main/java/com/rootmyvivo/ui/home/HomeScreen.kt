package com.rootmyvivo.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.twotone.Bolt
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.CloudDownload
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.DevicesOther
import androidx.compose.material.icons.twotone.LinkOff
import androidx.compose.material.icons.twotone.Memory
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material.icons.twotone.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.root.KsuVariant
import com.rootmyvivo.shell.TransportState
import com.rootmyvivo.ui.common.ChoiceDialog
import com.rootmyvivo.ui.common.ChoiceDialogItem
import com.rootmyvivo.ui.common.SettingsDivider
import com.rootmyvivo.ui.common.SettingsGroup
import com.rootmyvivo.ui.common.SettingsRow
import com.rootmyvivo.ui.common.TrailingValue
import com.rootmyvivo.ui.theme.LocalAppStyle
import com.rootmyvivo.vm.CatalogState
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.RootState
import com.rootmyvivo.vm.UiState
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Главный экран. Новая тема — в стиле ReSukiSU: большая сворачиваемая
 * шапка, статус-карточка, группы из отдельных карточек. Monet Old —
 * прежний вид.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    state: UiState,
    onRootStarted: () -> Unit,
    onOpenLastLog: () -> Unit = {},
    onOpenSupported: () -> Unit = {},
) {
    val style = LocalAppStyle.current
    if (style.isLegacy) {
        LegacyHomeScreen(vm, state, onRootStarted, onOpenLastLog, onOpenSupported)
    } else {
        ExpressiveHomeScreen(vm, state, onRootStarted, onOpenLastLog, onOpenSupported)
    }
}

// ─────────── Новая тема ───────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveHomeScreen(
    vm: MainViewModel,
    state: UiState,
    onRootStarted: () -> Unit,
    onOpenLastLog: () -> Unit,
    onOpenSupported: () -> Unit,
) {
    var ksuDialog by remember { mutableStateOf(false) }
    var warnDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf(false) }
    var warnDontShow by remember { mutableStateOf(false) }
    var rebootDontShow by remember { mutableStateOf(false) }
    val warnDismissed = state.settings.warnDismissed

    if (warnDialog && warnDismissed) {
        warnDialog = false
    }

    val topBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topBarState)
    val scrollState = rememberScrollState()

    fun startRoot() {
        if (state.settings.warnDismissed) {
            vm.startRoot()
            onRootStarted()
        } else {
            warnDialog = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
        ) {
            // Плашка обновления
            state.appUpdate?.let { update ->
                CompactUpdateCard(
                    update = update,
                    downloadDone = state.updateDownload?.done == true,
                    downloading = state.updateDownload?.let { !it.done } == true,
                    onUpdate = vm::updateAction,
                )
                Spacer(Modifier.height(10.dp))
            }

            // Статус-карточка
            StatusCard(state, onStartRoot = { startRoot() })
            Spacer(Modifier.height(10.dp))

            // Кнопки при живом руте
            if (state.rootState == RootState.ROOTED) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (state.settings.restartConfirmDismissed) {
                                vm.startRoot()
                                onRootStarted()
                            } else {
                                confirmAction = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.home_restart_exploit), maxLines = 1)
                    }
                    Button(
                        onClick = {
                            if (state.settings.softRebootConfirmDismissed) {
                                vm.performSoftReboot()
                            } else {
                                confirmAction = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.home_softreboot), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Транспорт — только при проблеме
            when (state.transport) {
                TransportState.None, TransportState.ShizukuNeedsPermission -> {
                    TransportCard(state, vm)
                    Spacer(Modifier.height(10.dp))
                }
                else -> {}
            }

            // Статус: пейлоад / менеджер / история / устройства
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.status_payload),
                    description = when {
                        state.payload != null -> state.payload!!.displayName
                        state.catalogState == CatalogState.ERROR -> stringResource(R.string.catalog_error)
                        else -> stringResource(R.string.payload_short_searching)
                    },
                    icon = if (state.payload != null) Icons.TwoTone.CheckCircle else Icons.TwoTone.CloudDownload,
                )
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.ksu_title),
                    description = state.selectedKsu.displayName,
                    icon = Icons.TwoTone.Security,
                    onClick = { ksuDialog = true },
                    trailing = { Chevron() },
                )
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.home_lastlog),
                    icon = Icons.TwoTone.Description,
                    onClick = onOpenLastLog,
                    trailing = { Chevron() },
                )
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.home_supported_devices),
                    icon = Icons.TwoTone.DevicesOther,
                    onClick = onOpenSupported,
                    trailing = { Chevron() },
                )
            }
            Spacer(Modifier.height(10.dp))

            // Устройство
            DeviceGroup(state)
            Spacer(Modifier.height(28.dp))
        }
    }

    // ── Диалоги (общие с легаси) ──
    SharedDialogs(
        state = state,
        vm = vm,
        warnDialog = warnDialog,
        onWarnDialog = { warnDialog = it },
        warnDontShow = warnDontShow,
        onWarnDontShow = { warnDontShow = it },
        confirmAction = confirmAction,
        onConfirmAction = { confirmAction = it },
        rebootDontShow = rebootDontShow,
        onRebootDontShow = { rebootDontShow = it },
        onRootStarted = onRootStarted,
        ksuDialog = ksuDialog,
        onKsuDialog = { ksuDialog = it },
    )
}

/** Цветная статус-карточка в стиле ReSukiSU StatusCard. */
@Composable
private fun StatusCard(state: UiState, onStartRoot: () -> Unit) {
    when (state.rootState) {
        RootState.ROOTED -> SettingsRow(
            icon = Icons.TwoTone.TaskAlt,
            title = stringResource(R.string.status_rooted),
            description = stringResource(R.string.status_rooted_desc),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        )
        RootState.CHECKING -> SettingsRow(
            icon = Icons.TwoTone.Memory,
            title = stringResource(R.string.status_checking),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        )
        else -> {
            val ready = state.payload != null
            val transportOk = state.transport == TransportState.Adb || state.transport == TransportState.Shizuku
            if (ready) {
                SettingsRow(
                    icon = Icons.TwoTone.Bolt,
                    title = stringResource(R.string.status_not_rooted),
                    description = state.payload!!.displayName,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    below = {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onStartRoot,
                            enabled = transportOk && !state.flowRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            Icon(Icons.TwoTone.Bolt, null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.action_root),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    },
                )
            } else {
                SettingsRow(
                    icon = Icons.TwoTone.Block,
                    isError = true,
                    title = when (state.catalogState) {
                        CatalogState.ERROR -> stringResource(R.string.catalog_error)
                        CatalogState.LOADING -> stringResource(R.string.catalog_loading)
                        else -> stringResource(R.string.status_unsupported)
                    },
                    description = stringResource(R.string.status_not_rooted_desc),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                )
            }
        }
    }
}

// ─────────── Общие компоненты ───────────

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TransportCard(state: UiState, vm: MainViewModel) {
    SettingsRow(
        icon = Icons.TwoTone.LinkOff,
        isError = true,
        title = when (state.transport) {
            TransportState.ShizukuNeedsPermission -> stringResource(R.string.transport_shizuku_perm)
            else -> stringResource(R.string.transport_none)
        },
        description = when (state.transport) {
            TransportState.ShizukuNeedsPermission -> stringResource(R.string.transport_shizuku_perm_desc)
            else -> stringResource(R.string.transport_none_desc)
        },
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        onClick = {
            if (state.transport == TransportState.ShizukuNeedsPermission) {
                vm.requestShizukuPermission()
            } else {
                vm.openShizukuApp()
            }
        },
    )
}

@Composable
private fun DeviceGroup(state: UiState) {
    SettingsGroup(title = stringResource(R.string.device_title)) {
        val d = state.device
        if (d == null) {
            Row(Modifier.padding(16.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } else {
            SettingsRow(
                title = stringResource(R.string.device_model),
                icon = Icons.TwoTone.DevicesOther,
                trailing = { TrailingValue("${d.marketName} (${d.model})", maxLines = 3) },
            )
            SettingsDivider()
            DeviceRow(stringResource(R.string.device_rom), d.rom)
            SettingsDivider()
            DeviceRow(stringResource(R.string.device_kernel), d.kernelShort)
            SettingsDivider()
            DeviceRow(stringResource(R.string.device_kmi), d.kmi.ifEmpty { "—" })
            SettingsDivider()
            DeviceRow(stringResource(R.string.device_patch), d.securityPatch)
            SettingsDivider()
            DeviceRow(stringResource(R.string.device_soc), d.soc)
        }
    }
}

@Composable
private fun DeviceRow(title: String, value: String) {
    SettingsRow(
        title = title,
        trailing = { TrailingValue(value, mono = true, maxLines = 3) },
    )
}

// ─────────── Компактная плашка обновления ───────────

/** Одна строка: иконка, версия и размер, кнопка «Обновить»/«Установить». */
@Composable
fun CompactUpdateCard(
    update: com.rootmyvivo.data.AppUpdate,
    downloadDone: Boolean,
    downloading: Boolean,
    onUpdate: () -> Unit,
) {
    val style = LocalAppStyle.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (style.isLegacy) MaterialTheme.shapes.large else RoundedCornerShape(16.dp),
        color = if (style.isLegacy) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        },
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.TwoTone.CloudDownload,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.update_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (downloading) {
                    Text(
                        stringResource(R.string.update_downloading),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                } else if (update.apkSize > 0) {
                    Text(
                        stringResource(
                            R.string.update_version_size,
                            update.versionName,
                            "%.1f".format(update.apkSize / 1048576.0),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Button(onClick = onUpdate, enabled = !downloading) {
                Text(
                    if (downloadDone) {
                        stringResource(R.string.update_retry_install)
                    } else {
                        stringResource(R.string.update_button)
                    },
                )
            }
        }
    }
}

// ─────────── Диалоги, общие для обеих тем ───────────

@Composable
private fun SharedDialogs(
    state: UiState,
    vm: MainViewModel,
    warnDialog: Boolean,
    onWarnDialog: (Boolean) -> Unit,
    warnDontShow: Boolean,
    onWarnDontShow: (Boolean) -> Unit,
    confirmAction: Boolean,
    onConfirmAction: (Boolean) -> Unit,
    rebootDontShow: Boolean,
    onRebootDontShow: (Boolean) -> Unit,
    onRootStarted: () -> Unit,
    ksuDialog: Boolean,
    onKsuDialog: (Boolean) -> Unit,
) {
    // Предупреждение перед запуском
    if (warnDialog && !state.settings.warnDismissed) {
        AlertDialog(
            onDismissRequest = { onWarnDialog(false) },
            title = { Text(stringResource(R.string.warn_title), fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(onClick = {
                    if (warnDontShow) vm.updateSettings { it.copy(warnDismissed = true) }
                    onWarnDialog(false)
                    vm.startRoot()
                    onRootStarted()
                }) {
                    Text(stringResource(R.string.warn_go))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WarningRow(Icons.TwoTone.Bolt, stringResource(R.string.warn_panics))
                    WarningRow(Icons.TwoTone.CheckCircle, stringResource(R.string.warn_stay))
                    WarningRow(Icons.Rounded.Refresh, stringResource(R.string.warn_retry))
                    Spacer(Modifier.height(4.dp))
                    DontShowRow(warnDontShow, onWarnDontShow)
                }
            },
            dismissButton = {
                TextButton(onClick = { onWarnDialog(false) }) {
                    Text(stringResource(R.string.warn_cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }

    // Подтверждение перезагрузки userspace / рестарта
    if (confirmAction) {
        AlertDialog(
            onDismissRequest = { onConfirmAction(false) },
            title = {
                Text(
                    stringResource(R.string.confirm_softreboot_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.confirm_softreboot_text),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    DontShowRow(rebootDontShow, onRebootDontShow)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (rebootDontShow) {
                        vm.updateSettings { it.copy(softRebootConfirmDismissed = true) }
                    }
                    onConfirmAction(false)
                    vm.performSoftReboot()
                }) {
                    Text(stringResource(R.string.warn_go))
                }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmAction(false) }) {
                    Text(stringResource(R.string.warn_cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }

    // Меню выбора рут-менеджера
    if (ksuDialog) {
        ChoiceDialog(
            title = stringResource(R.string.ksu_title),
            closeLabel = stringResource(R.string.action_close),
            onDismiss = { onKsuDialog(false) },
            items = KsuVariant.entries.map { v ->
                ChoiceDialogItem(
                    label = v.displayName,
                    description = "${v.repo} · ${stringResource(ksuDescription(v))}",
                    selected = state.selectedKsu == v,
                )
            },
            onSelect = { vm.selectKsu(KsuVariant.entries[it]) },
            closeOnSelect = false,
        )
    }
}

@Composable
private fun WarningRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            icon, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DontShowRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) },
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onChange(it) },
        )
        Text(
            stringResource(R.string.warn_dont_show),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ksuDescription(v: KsuVariant): Int = when (v.id) {
    KsuVariant.KERNELSU.id -> R.string.ksu_desc_kernelsu
    KsuVariant.KSU_NEXT.id -> R.string.ksu_desc_ksunext
    KsuVariant.SUKISU.id -> R.string.ksu_desc_sukisu
    else -> R.string.ksu_desc_resukisu
}

// ─────────── Monet Old: прежний экран ───────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LegacyHomeScreen(
    vm: MainViewModel,
    state: UiState,
    onRootStarted: () -> Unit,
    onOpenLastLog: () -> Unit,
    onOpenSupported: () -> Unit,
) {
    var ksuDialog by remember { mutableStateOf(false) }
    var warnDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf(false) }
    var warnDontShow by remember { mutableStateOf(false) }
    var rebootDontShow by remember { mutableStateOf(false) }
    val warnDismissed = state.settings.warnDismissed

    if (warnDialog && warnDismissed) {
        warnDialog = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp),
        )

        // Плашка обновления
        state.appUpdate?.let { update ->
            CompactUpdateCard(
                update = update,
                downloadDone = state.updateDownload?.done == true,
                downloading = state.updateDownload?.let { !it.done } == true,
                onUpdate = vm::updateAction,
            )
        }

        HeroCardLegacy(state) {
            if (warnDismissed) {
                vm.startRoot()
                onRootStarted()
            } else {
                warnDialog = true
            }
        }

        if (state.rootState == RootState.ROOTED) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (state.settings.restartConfirmDismissed) {
                            vm.startRoot()
                            onRootStarted()
                        } else {
                            confirmAction = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_restart_exploit), maxLines = 1)
                }
                Button(
                    onClick = {
                        if (state.settings.softRebootConfirmDismissed) {
                            vm.performSoftReboot()
                        } else {
                            confirmAction = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_softreboot), maxLines = 1)
                }
            }
        }

        when (state.transport) {
            TransportState.None, TransportState.ShizukuNeedsPermission ->
                TransportCardLegacy(state, vm)
            else -> {}
        }

        // Статус
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.status_payload),
                description = when {
                    state.payload != null -> state.payload!!.displayName
                    state.catalogState == CatalogState.ERROR -> stringResource(R.string.catalog_error)
                    else -> stringResource(R.string.payload_short_searching)
                },
                icon = if (state.payload != null) Icons.TwoTone.CheckCircle else Icons.TwoTone.CloudDownload,
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.home_supported_devices),
                icon = Icons.TwoTone.DevicesOther,
                onClick = onOpenSupported,
                trailing = { Chevron() },
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.ksu_title),
                description = state.selectedKsu.displayName,
                icon = Icons.TwoTone.Security,
                onClick = { ksuDialog = true },
                trailing = { Chevron() },
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.home_lastlog),
                icon = Icons.TwoTone.Description,
                onClick = onOpenLastLog,
                trailing = { Chevron() },
            )
        }
        DeviceGroup(state)
        Spacer(Modifier.height(28.dp))
    }

    SharedDialogs(
        state = state,
        vm = vm,
        warnDialog = warnDialog,
        onWarnDialog = { warnDialog = it },
        warnDontShow = warnDontShow,
        onWarnDontShow = { warnDontShow = it },
        confirmAction = confirmAction,
        onConfirmAction = { confirmAction = it },
        rebootDontShow = rebootDontShow,
        onRebootDontShow = { rebootDontShow = it },
        onRootStarted = onRootStarted,
        ksuDialog = ksuDialog,
        onKsuDialog = { ksuDialog = it },
    )
}

/** Прежняя hero-карточка Monet Old. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCardLegacy(state: UiState, onRoot: () -> Unit) {
    val rooted = state.rootState == RootState.ROOTED
    val transportOk = state.transport == TransportState.Adb || state.transport == TransportState.Shizuku
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (rooted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        AnimatedContent(
            targetState = state.rootState,
            transitionSpec = {
                (
                    fadeIn(tween(220)) +
                        slideInVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            initialOffsetY = { it / 8 },
                        )
                    ) togetherWith (
                    fadeOut(tween(150)) +
                        slideOutVertically(
                            animationSpec = tween(150),
                            targetOffsetY = { -it / 8 },
                        )
                    )
            },
            label = "hero",
        ) { rootState ->
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (rootState) {
                    RootState.ROOTED -> {
                        Icon(
                            Icons.TwoTone.CheckCircle, null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.status_rooted),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.status_rooted_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    RootState.CHECKING -> {
                        LoadingIndicator(Modifier.size(56.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.status_checking), style = MaterialTheme.typography.titleMedium)
                    }
                    else -> {
                        Icon(
                            Icons.TwoTone.Bolt, null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.status_not_rooted),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.status_not_rooted_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        val ready = state.payload != null
                        Button(
                            onClick = onRoot,
                            enabled = ready && transportOk && !state.flowRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(62.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Icon(Icons.TwoTone.Bolt, null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when {
                                    !ready && state.catalogState == CatalogState.LOADING ->
                                        stringResource(R.string.catalog_loading)
                                    !ready && state.catalogState == CatalogState.ERROR ->
                                        stringResource(R.string.catalog_retry)
                                    !ready -> stringResource(R.string.status_unsupported)
                                    else -> stringResource(R.string.action_root)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportCardLegacy(state: UiState, vm: MainViewModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = when (state.transport) {
            TransportState.ShizukuNeedsPermission -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.TwoTone.LinkOff, null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        when (state.transport) {
                            TransportState.ShizukuNeedsPermission -> stringResource(R.string.transport_shizuku_perm)
                            else -> stringResource(R.string.transport_none)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when (state.transport) {
                            TransportState.ShizukuNeedsPermission -> stringResource(R.string.transport_shizuku_perm_desc)
                            else -> stringResource(R.string.transport_none_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = if (state.transport == TransportState.ShizukuNeedsPermission) {
                    vm::requestShizukuPermission
                } else {
                    vm::openShizukuApp
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.transport == TransportState.ShizukuNeedsPermission) {
                        stringResource(R.string.action_grant_shizuku)
                    } else {
                        stringResource(R.string.action_open_shizuku)
                    },
                )
            }
        }
    }
}
