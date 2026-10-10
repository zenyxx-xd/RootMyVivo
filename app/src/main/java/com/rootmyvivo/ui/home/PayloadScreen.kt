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
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RocketLaunch
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.data.DeviceInfo
import com.rootmyvivo.ui.common.SettingsGroup
import com.rootmyvivo.ui.common.SettingsRow
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState

/**
 * Окно эксплоита: карточка юзера (девайс + ядро + используемый метод и
 * поддерживаемые ядра), строка «Метод рута» (клон строки «Эксплоит»
 * главной), кастомный payload.so и внизу — секция «Доступные эксплоиты»
 * с короткими описаниями обоих методов. Выбора метода больше нет: DF
 * для всех ядер кроме 6.1, GL — только 6.1.x (каталог).
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

            // ── Карточка устройства юзера (переехала из поддерживаемых) ──
            UserDeviceCard(state)

            // ── Кастомный payload.so (без подзаголовка) ──
            SettingsGroup {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    // Снапшот: карточка живёт во время exit-анимации
                    var shownCp by remember { mutableStateOf(state.customPayload) }
                    LaunchedEffect(state.customPayload) {
                        state.customPayload?.let { shownCp = it }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.customPayload != null,
                        enter = androidx.compose.animation.expandVertically(
                            androidx.compose.animation.core.tween(260),
                        ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)),
                        exit = androidx.compose.animation.shrinkVertically(
                            androidx.compose.animation.core.tween(220),
                        ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
                    ) {
                    shownCp?.let { cp ->
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
                                        Icons.Rounded.InsertDriveFile, null,
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
                    }
                    // Кнопка выбора — только когда кастомного файла нет;
                    // при отмене плавно возвращается
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.customPayload == null,
                        enter = androidx.compose.animation.expandVertically(
                            androidx.compose.animation.core.tween(260),
                        ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)),
                        exit = androidx.compose.animation.shrinkVertically(
                            androidx.compose.animation.core.tween(220),
                        ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
                    ) {
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
            }

            // ── Доступные эксплоиты — в самом низу ──
            SectionTitle(stringResource(R.string.payload_available_section))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExploitEntry(
                        name = "DirtyFrag",
                        description = stringResource(R.string.payload_df_desc_long),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ExploitEntry(
                        name = "GhostLock",
                        description = stringResource(R.string.payload_gl_desc_long),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Подзаголовок секции — как заголовки групп настроек. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(top = 8.dp, start = 12.dp, bottom = 2.dp),
    )
}

/** Пункт в «Доступных эксплоитах»: имя + короткое описание. */
@Composable
private fun ExploitEntry(name: String, description: String) {
    Column {
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Карточка устройства юзера: девайс (крупно, как заголовок окна), ядро
 * одной строкой с многоточием, используемый метод («DirtyFrag (активен
 * до …)» или «GhostLock» для 6.1) и поддерживаемые ядра-таблетки.
 * Если не поддерживается ничего — крестик и «Устройство не поддерживается».
 */
@Composable
private fun UserDeviceCard(state: UiState) {
    val d = state.device ?: return
    val cat = state.catalogData
    val myDev = cat?.devices?.firstOrNull { dev ->
        (d.model.isNotEmpty() && dev.models.any { it.equals(d.model, true) }) ||
            (d.marketName.isNotEmpty() && dev.names.any { it.equals(d.marketName, true) })
    }
    val kernels = myDev?.let { cat.kernelsOf(it) } ?: emptyList()

    val parts = d.kernelShort.split(".").map { it.toIntOrNull() ?: 0 }
    val maj = parts.getOrNull(0) ?: 0
    val min = parts.getOrNull(1) ?: 0
    val dfLimit = when {
        maj == 5 && min == 10 -> "5.10.254"
        maj == 5 && min == 15 -> "5.15.204"
        maj == 6 && min == 6 -> "6.6.126"
        maj == 6 && min == 12 -> "6.12.86"
        maj == 6 && min == 18 -> "6.18.28"
        else -> ""
    }
    val dfCompat = d.dirtyfragCompatible()
    val is61 = maj == 6 && min == 1
    val glAvailable = kernels.any { it.build.ready } || state.payload != null
    // Метод по dfAllowed (учитывает тумблер «DF на всех ядрах»), не по голой совместимости
    val dfUsed = d.dfAllowed(state.settings.allowDfAllKernels)
    val nothingSupported = !dfUsed && !glAvailable
    // Имя девайса из каталога: «iQOO Neo 11 (V2520A)»
    val heroName = state.payload?.device?.marketName?.takeIf { it.isNotEmpty() }
        ?: myDev?.marketName?.takeIf { it.isNotEmpty() }
        ?: d.marketName
    val heroTitle = "$heroName (${d.model})"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Заголовок — имя девайса из каталога + модель в скобках
            Text(
                heroTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Ядро — одна строка, дальше многоточие (без разделителя от девайса)
            if (d.kernel.isNotEmpty()) {
                Text(
                    d.kernel,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            if (nothingSupported) {
                // Ни DF, ни GL: крестик и текст — тем же шрифтом и размером,
                // что и строка «Используется метод …»
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Cancel, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        stringResource(R.string.home_btn_unsupported_device),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                // Как в бета1: «Используется метод …» — с границей для DF,
                // GL — просто метод. Пейлоад-строкой ниже — каталогные чипы
                Text(
                    if (dfUsed) {
                        stringResource(R.string.payload_method_used_df, dfLimit)
                    } else {
                        stringResource(R.string.payload_method_used_gl)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
