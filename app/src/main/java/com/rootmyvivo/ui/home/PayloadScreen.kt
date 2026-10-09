package com.rootmyvivo.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.data.DeviceInfo
import com.rootmyvivo.data.RootMethod
import com.rootmyvivo.ui.common.ChoiceDialog
import com.rootmyvivo.ui.common.ChoiceDialogItem
import com.rootmyvivo.ui.common.SettingsGroup
import com.rootmyvivo.ui.common.SettingsRow
import com.rootmyvivo.vm.CatalogState
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState

/**
 * Окно пейлоада: метод рута (авто / DirtyFrag / GhostLock — диалог выбора,
 * как у рут-менеджера) и кастомный payload.so. Открытие — тап по строке
 * «Пейлоад» на главной; дизайн и навигация — как у остальных окон.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadScreen(
    vm: MainViewModel,
    state: UiState,
    onClose: () -> Unit,
) {
    // SAF-выбор кастомного payload.so — без скачиваний, деплоится именно файл
    val pickPayload = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::onCustomPayloadPicked) }

    var methodDialog by remember { mutableStateOf(false) }

    // Доступность GhostLock: живая сборка в каталоге под это ядро
    val glAvailable = state.payload != null
    // GL-пейлоад как на главной: «GhostLock • 6.6.127-24b70 • PSELECT»
    val glPayloadDesc = state.payload?.let { p ->
        buildString {
            append("GhostLock • ").append(p.build.label)
            p.build.route?.let { append(" • ").append(it.uppercase()) }
        }
    } ?: ""
    // Ядро с git-id (5 символов, как чипы в поддерживаемых): по нему видно
    // точную сборку — «ядро в списке есть, но сборка другая» больше не тайна
    val kernelShort = state.device?.kernelShort.orEmpty()
    val id5 = Regex("""(?:android\d+-\d+-g?)?([0-9a-f]{5,8})$""")
        .find(state.device?.kernel.orEmpty())?.groupValues?.get(1)?.take(5)
    val kernelLabel = if (id5 != null) "$kernelShort-$id5" else kernelShort
    // Причина недоступности GL — как на главной кнопке: тела нет в каталоге
    // или ядро без сборки (с git-id)
    val glUnavailableText = when {
        state.catalogState == CatalogState.LOADING -> stringResource(R.string.payload_short_searching)
        state.catalogState != CatalogState.READY -> stringResource(R.string.catalog_error)
        !state.deviceInCatalog -> stringResource(R.string.home_btn_unsupported_device)
        else -> stringResource(R.string.home_btn_unsupported_kernel, kernelLabel)
    }
    // DF: привязки к девайсам нет — только ядро (с git-id)
    val dfAvailable = state.device?.dfAllowed(state.settings.allowDfAllKernels) == true
    val dfUnavailableText = stringResource(R.string.home_btn_unsupported_kernel, kernelLabel)
    // Авто: что реально будет использоваться (DF — первый приоритет)
    val autoText = when {
        dfAvailable -> stringResource(R.string.payload_method_auto_df)
        glAvailable -> stringResource(R.string.payload_method_auto_gl)
        else -> stringResource(R.string.payload_method_auto_none)
    }

    val methodLabel = when (state.settings.rootMethod) {
        RootMethod.AUTO -> stringResource(R.string.payload_method_auto)
        RootMethod.DIRTYFRAG -> "DirtyFrag"
        RootMethod.GHOSTLOCK -> "GhostLock"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payload_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Главная карточка окна: девайс + ядро + статус методов ──
            // Заголовок: имя из каталога + кодовая модель в скобках
            val heroDeviceTitle = state.device?.let { d ->
                val name = state.payload?.device?.marketName?.takeIf { it.isNotEmpty() } ?: d.marketName
                if (d.model.isNotEmpty()) "$name (${d.model})" else name
            } ?: ""
            // GL при поддержке: «Поддерживается (6.6.127-24b70 • PSELECT)»
            val glHeroStatus = if (glAvailable) {
                state.payload?.let { p ->
                    buildString {
                        append(stringResource(R.string.payload_status_supported))
                        append(" (").append(p.build.label)
                        p.build.route?.let { append(" • ").append(it.uppercase()) }
                        append(")")
                    }
                } ?: stringResource(R.string.payload_status_supported)
            } else {
                glUnavailableText
            }
            ExploitHeroCard(
                deviceTitle = heroDeviceTitle,
                kernel = state.device?.kernel.orEmpty(),
                dfAvailable = dfAvailable,
                glAvailable = glAvailable,
                dfStatusText = if (dfAvailable) stringResource(R.string.payload_status_supported)
                else dfUnavailableText,
                glStatusText = glHeroStatus,
            )

            // ── Метод рута (без подзаголовка) ──
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.payload_method_title),
                    description = methodLabel,
                    icon = Icons.Rounded.Bolt,
                    onClick = { methodDialog = true },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            // ── Кастомный payload.so (без подзаголовка) ──
            SettingsGroup {
                // Кнопки — с горизонтальным отступом как в «Другом»:
                // у группы его нет (строки носят свой паддинг сами)
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    state.customPayload?.let { cp ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
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
                                    TextButton(onClick = vm::clearCustomPayload) {
                                        Text(stringResource(R.string.home_custom_payload_cancel))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedButton(
                        onClick = { pickPayload.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.home_pick_payload), maxLines = 1)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (methodDialog) {
        ChoiceDialog(
            title = stringResource(R.string.payload_method_title),
            closeLabel = stringResource(R.string.action_close),
            onDismiss = { methodDialog = false },
            items = listOf(
                // Авто всегда активно — это авто: выберет то, что доступно
                ChoiceDialogItem(
                    label = stringResource(R.string.payload_method_auto),
                    description = autoText,
                    selected = state.settings.rootMethod == RootMethod.AUTO,
                ),
                ChoiceDialogItem(
                    label = "DirtyFrag",
                    description = if (dfAvailable) stringResource(R.string.payload_method_df_ok)
                    else dfUnavailableText,
                    selected = state.settings.rootMethod == RootMethod.DIRTYFRAG,
                    enabled = dfAvailable,
                    // «Рекомендуется» — только когда оба метода доступны
                    badge = if (dfAvailable && glAvailable) stringResource(R.string.payload_method_recommended) else null,
                ),
                ChoiceDialogItem(
                    label = "GhostLock",
                    description = if (glAvailable) stringResource(R.string.payload_method_gl)
                    else glUnavailableText,
                    selected = state.settings.rootMethod == RootMethod.GHOSTLOCK,
                    enabled = glAvailable,
                ),
            ),
            onSelect = { idx ->
                // Недоступные варианты не выбираются (серые)
                if (idx == 1 && !dfAvailable) return@ChoiceDialog
                if (idx == 2 && !glAvailable) return@ChoiceDialog
                val m = RootMethod.entries.getOrNull(idx) ?: return@ChoiceDialog
                vm.updateSettings { it.copy(rootMethod = m) }
            },
        )
    }
}

/**
 * Главная карточка окна эксплойта: девайс и ядро (как на главной) +
 * статус обоих методов. Без акцентной заливки — та же подложка, что у
 * остальных карточек приложения.
 */
@Composable
private fun ExploitHeroCard(
    deviceTitle: String,
    kernel: String,
    dfAvailable: Boolean,
    glAvailable: Boolean,
    dfStatusText: String,
    glStatusText: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Заголовок карточки: «iQOO Neo 11 (PD2520)» — крупно
            Text(
                deviceTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            // Полное ядро (mono, до трёх строк — как на главной)
            if (kernel.isNotEmpty()) {
                Text(
                    kernel,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(6.dp))
            // Статусы методов: иконка + имя + статус/причина — компактно
            MethodStatusRow(
                name = "DirtyFrag",
                available = dfAvailable,
                statusText = dfStatusText,
            )
            MethodStatusRow(
                name = "GhostLock",
                available = glAvailable,
                statusText = glStatusText,
            )
        }
    }
}

@Composable
private fun MethodStatusRow(
    name: String,
    available: Boolean,
    statusText: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (available) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            null,
            tint = if (available) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )
        Column {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
