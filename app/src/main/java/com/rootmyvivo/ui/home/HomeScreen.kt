package com.rootmyvivo.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
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
    onOpenFaq: () -> Unit = {},
) {
    var ksuDialog by remember { mutableStateOf(false) }
    var warnDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf(false) }
    // Диалог остановки эксплойта — анти-мисклик, чекер «не показывать» в нём
    var stopDialog by remember { mutableStateOf(false) }
    var stopDontShow by remember { mutableStateOf(false) }
    // Системный файловый менеджер (SAF): выбор кастомного payload.so —
    // без скачиваний, деплоится именно выбранный файл
    val pickPayload = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::onCustomPayloadPicked) }
    // Чекеры «больше не показывать» — локальные: фиксируются только кнопкой
    // действия. Отмена оставляет настройку нетронутой
    var warnDontShow by remember { mutableStateOf(false) }
    var rebootDontShow by remember { mutableStateOf(false) }
    val warnDismissed = state.settings.warnDismissed

    // Системный тост: неподдерживаемый тип файла и прочие одноразовые события
    val appCtx = LocalContext.current
    LaunchedEffect(state.toastRes) {
        state.toastRes?.let {
            android.widget.Toast.makeText(appCtx, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

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
        // Плашка первого запуска: подписка на Telegram-канал автора.
        // Показывается один раз — «Пропустить» и «Перейти» гасят её навсегда
        AnimatedVisibility(
            visible = state.tgBannerVisible,
            enter = expandVertically(
                animationSpec = tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            ) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(240)) + fadeOut(tween(200)),
        ) {
            TelegramPromoCard(vm)
        }
        HeroCard(
            state,
            onRoot = {
                if (state.settings.warnDismissed) {
                    vm.startRoot()
                    onRootStarted()
                } else {
                    warnDialog = true
                }
            },
            onPickPayload = { pickPayload.launch(arrayOf("*/*")) },
            onClearPayload = vm::clearCustomPayload,
        )
        // Принудительная остановка во время выполнения — под главным статусом.
        // Подтверждение можно отключить чекером в диалоге
        if (state.flowRunning) {
            OutlinedButton(
                onClick = {
                    if (state.settings.exploitStopConfirmDismissed) {
                        vm.stopRoot()
                    } else {
                        stopDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Rounded.StopCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_stop_exploit), maxLines = 1)
            }
        }
        // Действие при активном руте: перезагрузка userspace (как на iOS),
        // с подтверждением против мискликов. Перезапуск эксплойта остался
        // в меню разработчика
        if (state.rootState == RootState.ROOTED) {
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
                    downloadDone = state.updateDownload?.done == true,
                    download = state.updateDownload,
                    onUpdate = vm::updateAction,
                )
            }
        }

        // Карточка транспорта — только когда есть проблема (нет Shizuku / нет разрешения)
        when (state.transport) {
            TransportState.None, TransportState.ShizukuNeedsPermission ->
                TransportCard(state, onPermission = vm::requestShizukuPermission, onOpenShizuku = vm::openShizukuApp)
            else -> {}
        }
        // Группа 1: рут-менеджер + пейлоад + характеристики устройства
        InfoGroup(state, onKsuClick = { ksuDialog = true })
        // Группа 2: история запусков, поддерживаемые устройства, FAQ
        NavGroup(
            onOpenLastLog = onOpenLastLog,
            onOpenSupported = onOpenSupported,
            onOpenFaq = onOpenFaq,
        )
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

    // Подтверждение остановки эксплойта — анти-мисклик, как у «Получить рут»:
    // объясняем последствия, чекер фиксируется только подтверждением
    if (stopDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { stopDialog = false },
            title = {
                Text(
                    stringResource(R.string.home_stop_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.home_stop_text),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = stopDontShow,
                            onCheckedChange = { stopDontShow = it },
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
                    if (stopDontShow) {
                        vm.updateSettings { it.copy(exploitStopConfirmDismissed = true) }
                    }
                    stopDialog = false
                    vm.stopRoot()
                }) {
                    Text(stringResource(R.string.home_stop_go))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { stopDialog = false }) {
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
private fun HeroCard(
    state: UiState,
    onRoot: () -> Unit,
    onPickPayload: () -> Unit,
    onClearPayload: () -> Unit,
) {
    val rooted = state.rootState == RootState.ROOTED
    val transportOk = state.transport == TransportState.Adb || state.transport == TransportState.Shizuku
    // Тёмная тема определяется по реальной яркости фона: NeoTheme выбирает
    // палитру настройкой, а не только системным флагом
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
                        // Кастомный пейлоад: карточка с контуром (как у кнопки
                        // выбора, без заливки). Снимок держим до конца анимации
                        // сворачивания — иначе контент исчезнет раньше выхода
                        var shownPayload by remember { mutableStateOf(state.customPayload) }
                        LaunchedEffect(state.customPayload) {
                            state.customPayload?.let { shownPayload = it }
                        }
                        AnimatedVisibility(
                            visible = state.customPayload != null,
                            enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                            exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
                        ) {
                            shownPayload?.let { cp ->
                                // Отступ — padding'ом самой карточки: контейнер
                                // AnimatedVisibility кладёт детей в Box, и
                                // отдельный Spacer внутрь высоты не добавил бы
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    color = Color.Transparent,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                ) {
                                    Column(
                                        Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Rounded.Code, null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Text(
                                                stringResource(R.string.home_custom_payload_selected),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Rounded.Description, null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    cp.displayName,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                )
                                                Text(
                                                    stringResource(
                                                        R.string.home_custom_payload_size,
                                                        "%.1f".format(cp.size / 1048576.0),
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            TextButton(onClick = onClearPayload) {
                                                Text(stringResource(R.string.home_custom_payload_cancel))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Выбор кастомного .so — внутри карточки под кнопкой
                        // рута. При полученном ру ветка не рендерится вовсе;
                        // при выбранном файле карточка выше показывает текущий.
                        // Телефон официально не поддерживается — кнопка залита
                        // (путь к руту единственный); иначе — обычный контур
                        // Material без усиленной обводки (в светлой теме
                        // контрастный контур выглядел лишним)
                        AnimatedVisibility(
                            visible = state.customPayload == null,
                            enter = expandVertically(tween(260)) + fadeIn(tween(260)),
                            exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
                        ) {
                            val filled = state.catalogState == CatalogState.READY && state.payload == null
                            AnimatedContent(
                                targetState = filled,
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                                label = "pickStyle",
                            ) { isFilled ->
                                if (isFilled) {
                                    Button(
                                        onClick = onPickPayload,
                                        enabled = !state.flowRunning,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp),
                                        shape = MaterialTheme.shapes.large,
                                    ) {
                                        Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.home_pick_payload), maxLines = 1)
                                    }
                                } else {
                                    // В тёмной теме — усиленный контрастный контур,
                                    // в светлой — обычный Material-контур
                                    OutlinedButton(
                                        onClick = onPickPayload,
                                        enabled = !state.flowRunning,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp),
                                        shape = MaterialTheme.shapes.large,
                                        border = if (isDark) {
                                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        } else {
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                        },
                                    ) {
                                        Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.home_pick_payload), maxLines = 1)
                                    }
                                }
                            }
                        }
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
    val ready = state.payload != null || state.customPayload != null
    val enabled = ready && transportOk && !state.flowRunning
    val kernelShort = state.device?.kernelShort.orEmpty()
    // «Не поддерживается» всегда объясняет причину: нет тела в каталоге →
    // устройство; тело есть, но живой сборки под ядро нет → ядро. Без
    // транспорта кнопка неактивна и говорит, чего не хватает
    val label = when {
        state.customPayload != null -> stringResource(R.string.action_root)
        state.catalogState == CatalogState.LOADING -> stringResource(R.string.catalog_loading)
        state.catalogState == CatalogState.ERROR -> stringResource(R.string.catalog_retry)
        !ready && state.catalogState == CatalogState.READY && !state.deviceInCatalog ->
            stringResource(R.string.home_btn_unsupported_device)
        !ready && kernelShort.isNotEmpty() ->
            stringResource(R.string.home_btn_unsupported_kernel, kernelShort)
        !ready -> stringResource(R.string.status_unsupported)
        !transportOk -> stringResource(R.string.home_btn_need_transport)
        else -> stringResource(R.string.action_root)
    }
    // Смена состояний («Получить рут» ↔ «Не поддерживается» ↔ загрузка
    // каталога) всегда анимирована в обе стороны: текст едет с фейдом,
    // цвет перетекает. В disabled — стандартные Material-токены
    // (onSurface 12%/38%): именно «неактивная» кнопка, а не залитая серая
    val container by animateColorAsState(
        if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        animationSpec = tween(220),
        label = "rootBtnContainer",
    )
    val contentColor by animateColorAsState(
        if (enabled) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        animationSpec = tween(220),
        label = "rootBtnContent",
    )
    Button(
        onClick = onRoot,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = container,
            disabledContentColor = contentColor,
        ),
    ) {
        Icon(Icons.Rounded.Bolt, null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        AnimatedContent(
            targetState = label,
            transitionSpec = {
                (
                    fadeIn(tween(160)) +
                        slideInVertically(tween(220), initialOffsetY = { it / 3 })
                    ) togetherWith (
                    fadeOut(tween(120)) +
                        slideOutVertically(tween(120), targetOffsetY = { -it / 3 })
                    )
            },
            label = "rootBtnLabel",
        ) { text ->
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}


// ─────────── Компактная плашка обновления ───────────

/** Одна строка: иконка, версия и размер, кнопка «Обновить»/«Установить».
 *  При скачивании — живой прогресс-бар с мегабайтами. */
@Composable
private fun CompactUpdateCard(
    update: com.rootmyvivo.data.AppUpdate,
    downloadDone: Boolean,
    download: com.rootmyvivo.vm.UpdateDownloadState?,
    onUpdate: () -> Unit,
) {
    val downloading = download != null && !download.done
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
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
                if (downloading && download != null) {
                    val mb = "%.1f / %.1f МБ".format(
                        download.read / 1048576.0,
                        download.total / 1048576.0,
                    )
                    when (val f = download.fraction) {
                        null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        else -> LinearProgressIndicator(
                            progress = { f.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        mb,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

// ─────────── Плашка первого запуска: Telegram-канал ───────────

@Composable
private fun TelegramPromoCard(vm: MainViewModel) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    // Иконка «выпрыгивает» пружиной вслед за разворотом карточки
    val iconScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "tgIcon",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
    ) {
        Column(
            Modifier
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ),
                )
                .padding(20.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .scale(iconScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Send, null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tg_promo_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.tg_promo_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = vm::dismissTgBanner,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.tg_promo_skip))
                }
                Button(
                    onClick = vm::openTelegramChannel,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.tg_promo_go))
                }
            }
        }
    }
}

// ─────────── Группа 1: менеджер + пейлоад + устройство ───────────

@Composable
private fun InfoGroup(state: UiState, onKsuClick: () -> Unit) {
    SettingsGroup {
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
        SettingsDivider()
        // Пейлоад
        SettingsRow(
            title = stringResource(R.string.status_payload),
            description = when {
                state.customPayload != null ->
                    stringResource(R.string.home_custom_payload_active, state.customPayload!!.displayName)
                state.payload != null -> state.payload!!.displayName
                state.catalogState == CatalogState.LOADING ->
                    stringResource(R.string.payload_short_searching)
                // каталог загружен, но записи для этого устройства нет —
                // «поиск» больше не идёт, честно говорим «не найден»
                state.catalogState == CatalogState.READY -> stringResource(R.string.payload_not_found)
                else -> stringResource(R.string.catalog_error)
            },
            icon = when {
                state.payload != null || state.customPayload != null -> Icons.Rounded.Verified
                state.catalogState == CatalogState.READY -> Icons.Rounded.SearchOff
                else -> Icons.Rounded.Search
            },
        )
        // Характеристики устройства — переехали сюда отдельным блоком
        val d = state.device
        if (d == null) {
            Row(Modifier.padding(16.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        } else {
            SettingsDivider(indentIcon = false)
            SettingsRow(
                title = stringResource(R.string.device_model),
                // Маркет-нейм из каталога + PD-код: «vivo X200 Pro (PD2405)».
                // Без записи в каталоге — как есть: Build.MODEL (Build.DEVICE)
                trailing = {
                    TrailingValue(
                        (state.catalogMarketName ?: d.marketName) + " (${d.model})",
                        maxLines = 3,
                    )
                },
            )
            SettingsDivider(indentIcon = false)
            SettingsRow(
                title = stringResource(R.string.device_rom),
                trailing = { TrailingValue(d.rom, mono = true, maxLines = 3) },
            )
            SettingsDivider(indentIcon = false)
            SettingsRow(
                title = stringResource(R.string.device_kernel),
                trailing = { TrailingValue(d.kernel, mono = true, maxLines = 3) },
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

// ─────────── Группа 2: навигация (история / устройства / FAQ) ───────────

@Composable
private fun NavGroup(
    onOpenLastLog: () -> Unit,
    onOpenSupported: () -> Unit,
    onOpenFaq: () -> Unit,
) {
    SettingsGroup {
        // История запусков — всегда видима: на свежей установке это
        // единственная точка входа к логам, пустая история не повод её прятать
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
        SettingsDivider()
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
        SettingsRow(
            title = stringResource(R.string.home_faq),
            description = stringResource(R.string.home_faq_desc),
            icon = Icons.Rounded.HelpOutline,
            onClick = onOpenFaq,
            trailing = {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
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
