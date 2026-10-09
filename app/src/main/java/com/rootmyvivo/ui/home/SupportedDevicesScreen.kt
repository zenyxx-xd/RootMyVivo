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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
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
import com.rootmyvivo.data.DeviceInfo
import com.rootmyvivo.data.PayloadCatalog
import com.rootmyvivo.vm.CatalogState
import com.rootmyvivo.vm.UiState

/**
 * Поддерживаемые устройства: три секции с подзаголовками — «Ваше устройство»
 * (карточка юзера: DF по его ветке ядра + GL-пейлоады), «Метод DirtyFrag»
 * (таблица по веткам) и «Все устройства vivo/iQOO» (каталог, сортировка
 * по алфавиту названий).
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
                    // ── Секция: Ваше устройство ──
                    SectionTitle(stringResource(R.string.supported_your))
                    UserDeviceCard(state, cat)

                    // ── Секция: Метод DirtyFrag ──
                    SectionTitle(stringResource(R.string.supported_df_section))
                    DirtyFragTableCard()

                    // ── Секция: Все устройства vivo/iQOO ──
                    SectionTitle(stringResource(R.string.supported_all_devices))
                    // Показываем: живые тела и глобально отключённые (без ядер);
                    // тело со списком только мёртвых сборок не показываем
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
                    ordered.forEachIndexed { i, device ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay((i * 40L).coerceAtMost(400L))
                            visible = true
                        }
                        AnimatedVisibility(
                            visible = visible,
                            enter = expandVertically(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                            ) + fadeIn(tween(300, easing = FastOutSlowInEasing)),
                        ) {
                            DeviceRow(
                                device = device,
                                kernels = cat.kernelsOf(device),
                                buildsById = cat.builds,
                            )
                        }
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
 * Карточка устройства пользователя — показывается ВСЕГДА (даже без
 * записи в каталоге). Крестик слева — только когда недоступен ни один
 * метод: DF-ветка без ko/6.1 И нет живых GL-сборок.
 */
@Composable
private fun UserDeviceCard(state: UiState, cat: PayloadCatalog) {
    val d = state.device ?: return
    // Каталожное тело юзера (может не быть — карточка всё равно рисуется)
    val myDev = cat.devices.firstOrNull { dev ->
        (d.model.isNotEmpty() && dev.models.any { it.equals(d.model, true) }) ||
            (d.marketName.isNotEmpty() && dev.names.any { it.equals(d.marketName, true) })
    }
    val kernels = myDev?.let { cat.kernelsOf(it) } ?: emptyList()
    // Живое ядро юзера — для подсветки чипа
    val myBuildId = kernels.firstOrNull {
        it.build.ready && d.kernel.isNotEmpty() && it.build.specificity(d.kernel) > 0
    }?.build?.id

    // DF по ветке ядра юзера: галочка, если ko под ветку есть (не 6.1)
    val parts = d.kernelShort.split(".").map { it.toIntOrNull() ?: 0 }
    val maj = parts.getOrNull(0) ?: 0
    val min = parts.getOrNull(1) ?: 0
    val dfActive = (maj == 5 && min == 10) || (maj == 5 && min == 15) ||
        (maj == 6 && (min == 6 || min == 12 || min == 18))
    val dfLimit = when {
        maj == 5 && min == 10 -> "5.10.254"
        maj == 5 && min == 15 -> "5.15.204"
        maj == 6 && min == 6 -> "6.6.137"
        maj == 6 && min == 12 -> "6.12.86"
        maj == 6 && min == 18 -> "6.18.28"
        else -> ""
    }
    // Крестик карточки — ни один метод недоступен
    val disabled = myDev != null && kernels.isEmpty()
    val noMethod = !dfActive && (myDev == null || kernels.none { it.build.ready })

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (noMethod || disabled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (noMethod || disabled) Icons.Rounded.Cancel else Icons.Rounded.CheckCircle,
                    null,
                    tint = if (noMethod || disabled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    myDev?.title ?: listOf(d.marketName, d.model)
                        .filter { it.isNotEmpty() }.joinToString(" • "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
            // DirtyFrag: по ветке ядра юзера, без привязки к девайсу
            MethodStatusLine(
                name = "DirtyFrag",
                available = dfActive,
                status = if (dfActive) {
                    stringResource(R.string.df_table_active, dfLimit)
                } else {
                    stringResource(R.string.df_table_dead)
                },
            )
            Spacer(Modifier.height(8.dp))
            // GhostLock: пейлоады каталога для ядер тела; галочки нет —
            // галочка/крестик решаются на уровне всей карточки
            Text(
                "GhostLock",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (disabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.supported_temporarily_disabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (kernels.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    kernels.forEach { dk ->
                        KernelChip(
                            label = dk.build.label,
                            ready = dk.build.ready,
                            current = dk.build.id == myBuildId,
                            experimental = dk.build.experimental,
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.payload_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Строка статуса метода в карточке юзера: иконка + имя + текст. */
@Composable
private fun MethodStatusLine(name: String, available: Boolean, status: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (available) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            null,
            tint = if (available) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.size(16.dp),
        )
        Column {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
        Row("6.6", stringResource(R.string.df_table_active, "6.6.137"), true),
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
            Text(
                "DirtyFrag",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
 * Карточка тела каталога: иконка статуса, заголовок «нейм • код»,
 * разделитель, чипы ядер.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(
    device: CatalogDevice,
    kernels: List<com.rootmyvivo.data.DeviceKernel>,
    buildsById: Map<String, com.rootmyvivo.data.KernelBuild> = emptyMap(),
) {
    val supported = kernels.isNotEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (supported) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                    null,
                    tint = if (supported) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    device.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                            current = false,
                            experimental = buildsById[dk.build.id]?.experimental == true,
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
private fun KernelChip(label: String, ready: Boolean, current: Boolean, experimental: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when {
            current -> MaterialTheme.colorScheme.primary
            ready -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> Color.Transparent
        },
        border = when {
            current -> null
            ready -> null
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        },
    ) {
        Text(
            if (experimental && ready) "$label (beta)" else label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color = when {
                current -> MaterialTheme.colorScheme.onPrimary
                ready -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            },
            textDecoration = if (ready) null else TextDecoration.LineThrough,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
