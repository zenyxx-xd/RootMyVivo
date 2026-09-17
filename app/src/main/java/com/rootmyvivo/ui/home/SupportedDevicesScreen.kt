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
import com.rootmyvivo.data.Catalog
import com.rootmyvivo.data.CatalogDevice
import com.rootmyvivo.data.DeviceKernel
import com.rootmyvivo.data.PayloadCatalog
import com.rootmyvivo.vm.UiState

/**
 * Каталог v5: карточка = физическое тело («iQOO Neo 11 • V2520A»), внутри —
 * ВСЕ известные сборки ядра чипами вида «6.6.89-1f718»: живые обычным,
 * мёртвые — зачёркнутым. Разные сборки одного корпуса не дробятся по
 * карточкам и не режутся многоточием: чипы переносятся строкой ниже.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportedDevicesScreen(
    state: UiState,
    catalogUrl: String,
    onClose: () -> Unit,
) {
    var catalog by remember(catalogUrl) { mutableStateOf<PayloadCatalog?>(null) }
    var failed by remember(catalogUrl) { mutableStateOf(false) }
    val client = remember(catalogUrl) { Catalog(catalogUrl) }
    LaunchedEffect(client) {
        client.fetch()
            .onSuccess { catalog = it }
            .onFailure { failed = true }
    }

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
                failed -> Text(
                    stringResource(R.string.dev_catalog_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                catalog == null -> Row(Modifier.padding(16.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                else -> {
                    val cat = catalog!!
                    val info = state.device
                    val myDevice = info?.let { i ->
                        cat.devices.firstOrNull { d ->
                            (i.model.isNotEmpty() && d.models.any { it.equals(i.model, true) }) ||
                                (i.marketName.isNotEmpty() && d.names.any { it.equals(i.marketName, true) })
                        }
                    }
                    // Показываем только тела с живой сборкой: полностью
                    // отключённые модели не показываем вовсе
                    val shown = cat.devices.filter { cat.isSupported(it) }
                    val kernelsTotal = shown.sumOf { cat.kernelsOf(it).size }
                    Text(
                        stringResource(R.string.supported_count, shown.size, kernelsTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    // карточки появляются каскадом, как в остальном приложении
                    shown.forEachIndexed { i, device ->
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
                                mine = myDevice?.id == device.id,
                                currentBuild =
                                    if (myDevice?.id == device.id) state.payload?.build?.id else null,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(
    device: CatalogDevice,
    kernels: List<DeviceKernel>,
    mine: Boolean,
    currentBuild: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (mine) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // карточка показывается только для живых тел — иконка всегда «ок»
                Icon(
                    Icons.Rounded.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    device.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium,
                    color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    // название не растягивает карточку: максимум 2 строки,
                    // бейдж всегда виден рядом
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mine) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            stringResource(R.string.supported_your),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
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
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color = when {
                current -> MaterialTheme.colorScheme.onPrimary
                ready -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            },
            textDecoration = if (ready) null else TextDecoration.LineThrough,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
