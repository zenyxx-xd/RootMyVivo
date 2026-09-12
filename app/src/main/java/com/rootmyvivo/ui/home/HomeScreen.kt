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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
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
import com.rootmyvivo.vm.CatalogState
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.RootState
import com.rootmyvivo.vm.UiState

/** Главный экран: статус → транспорт/пейлоад/менеджер → устройство. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    state: UiState,
    onRootStarted: () -> Unit,
    onOpenLastLog: () -> Unit = {},
    onOpenSupported: () -> Unit = {},
) {
    var ksuDialog by remember { mutableStateOf(false) }
    var warnDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf(false) }
    // Чекеры «больше не показывать» — локальные: фиксируются только кнопкой
    // действия. Отмена оставляет настройку нетронутой
    var warnDontShow by remember { mutableStateOf(false) }
    var rebootDontShow by remember { mutableStateOf(false) }
    val warnDismissed = state.settings.warnDismissed

    // Предупреждение перед запуском (или сразу запуск если скрыто)
    if (warnDialog && warnDismissed) {
        warnDialog = false
    }

    // Предупреждение перед запуском: паники, не выходить, перезапуск
    if (warnDialog && !warnDismissed) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { warnDialog = false },
            title = { Text(stringResource(R.string.warn_title), fontWeight = FontWeight.Bold) },
            confirmButton = {
                Button(onClick = {
                    if (warnDontShow) vm.updateSettings { it.copy(warnDismissed = true) }
                    warnDialog = false
                    vm.startRoot()
                    onRootStarted()
                }) {
                    Text(stringResource(R.string.warn_go))
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Rounded.Bolt, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(stringResource(R.string.warn_panics), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Rounded.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(stringResource(R.string.warn_stay), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(
                            Icons.Rounded.Refresh, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(stringResource(R.string.warn_retry), style = MaterialTheme.typography.bodyMedium)
                    }
                    // Чекбокс "больше не показывать" — кликабельна вся строка;
                    // фиксируется только при подтверждении, отмена не меняет настройку
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .clickable { warnDontShow = !warnDontShow },
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = warnDontShow,
                            onCheckedChange = { warnDontShow = it },
                        )
                        Text(
                            stringResource(R.string.warn_dont_show),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { warnDialog = false }) {
                    Text(stringResource(R.string.warn_cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Header()
        HeroCard(state, onRoot = {
            if (state.settings.warnDismissed) {
                vm.startRoot()
                onRootStarted()
            } else {
                warnDialog = true
            }
        })

        // Действие при активном руте: перезагрузка userspace (как на iOS),
        // с подтверждением против мискликов + перезапуск эксплойта,
        // если рут жив, но KSU не встал (soft reboot не нужен — только
        // повторный прогон цепочки поверх живого рута)
        if (state.rootState == RootState.ROOTED) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Перезапуск эксплойта — контурная: это вторичное действие,
                // заливку оставляем перезагрузке userspace
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
        // Плашка обновления приложения (автопоиск при запуске / «Проверить сейчас»).
        // Компактная, одна строка; диалог при первом нахождении открыт поверх всего
        androidx.compose.animation.AnimatedVisibility(
            visible = state.appUpdate != null,
            enter = androidx.compose.animation.expandVertically(
                animationSpec = androidx.compose.animation.core.tween(
                    320, easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
            ) + androidx.compose.animation.fadeIn(
                androidx.compose.animation.core.tween(320),
            ),
            exit = androidx.compose.animation.shrinkVertically(
                animationSpec = androidx.compose.animation.core.tween(220),
            ) + androidx.compose.animation.fadeOut(
                androidx.compose.animation.core.tween(220),
            ),
        ) {
            state.appUpdate?.let { update ->
                CompactUpdateCard(
                    update = update,
                    downloading = state.updateDownload?.let { !it.done } == true,
                    onUpdate = vm::downloadAndInstallUpdate,
                    onCancel = vm::dismissUpdate,
                )
            }
        }

        // Карточка транспорта — только когда есть проблема (нет Shizuku / нет разрешения)
        when (state.transport) {
            TransportState.None, TransportState.ShizukuNeedsPermission ->
                TransportCard(state, onPermission = vm::requestShizukuPermission, onOpenShizuku = vm::openShizukuApp)
            else -> {}
        }
        StatusGroup(state, onKsuClick = { ksuDialog = true }, onOpenLastLog = onOpenLastLog, onOpenSupported = onOpenSupported)
        DeviceGroup(state)
        Spacer(Modifier.height(28.dp))
    }

    // Подтверждение перезагрузки userspace — анти-мисклик
    if (confirmAction) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmAction = false },
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = rebootDontShow,
                            onCheckedChange = { rebootDontShow = it },
                        )
                        Text(
                            stringResource(R.string.warn_dont_show),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (rebootDontShow) {
                        vm.updateSettings { it.copy(softRebootConfirmDismissed = true) }
                    }
                    confirmAction = false
                    vm.performSoftReboot()
                }) {
                    Text(stringResource(R.string.warn_go))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmAction = false }) {
                    Text(stringResource(R.string.warn_cancel))
                }
            },
            shape = MaterialTheme.shapes.extraLarge,
        )
    }

    // Меню выбора рут-менеджера — как в настройках, без закрытия после выбора
    if (ksuDialog) {
        ChoiceDialog(
            title = stringResource(R.string.ksu_title),
            closeLabel = stringResource(R.string.action_close),
            onDismiss = { ksuDialog = false },
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

// ─────────── Заголовок ───────────

@Composable
private fun Header() {
    Text(
        stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 12.dp),
    )
}

// ─────────── Hero: статус + кнопка ROOT ───────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroCard(state: UiState, onRoot: () -> Unit) {
    val rooted = state.rootState == RootState.ROOTED
    val transportOk = state.transport == TransportState.Adb || state.transport == TransportState.Shizuku
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (rooted) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.secondaryContainer,
    ) {
        // Плавная смена содержимого (проверка → результат)
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
                            Icons.Rounded.CheckCircle, null,
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
                            Icons.Rounded.Bolt, null,
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
                        RootButton(state, transportOk, onRoot)
                        if (transportOk) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    when (state.transport) {
                                        TransportState.Adb -> Icons.Rounded.Usb
                                        else -> Icons.Rounded.Security
                                    },
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    when (state.transport) {
                                        TransportState.Adb -> stringResource(R.string.transport_via_adb)
                                        else -> stringResource(R.string.transport_via_shizuku)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootButton(state: UiState, transportOk: Boolean, onRoot: () -> Unit) {
    val ready = state.payload != null
    Button(
        onClick = onRoot,
        enabled = ready && transportOk && !state.flowRunning,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(24.dp))
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


// ─────────── Компактная плашка обновления ───────────

/** Одна строка: иконка, версия и размер, кнопка «Обновить», крестик. */
@Composable
private fun CompactUpdateCard(
    update: com.rootmyvivo.data.AppUpdate,
    downloading: Boolean,
    onUpdate: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.CloudDownload,
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
            TextButton(onClick = onUpdate, enabled = !downloading) {
                Text(stringResource(R.string.update_button))
            }
            TextButton(onClick = onCancel, enabled = !downloading) {
                Text(stringResource(R.string.update_cancel))
            }
        }
    }
}

