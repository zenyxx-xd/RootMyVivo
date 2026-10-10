package com.rootmyvivo.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.data.CatalogDevice
import com.rootmyvivo.vm.CatalogState
import com.rootmyvivo.vm.UiState

/**
 * Поддерживаемые устройства: секция «Метод GhostLock» (каталог 6.1-ядер,
 * счётчик, карточки без иконок) и карточка DirtyFrag «Все устройства
 * vivo/iQOO» с таблицей ядер. Карточка юзера переехала на экран эксплойта.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedDevicesScreen(
    state: UiState,
    onClose: () -> Unit,
) {
    val cat = state.catalogData

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.supported_title)) },
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
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                state.catalogState == CatalogState.ERROR -> Text(
                    stringResource(R.string.dev_catalog_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                cat == null -> Row(Modifier.padding(16.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                else -> {
                    // ── DirtyFrag: таблица поддержки ядер ──
                    SectionTitle(stringResource(R.string.supported_df_section))
                    DirtyFragTableCard()

                    // ── Метод GhostLock: каталог ──
                    SectionTitle(stringResource(R.string.supported_gl_section))
                    val shown = cat.devices.filter {
                        cat.kernelsOf(it).isEmpty() || cat.isSupported(it)
                    }
                    Text(
                        stringResource(
                            R.string.supported_count,
                            shown.size,
                            shown.sumOf { cat.kernelsOf(it).size },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    // Сортировка строго по алфавиту названия модели
                    val ordered = shown.sortedBy { it.marketName.lowercase() }
                    ordered.forEach { device ->
                        DeviceRow(
                            device = device,
                            kernels = cat.kernelsOf(device),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
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

/**
 * Таблица поддержки DirtyFrag по веткам ядер: до какого патча ветка
 * уязвима (фикс ветки минус один). Таблица действительна только для
 * устройств vivo/iQOO и может быть неточной.
 */
@Composable
private fun DirtyFragTableCard() {
    data class Row(val kernel: String, val status: String, val active: Boolean)
    val rows = listOf(
        Row("5.10", stringResource(R.string.df_table_active, "5.10.254"), true),
        Row("5.15", stringResource(R.string.df_table_active, "5.15.204"), true),
        Row("6.1", stringResource(R.string.df_table_dead), false),
        Row("6.6", stringResource(R.string.df_table_active, "6.6.126"), true),
        Row("6.12", stringResource(R.string.df_table_active, "6.12.86"), true),
        Row("6.18", stringResource(R.string.df_table_active, "6.18.28"), true),
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Заголовок карточки — как имена устройств на карточках GhostLock
            Text(
                stringResource(R.string.supported_all_devices),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        r.kernel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(90.dp),
                    )
                    Text(
                        r.status,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (r.active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.df_table_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

/**
 * Карточка тела каталога: заголовок «нейм • код», разделитель, чипы ядер.
 * Без галочек/крестиков — статус решает наличие живых чипов.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(
    device: CatalogDevice,
    kernels: List<com.rootmyvivo.data.DeviceKernel>,
) {
    val supported = kernels.isNotEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                device.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            if (kernels.isEmpty()) {
                Text(
                    stringResource(R.string.supported_temporarily_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    kernels.forEach { dk ->
                        KernelChip(
                            label = dk.build.label,
                            ready = dk.build.ready,
                            experimental = dk.build.experimental,
                        )
                    }
                }
            }
        }
    }
}

/** Метка сборки ядра: живые — обычным текстом (experimental — с «(beta)»
 *  в скобках), мёртвые (patched/unsupported) — зачёркнутым. */
@Composable
private fun KernelChip(label: String, ready: Boolean, experimental: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (ready) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        border = if (!ready) BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)) else null,
    ) {
        Text(
            if (experimental && ready) "$label (beta)" else label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = if (ready) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            },
            textDecoration = if (ready) null else TextDecoration.LineThrough,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
