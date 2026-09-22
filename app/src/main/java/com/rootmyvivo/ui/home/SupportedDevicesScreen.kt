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
import com.rootmyvivo.data.DeviceKernel
import com.rootmyvivo.vm.CatalogState
import com.rootmyvivo.vm.UiState

/**
 * Каталог v5: карточка = физическое тело («iQOO Neo 11 • V2520A»). Тела с
 * живой сборкой показывают список ядер чипами «6.6.89-1f718» (версия +
 * git-хэш, переносятся строкой ниже, не обрезаются). Тела вообще без ядер —
 * «временно отключено». Тела, у которых ядра есть, но все мёртвые, не
 * показываем. Каталог берём из состояния: главный экран уже загрузил его,
 * второй сетевой запрос при открытии страницы не нужен.
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

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
                    val info = state.device
                    val myDevice = info?.let { i ->
                        cat.devices.firstOrNull { d ->
                            (i.model.isNotEmpty() && d.models.any { it.equals(i.model, true) }) ||
                                (i.marketName.isNotEmpty() && d.names.any { it.equals(i.marketName, true) })
                        }
                    }
                    // Показываем: живые тела и глобально отключённые (без ядер);
                    // тело со списком только мёртвых сборок не показываем
                    val shown = cat.devices.filter {
                        cat.kernelsOf(it).isEmpty() || cat.isSupported(it)
                    }
                    // Карточка пользователя всегда первой, над всеми телами
                    val ordered = myDevice?.let { mine ->
                        shown.sortedByDescending { it.id == mine.id }
                    } ?: shown
                    // Живое ядро телефона: готовая сборка корпуса, паттерн
                    // которой совпадает с живым uname (подсветка чипа в карточке).
                    // Считаем по списку ядер корпуса, а не по state.payload:
                    // подсветка не должна зависеть от того, успел ли главный
                    // экран сматчить пейлоад
                    val myBuildId = remember(cat, myDevice, info) {
                        val dev = myDevice
                        val kernel = info?.kernel?.takeIf { it.isNotEmpty() }
                        if (dev == null || kernel == null) null
                        else cat.kernelsOf(dev)
                            .firstOrNull { it.build.ready && it.build.specificity(kernel) > 0 }
                            ?.build?.id
                    }
                    val kernelsTotal = shown.sumOf { cat.kernelsOf(it).size }
                    Text(
                        stringResource(R.string.supported_count, shown.size, kernelsTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    // карточки появляются каскадом, как в остальном приложении
                    ordered.forEachIndexed { i, device ->
                        val mine = myDevice != null && device.id == myDevice.id
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
                            // После карточки пользователя — увеличенный отступ
                            // до следующего тела
                            Column {
                                DeviceRow(
                                    device = device,
                                    kernels = cat.kernelsOf(device),
                                    sameBody = mine,
                                    liveMine = state.payload?.device?.id == device.id,
                                    currentBuild = if (mine) myBuildId ?: state.payload?.build?.id else null,
                                )
                                if (mine) Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Карточка тела: иконка статуса, заголовок «нейм • код», разделитель, чипы
 * ядер. Карточка пользователя подсвечена всегда: живое поддерживаемое ядро —
 * сильный акцент (primaryContainer), тело без живого ядра — мягкая подсветка
 * (secondaryContainer). Тело без ядер — красный текст «временно отключено».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(
    device: CatalogDevice,
    kernels: List<DeviceKernel>,
    sameBody: Boolean,
    liveMine: Boolean,
    currentBuild: String?,
) {
    val supported = kernels.isNotEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = when {
            liveMine -> MaterialTheme.colorScheme.primaryContainer
            sameBody -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
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
                    fontWeight = if (liveMine) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (liveMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (sameBody) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            stringResource(R.string.supported_your),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
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
                // Все ядра корпуса: переносятся, а не обрезаются
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    kernels.forEach { dk ->
                        KernelChip(
                            label = dk.build.label,
                            ready = dk.build.ready,
                            current = dk.build.id == currentBuild,
                        )
                    }
                }
            }
        }
    }
}

/** Метка сборки ядра: живые — обычным текстом, мёртвые — зачёркнутым. */
@Composable
private fun KernelChip(label: String, ready: Boolean, current: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when {
            current -> MaterialTheme.colorScheme.primary
            ready -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> Color.Transparent
        },
        border = when {
            current -> null
            ready -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        },
    ) {
        Text(
            label,
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