// ─────────── Карточка транспорта (только при проблемах) ───────────

@Composable
private fun TransportCard(
    state: UiState,
    onPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
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
                    when (state.transport) {
                        TransportState.ShizukuNeedsPermission -> Icons.Rounded.Security
                        else -> Icons.Rounded.LinkOff
                    },
                    null,
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
                onClick = if (state.transport == TransportState.ShizukuNeedsPermission) onPermission else onOpenShizuku,
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

// ─────────── Статус: транспорт + пейлоад + рут-менеджер (компактно) ───────────

@Composable
private fun StatusGroup(
    state: UiState,
    onKsuClick: () -> Unit,
    onOpenLastLog: () -> Unit,
    onOpenSupported: () -> Unit,
) {
    SettingsGroup {
        // Пейлоад
        SettingsRow(
            title = stringResource(R.string.status_payload),
            description = when {
                state.payload != null -> state.payload!!.displayName
                state.catalogState == CatalogState.ERROR -> stringResource(R.string.catalog_error)
                else -> stringResource(R.string.payload_short_searching)
            },
            icon = if (state.payload != null) Icons.Rounded.Verified else Icons.Rounded.Search,
        )
        SettingsDivider()
        // Поддерживаемые устройства — карточка всех пейлоадов каталога
        SettingsRow(
            title = stringResource(R.string.home_supported_devices),
            icon = Icons.Rounded.PhoneAndroid,
            onClick = onOpenSupported,
            trailing = {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        SettingsDivider()
        // Рут-менеджер
        SettingsRow(
            title = stringResource(R.string.ksu_title),
            description = state.selectedKsu.displayName,
            icon = Icons.Rounded.Security,
            onClick = onKsuClick,
            trailing = {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        // История запусков — всегда видима: на свежей установке это
        // единственная точка входа к логам, пустая история не повод её прятать
        SettingsDivider()
        SettingsRow(
            title = stringResource(R.string.home_lastlog),
            icon = Icons.Rounded.Description,
            onClick = onOpenLastLog,
            trailing = {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ─────────── Устройство ───────────

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
                trailing = { TrailingValue("${d.marketName} (${d.model})", maxLines = 3) },
            )
            SettingsDivider(indentIcon = false)
            SettingsRow(
                title = stringResource(R.string.device_rom),
                trailing = { TrailingValue(d.rom, mono = true, maxLines = 3) },
            )
            SettingsDivider(indentIcon = false)
            SettingsRow(
                title = stringResource(R.string.device_kernel),
                trailing = { TrailingValue(d.kernelShort, mono = true, maxLines = 3) },
            )
            SettingsDivider(indentIcon = false)
            SettingsRow(
                title = stringResource(R.string.device_kmi),
                trailing = { TrailingValue(d.kmi.ifEmpty { "—" }, mono = true, maxLines = 3) },
            )
            SettingsDivider(indentIcon = false)
            SettingsRow(
                title = stringResource(R.string.device_patch),
                trailing = { TrailingValue(d.securityPatch, mono = true, maxLines = 3) },
            )
            SettingsDivider(indentIcon = false)
            SettingsRow(
                title = stringResource(R.string.device_soc),
                trailing = { TrailingValue(d.soc, maxLines = 3) },
            )
        }
    }
}

@Composable
private fun ksuDescription(v: KsuVariant): Int = when (v.id) {
    KsuVariant.KERNELSU.id -> R.string.ksu_desc_kernelsu
    KsuVariant.KSU_NEXT.id -> R.string.ksu_desc_ksunext
    KsuVariant.SUKISU.id -> R.string.ksu_desc_sukisu
    else -> R.string.ksu_desc_resukisu
}
