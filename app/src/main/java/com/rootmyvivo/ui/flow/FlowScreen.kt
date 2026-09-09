package com.rootmyvivo.ui.flow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.animation.fadeIn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.rootmyvivo.R
import com.rootmyvivo.root.FlowEvent
import com.rootmyvivo.root.LogLevel
import com.rootmyvivo.root.Phase
import com.rootmyvivo.vm.ExploitLiveState
import com.rootmyvivo.vm.FlowResult
import com.rootmyvivo.vm.LogEntry
import com.rootmyvivo.vm.LogKind
import com.rootmyvivo.vm.UiState

/** Полноэкранный процесс получения root: шаги, загрузки, терминал, результат. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FlowScreen(
    state: UiState,
    canClose: Boolean,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onSoftReboot: () -> Unit,
    onDismissSoftReboot: () -> Unit,
    onFullReboot: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.flow_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = canClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    // Копировать лог
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    IconButton(
                        onClick = {
                            val text = state.log.joinToString("\n") { it.text }
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                            android.widget.Toast.makeText(ctx, ctx.getString(R.string.log_copied), android.widget.Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            stringResource(R.string.copy_log),
                        )
                    }
                },
            )
        },
    ) { padding ->
        // Геометрия бокса лога — для фонового продолжения тумана
        val logGeometry = remember { mutableStateOf(0f to 0) }
        val bgColor = MaterialTheme.colorScheme.background

        // Продолжение тумана на весь экран: рисуется ЗА карточками, поэтому
        // сверху (над началом затухания) — просто цвет фона, ниже — та же
        // кривая, что у оверлея в логе. Края фильтра больше не видны
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .drawBehind {
                    val (topInRoot, h) = logGeometry.value
                    if (h <= 0 || size.height <= 0f || topInRoot <= 0f) {
                        return@drawBehind
                    }
                    val clearPx = CLEAR_ZONE.toPx()
                    val clearFrac = ((h - clearPx) / h).coerceIn(0.3f, 0.95f)
                    val topLocal = topInRoot.coerceIn(0f, size.height)
                    val total = size.height
                    val stops = buildList {
                        add(0f to bgColor)
                        add(topLocal / total to bgColor)
                        for (i in 1..10) {
                            val e = (1f - i / 10f).let { it * it }
                            add((topLocal + clearFrac * h * i / 10f) / total to bgColor.copy(alpha = e))
                        }
                        add(1f to androidx.compose.ui.graphics.Color.Transparent)
                    }
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(*stops.toTypedArray()))
                },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 0.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
            Spacer(Modifier.height(4.dp))

            Box(Modifier.padding(horizontal = 20.dp)) {
                StepIndicator(state)
            }

            // Загрузка файла (только во время активного скачивания)
            state.downloadProgress?.let { p ->
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Download, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.downloading),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { p },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = StrokeCap.Round,
                    )
                }
            }

            Box(Modifier.padding(horizontal = 20.dp)) {
                ResultBanner(state, onRetry, onFullReboot)
            }

            // Поток лога (Dopamine-стиль): новые снизу, туман у самого верха
            if (state.log.isNotEmpty()) {
                LogStream(
                    state.log,
                    state.flowRunning,
                    state.exploitLive,
                    Modifier.weight(1f),
                    onGeometry = { top, h -> logGeometry.value = top to h },
                )
            } else if (state.flowRunning) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(48.dp))
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ─────────── Шаги ───────────

@Composable
private fun StepIndicator(state: UiState) {
    if (!state.flowRunning && state.flowResult == null) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Плавная смена названия фазы (slide up/down)
                AnimatedContent(
                    targetState = state.flowPhase,
                    transitionSpec = {
                        val forward = (targetState?.ordinal ?: 0) >= (initialState?.ordinal ?: 0)
                        (
                            slideInVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                initialOffsetY = { if (forward) it / 2 else -it / 2 },
                            ) + fadeIn()
                            ) togetherWith (
                            slideOutVertically(
                                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                targetOffsetY = { if (forward) -it / 2 else it / 2 },
                            ) + fadeOut()
                            )
                    },
                    label = "phase",
                ) { phase ->
                    Text(
                        phaseName(phase) ?: stringResource(R.string.flow_running),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "${state.stepIndex}/${state.stepTotal}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { if (state.stepTotal > 0) state.stepIndex.toFloat() / state.stepTotal else 0f },
                modifier = Modifier.fillMaxWidth(),
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun phaseName(phase: Phase?): String? = when (phase) {
    Phase.CATALOG -> stringResource(R.string.phase_catalog)
    Phase.PAYLOAD -> stringResource(R.string.phase_payload)
    Phase.DOWNLOAD -> stringResource(R.string.phase_download)
    Phase.DEPLOY -> stringResource(R.string.phase_deploy)
    Phase.EXPLOIT -> stringResource(R.string.phase_exploit)
    Phase.ROOT_WAIT -> stringResource(R.string.phase_root_wait)
    Phase.KSU -> stringResource(R.string.phase_ksu)
    null -> null
}

// ─────────── Результат ───────────

@Composable
private fun ResultBanner(state: UiState, onRetry: () -> Unit, onFullReboot: () -> Unit) {
    AnimatedVisibility(
        visible = state.flowResult != null,
        enter = fadeIn(tween(280)) +
            androidx.compose.animation.expandVertically(tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
    ) {
        when (val result = state.flowResult) {
            FlowResult.Success -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle, null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.flow_success),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            stringResource(R.string.flow_success_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            is FlowResult.Failure -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Error, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            stringResource(R.string.flow_failed),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            failureText(result),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                        )
                        failureHint(result)?.let { hint ->
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        if (result.reason == FlowEvent.Reason.SYSTEM_BROKEN) {
                            // Система повреждена — единственный выход: полная перезагрузка
                            Button(onClick = onFullReboot) {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_full_reboot))
                            }
                        } else {
                            Button(onClick = onRetry) {
                                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }
            null -> {}
        }
    }
}

@Composable
private fun failureHint(f: FlowResult.Failure): String? = when (f.reason) {
    FlowEvent.Reason.NO_TRANSPORT -> stringResource(R.string.hint_no_transport)
    FlowEvent.Reason.NO_PAYLOAD -> stringResource(R.string.hint_no_payload)
    FlowEvent.Reason.DOWNLOAD -> stringResource(R.string.hint_download)
    FlowEvent.Reason.DEPLOY -> stringResource(R.string.hint_deploy)
    FlowEvent.Reason.EXPLOIT -> stringResource(R.string.hint_exploit)
    FlowEvent.Reason.TIMEOUT -> stringResource(R.string.hint_timeout)
    FlowEvent.Reason.STOPPED -> null
    FlowEvent.Reason.KSU -> stringResource(R.string.hint_ksu)
    FlowEvent.Reason.SYSTEM_BROKEN -> stringResource(R.string.hint_system_broken)
    FlowEvent.Reason.OTHER -> null
}

@Composable
private fun failureText(f: FlowResult.Failure): String = stringResource(
    when (f.reason) {
        FlowEvent.Reason.NO_TRANSPORT -> R.string.flow_fail_no_transport
        FlowEvent.Reason.NO_PAYLOAD -> R.string.flow_fail_no_payload
        FlowEvent.Reason.DOWNLOAD -> R.string.flow_fail_download
        FlowEvent.Reason.DEPLOY -> R.string.flow_fail_deploy
        FlowEvent.Reason.EXPLOIT -> R.string.flow_fail_exploit
        FlowEvent.Reason.TIMEOUT -> R.string.flow_fail_timeout
        FlowEvent.Reason.STOPPED -> R.string.flow_fail_stopped
        FlowEvent.Reason.KSU -> R.string.flow_fail_ksu
        FlowEvent.Reason.SYSTEM_BROKEN -> R.string.flow_fail_system_broken
        FlowEvent.Reason.OTHER -> R.string.flow_fail_other
    },
)

// ─────────── Поток лога (Dopamine-стиль) ───────────

/**
 * Статусная иконка лога: спиннер ⇄ галка ⇄ крест ⇄ треугольник.
 * Геометрия — официальные Material Symbols (check/close/progress_activity/
 * warning, rounded), контуры дискретизированы в 64 точки. При смене статуса
 * точки интерполируются напрямую — фигура непрерывно перетекает из одной
 * в другую, не растворяясь и не накладываясь.
 */
@Composable
private fun StatusIcon(status: LogLevel, tint: Color, modifier: Modifier = Modifier) {
    val target = iconShape(status)
    var fromShape by remember { mutableStateOf(target) }
    var currentShape by remember { mutableStateOf(target) }
    val morph = remember { androidx.compose.animation.core.Animatable(0f) }

    // Спиннер крутится всегда; вращение плавно уходит при морфе в другую фигуру
    val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin")
    val spinAngle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(1100, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "spinAngle",
    )

    LaunchedEffect(target) {
        if (target != currentShape) {
            fromShape = currentShape
            currentShape = target
            morph.snapTo(0f)
            morph.animateTo(
                1f,
                tween(480, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            )
        }
    }

    val from = remember(fromShape) { shapePoints(fromShape) }
    val to = remember(currentShape) { shapePoints(currentShape) }
    val p = morph.value

    // Во сколько раз крутить: спиннер — 1, чужая фигура — 0, в морфе — плавно
    val spinFactor = when {
        fromShape == IconShape.SPINNER && currentShape == IconShape.SPINNER -> 1f
        currentShape == IconShape.SPINNER -> p
        fromShape == IconShape.SPINNER -> 1f - p
        else -> 0f
    }
    // EMPTY (инфо-строки без иконки) — фигура стягивается в точку и гаснет
    val shapeAlpha = if (currentShape == IconShape.EMPTY) 1f - p else 1f

    androidx.compose.foundation.Canvas(modifier.size(22.dp)) {
        val s = size.minDimension / 24f
        withTransform({ rotate(spinAngle * spinFactor, pivot = center) }) {
            val pts = lerpPoints(from, to, p)
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(pts.first().x * s, pts.first().y * s)
            for (k in 1 until pts.size) {
                path.lineTo(pts[k].x * s, pts[k].y * s)
            }
            path.close()
            val color = if (shapeAlpha >= 1f) tint else tint.copy(alpha = shapeAlpha)
            drawPath(path, color)
        }
    }
}

// ─── Геометрия морфинга: официальные Material Symbols (24-сетка, 64 точки) ───────────

private val CHECK_PTS = floatArrayOf(19.01f, 6.42f, 19.55f, 6.83f, 19.72f, 7.48f, 19.44f, 8.09f, 18.95f, 8.58f, 18.47f, 9.06f, 17.98f, 9.55f, 17.49f, 10.04f, 17.01f, 10.52f, 16.52f, 11.01f, 16.04f, 11.5f, 15.55f, 11.98f, 15.07f, 12.47f, 14.58f, 12.96f, 14.1f, 13.44f, 13.61f, 13.93f, 13.13f, 14.42f, 12.64f, 14.9f, 12.15f, 15.39f, 11.67f, 15.88f, 11.18f, 16.36f, 10.7f, 16.85f, 10.21f, 17.34f, 9.59f, 17.6f, 8.95f, 17.39f, 8.46f, 16.91f, 7.98f, 16.43f, 7.49f, 15.94f, 7.0f, 15.45f, 6.52f, 14.97f, 6.03f, 14.48f, 5.55f, 14.0f, 5.06f, 13.51f, 4.57f, 13.02f, 4.27f, 12.42f, 4.42f, 11.77f, 4.94f, 11.34f, 5.61f, 11.33f, 6.16f, 11.74f, 6.64f, 12.22f, 7.13f, 12.71f, 7.61f, 13.2f, 8.1f, 13.69f, 8.58f, 14.17f, 9.07f, 14.66f, 9.55f, 15.15f, 10.04f, 14.66f, 10.52f, 14.18f, 11.01f, 13.69f, 11.49f, 13.21f, 11.98f, 12.72f, 12.47f, 12.23f, 12.95f, 11.75f, 13.44f, 11.26f, 13.92f, 10.78f, 14.41f, 10.29f, 14.9f, 9.8f, 15.38f, 9.32f, 15.87f, 8.83f, 16.36f, 8.34f, 16.84f, 7.86f, 17.33f, 7.37f, 17.81f, 6.89f, 18.34f, 6.45f)

private val CROSS_PTS = floatArrayOf(6.68f, 5.46f, 7.51f, 6.11f, 8.25f, 6.85f, 9.0f, 7.6f, 9.75f, 8.35f, 10.5f, 9.1f, 11.25f, 9.85f, 12.0f, 10.6f, 12.75f, 9.85f, 13.5f, 9.1f, 14.25f, 8.35f, 15.0f, 7.6f, 15.75f, 6.85f, 16.49f, 6.11f, 17.32f, 5.46f, 18.3f, 5.7f, 18.54f, 6.68f, 17.89f, 7.51f, 17.15f, 8.25f, 16.4f, 9.0f, 15.65f, 9.75f, 14.9f, 10.5f, 14.15f, 11.25f, 13.4f, 12.0f, 14.15f, 12.75f, 14.9f, 13.5f, 15.65f, 14.25f, 16.4f, 15.0f, 17.15f, 15.75f, 17.89f, 16.49f, 18.54f, 17.32f, 18.3f, 18.3f, 17.32f, 18.54f, 16.49f, 17.89f, 15.75f, 17.15f, 15.0f, 16.4f, 14.25f, 15.65f, 13.5f, 14.9f, 12.75f, 14.15f, 12.0f, 13.4f, 11.25f, 14.15f, 10.5f, 14.9f, 9.75f, 15.65f, 9.0f, 16.4f, 8.25f, 17.15f, 7.51f, 17.89f, 6.68f, 18.54f, 5.7f, 18.3f, 5.46f, 17.32f, 6.11f, 16.49f, 6.85f, 15.75f, 7.6f, 15.0f, 8.35f, 14.25f, 9.1f, 13.5f, 9.85f, 12.75f, 10.6f, 12.0f, 9.85f, 11.25f, 9.1f, 10.5f, 8.35f, 9.75f, 7.6f, 9.0f, 6.85f, 8.25f, 6.11f, 7.51f, 5.46f, 6.68f, 5.7f, 5.7f)

private val SPINNER_PTS = floatArrayOf(11.95f, 2.0f, 12.98f, 2.79f, 12.35f, 3.94f, 10.94f, 4.06f, 9.55f, 4.36f, 8.23f, 4.91f, 7.05f, 5.7f, 6.02f, 6.68f, 5.16f, 7.81f, 4.52f, 9.08f, 4.14f, 10.45f, 4.0f, 11.87f, 4.1f, 13.29f, 4.43f, 14.67f, 5.03f, 15.97f, 5.85f, 17.13f, 6.85f, 18.14f, 8.01f, 18.96f, 9.3f, 19.56f, 10.68f, 19.9f, 12.1f, 20.0f, 13.52f, 19.87f, 14.89f, 19.49f, 16.17f, 18.86f, 17.3f, 18.0f, 18.28f, 16.97f, 19.08f, 15.79f, 19.63f, 14.48f, 19.93f, 13.09f, 20.05f, 11.67f, 21.18f, 11.01f, 22.0f, 12.03f, 21.9f, 13.45f, 21.59f, 14.84f, 21.08f, 16.17f, 20.39f, 17.41f, 19.54f, 18.55f, 18.53f, 19.56f, 17.39f, 20.41f, 16.14f, 21.1f, 14.81f, 21.6f, 13.42f, 21.9f, 12.0f, 22.0f, 10.58f, 21.9f, 9.19f, 21.6f, 7.86f, 21.09f, 6.61f, 20.41f, 5.47f, 19.56f, 4.46f, 18.55f, 3.61f, 17.41f, 2.92f, 16.17f, 2.41f, 14.84f, 2.1f, 13.45f, 2.0f, 12.03f, 2.09f, 10.6f, 2.39f, 9.21f, 2.89f, 7.88f, 3.58f, 6.63f, 4.43f, 5.49f, 5.43f, 4.48f, 6.57f, 3.62f, 7.81f, 2.93f, 9.14f, 2.42f, 10.53f, 2.11f)

private val WARN_PTS = floatArrayOf(11.63f, 3.07f, 12.55f, 3.16f, 13.12f, 3.93f, 13.61f, 4.77f, 14.09f, 5.6f, 14.57f, 6.44f, 15.06f, 7.27f, 15.54f, 8.11f, 16.02f, 8.95f, 16.51f, 9.78f, 16.99f, 10.62f, 17.47f, 11.45f, 17.96f, 12.29f, 18.44f, 13.12f, 18.92f, 13.96f, 19.41f, 14.8f, 19.89f, 15.63f, 20.37f, 16.47f, 20.86f, 17.3f, 21.34f, 18.14f, 21.82f, 18.98f, 22.25f, 19.83f, 21.96f, 20.72f, 21.07f, 21.0f, 20.1f, 21.0f, 19.14f, 21.0f, 18.17f, 21.0f, 17.21f, 21.0f, 16.24f, 21.0f, 15.28f, 21.0f, 14.31f, 21.0f, 13.35f, 21.0f, 12.38f, 21.0f, 11.41f, 21.0f, 10.45f, 21.0f, 9.48f, 21.0f, 8.52f, 21.0f, 7.55f, 21.0f, 6.59f, 21.0f, 5.62f, 21.0f, 4.66f, 21.0f, 3.69f, 21.0f, 2.73f, 21.0f, 1.91f, 20.56f, 1.81f, 19.64f, 2.28f, 18.8f, 2.76f, 17.96f, 3.25f, 17.13f, 3.73f, 16.29f, 4.21f, 15.45f, 4.7f, 14.62f, 5.18f, 13.78f, 5.66f, 12.95f, 6.15f, 12.11f, 6.63f, 11.27f, 7.11f, 10.44f, 7.6f, 9.6f, 8.08f, 8.77f, 8.56f, 7.93f, 9.05f, 7.1f, 9.53f, 6.26f, 10.01f, 5.42f, 10.5f, 4.59f, 10.98f, 3.75f)

private enum class IconShape { SPINNER, CHECK, CROSS, WARN, EMPTY }

private fun iconShape(status: LogLevel): IconShape = when (status) {
    LogLevel.RUNNING -> IconShape.SPINNER
    LogLevel.OK -> IconShape.CHECK
    LogLevel.ERROR -> IconShape.CROSS
    LogLevel.WARN -> IconShape.WARN
    else -> IconShape.EMPTY
}

/** Контур фигуры как 64 точки (24-сетка). EMPTY — точка в центре. */
private fun shapePoints(shape: IconShape): List<androidx.compose.ui.geometry.Offset> {
    val arr = when (shape) {
        IconShape.SPINNER -> SPINNER_PTS
        IconShape.CHECK -> CHECK_PTS
        IconShape.CROSS -> CROSS_PTS
        IconShape.WARN -> WARN_PTS
        IconShape.EMPTY -> return List(64) { androidx.compose.ui.geometry.Offset(12f, 12f) }
    }
    return List(arr.size / 2) {
        androidx.compose.ui.geometry.Offset(arr[it * 2], arr[it * 2 + 1])
    }
}

/** Почленнная интерполяция точек двух фигур. */
private fun lerpPoints(
    from: List<androidx.compose.ui.geometry.Offset>,
    to: List<androidx.compose.ui.geometry.Offset>,
    p: Float,
): List<androidx.compose.ui.geometry.Offset> = List(from.size) { i ->
    from[i] + (to[i] - from[i]) * p
}

/**
 * Строка лога: иконка + текст.
 * Анимация появления (blur + spring scale из ниоткуда) — только для реально
 * новых строк (animateIn); при скролле история появляется сразу, без «выпрыгивания».
 */
@Composable
private fun LogLine(
    entry: LogEntry,
    animateIn: Boolean,
    modifier: Modifier = Modifier,
) {
    val targetColor = when (entry.status) {
        LogLevel.RUNNING -> MaterialTheme.colorScheme.secondary
        LogLevel.OK -> MaterialTheme.colorScheme.primary
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.PLAIN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(400),
        label = "logColor",
    )

    // Появление из ниоткуда: маленькая размытая строка вырастает и проясняется.
    // Без баунса — просто плавное появление. История при скролле — сразу на месте.
    var appeared by remember(entry.id) { mutableStateOf(!animateIn) }
    LaunchedEffect(entry.id) {
        // небольшая пауза: старые строки успевают плавно уехать вверх,
        // новая проявляется уже на освободившемся месте
        kotlinx.coroutines.delay(180)
        appeared = true
    }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.45f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "appearScale",
    )
    val appearAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(420),
        label = "appearAlpha",
    )
    val appearBlur by animateDpAsState(
        targetValue = if (appeared) 0.dp else 9.dp,
        animationSpec = tween(450),
        label = "appearBlur",
    )

    Row(
        modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(appearAlpha)
            .blur(appearBlur)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(entry.status, color)
        Spacer(Modifier.width(12.dp))
        Text(
            entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** Чистая зона снизу: примерно три строки лога, выше начинается туман. */
private val CLEAR_ZONE = 100.dp

/**
 * Строка шага эксплойта. Закрыта — обычная строка потока со стрелкой вправо;
 * раскрыта — закреплена внизу, стрелка наверх, над ней поднимается живой лог.
 */
@Composable
private fun ExploitLine(
    entry: LogEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by androidx.compose.animation.animateColorAsState(
        targetValue = logColor(entry.status),
        animationSpec = tween(400),
        label = "exploitColor",
    )
    val rotation by animateFloatAsState(
        targetValue = if (expanded) -90f else 0f,
        animationSpec = tween(250),
        label = "chevron",
    )

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(entry.status, color)
        Spacer(Modifier.width(12.dp))
        Text(
            entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation),
        )
    }
}

/** Цвет строки лога по статусу. */
@Composable
private fun logColor(status: LogLevel): Color = when (status) {
    LogLevel.RUNNING -> MaterialTheme.colorScheme.secondary
    LogLevel.OK -> MaterialTheme.colorScheme.primary
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    LogLevel.PLAIN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Поток лога (Dopamine-стиль): новые логи снизу (reverseLayout), первые строки
 * прижаты к низу экрана. Туман — оверлеи на весь бокс: градиент непрозрачности +
 * AGSL-шейдер гауссова размытия (API 33+), размывающий попиксельно по высоте —
 * без привязки к строкам, даже одна строка на границе размывается плавно.
 * Внизу/вверху список оттягивается резиной с плавным возвратом.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LogStream(
    log: List<LogEntry>,
    flowRunning: Boolean,
    exploitLive: ExploitLiveState,
    modifier: Modifier = Modifier,
    onGeometry: (topInRoot: Float, height: Int) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    val bg = MaterialTheme.colorScheme.background
    val density = androidx.compose.ui.platform.LocalDensity.current
    val clearPx = with(density) { CLEAR_ZONE.toPx() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Все id, уже бывшие в списке: анимация появления — только для новых строк.
    // null = первая композиция: вернувшись на экран со старым логом, ничего не анимируем.
    val seenIds = remember { mutableStateOf<Set<Long>?>(null) }
    val newIds = remember(log, flowRunning) {
        val ids = log.map { it.id }.toSet()
        val prev = seenIds.value
        when {
            prev == null && log.isEmpty() -> emptySet()
            prev == null && !flowRunning -> emptySet() // экран открыт после завершения — всё старое
            prev == null -> ids // начало процесса — логи анимируются
            else -> ids - prev
        }
    }
    androidx.compose.runtime.SideEffect {
        seenIds.value = (seenIds.value ?: emptySet()) + log.map { it.id }.toSet()
    }

    // Раскрыт ли живой лог эксплойта; livePresent отстаёт при закрытии,
    // чтобы блок успел проиграть анимацию сворачивания до смены списка
    var exploitExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var livePresent by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(exploitExpanded) {
        if (exploitExpanded) {
            livePresent = true
            listState.scrollToItem(0)
        } else {
            kotlinx.coroutines.delay(360)
            livePresent = false
        }
    }

    // Новые логи приходят в index 0 (низ). Автоскролл — только если пользователь
    // у последних логов; чтение истории не прерываем. Мгновенный scrollToItem:
    // анимация обрывалась гонкой (вставка в index 0 сдвигает якорь на 1 —
    // условие == 0 отваливалось и список «застревал» на предпоследней строке)
    LaunchedEffect(log.size, exploitLive.lines.size) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
    }

    // Высота бокса — для градиента тумана
    var viewportH by remember { mutableStateOf(0) }
    // Доля высоты, ниже которой тумана нет (граница чистой зоны)
    val clearFrac = if (viewportH > 0) ((viewportH - clearPx) / viewportH).coerceIn(0.3f, 0.95f) else 0.85f

    // AGSL-шейдер прогрессивного размытия (туман поверх всего бокса)
    val fogShader = if (android.os.Build.VERSION.SDK_INT >= 33) {
        remember { android.graphics.RuntimeShader(FOG_BLUR_SHADER) }
    } else {
        null
    }

    // Резинка за край списка: оттягивание с затуханием и плавным возвратом
    val rubber = remember { androidx.compose.animation.core.Animatable(0f) }
    val maxRubber = with(density) { 80.dp.toPx() }
    val rubberConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput &&
                    available.y != 0f && kotlin.math.abs(rubber.value) < maxRubber
                ) {
                    // затухающий прирост: чем сильнее оттянуто, тем тяжелее тянуть дальше
                    val damp = 1f - kotlin.math.abs(rubber.value) / maxRubber
                    val next = (rubber.value + available.y * 0.45f * damp)
                        .coerceIn(-maxRubber, maxRubber)
                    scope.launch { rubber.snapTo(next) }
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                if (rubber.value != 0f) {
                    scope.launch {
                        rubber.animateTo(
                            0f,
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                }
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            // Обрезка ровно по границе бокса: выше неё строки уходили бы под
            // карточки без тумана. Край невидим — градиент у верхней кромки
            // уже на 100% непрозрачен, контент там скрыт
            .clipToBounds()
            .onGloballyPositioned {
                viewportH = it.size.height
                onGeometry(it.boundsInRoot().top, it.size.height)
            },
    ) {
        val exploitEntry = log.lastOrNull { it.kind == LogKind.EXPLOIT }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rubberConnection)
                .graphicsLayer {
                    translationY = rubber.value
                    // Размытие — фильтр на весь список: радиус растёт с высотой,
                    // координаты в пространстве слоя (стабильны при скролле)
                    if (fogShader != null && size.height > 0f) {
                        fogShader.setFloatUniform("fogEnd", size.height - clearPx)
                        fogShader.setFloatUniform("maxBlur", 10.dp.toPx())
                        renderEffect = android.graphics.RenderEffect
                            .createRuntimeShaderEffect(fogShader, "content")
                            .asComposeRenderEffect()
                    }
                },
            reverseLayout = true,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
        ) {
            if (livePresent && exploitEntry != null) {
                // Записи новее шапки — ниже неё (новейшая в самом низу):
                // пока шапка — последняя строка лога, она и стоит в низу;
                // когда приходят новые записи, они появляются под ней как обычно
                items(
                    log.filter { it.id > exploitEntry.id }.asReversed(),
                    key = { it.id },
                ) { entry ->
                    LogLine(
                        entry = entry,
                        animateIn = entry.id in newIds,
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    )
                }
                item(key = "exploit_header") {
                    ExploitLine(
                        entry = exploitEntry,
                        expanded = true,
                        onToggle = { exploitExpanded = false },
                    )
                }
                item(key = "live_block") {
                    LiveLogBlock(exploitLive.lines, expanded = exploitExpanded)
                }
                items(
                    log.filter { it.id < exploitEntry.id }.asReversed(),
                    key = { it.id },
                ) { entry ->
                    LogLine(
                        entry = entry,
                        animateIn = entry.id in newIds,
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                    )
                }
            } else {
                items(log.asReversed(), key = { it.id }) { entry ->
                    if (entry.kind == LogKind.EXPLOIT) {
                        ExploitLine(
                            entry = entry,
                            expanded = false,
                            onToggle = { exploitExpanded = true },
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        )
                    } else {
                        LogLine(
                            entry = entry,
                            animateIn = entry.id in newIds,
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                        )
                    }
                }
            }
        }

        // Туман-непрозрачность: градиент поверх бокса (как в 0.4.24),
        // плотность растёт по квадратичной кривой к верху
        val fogStops = buildList {
            for (i in 0..10) {
                val f = clearFrac * i / 10f
                val e = (1f - f / clearFrac).let { it * it }
                add(f to bg.copy(alpha = e))
            }
            add(1f to androidx.compose.ui.graphics.Color.Transparent)
        }
        Box(
            Modifier
                .matchParentSize()
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(*fogStops.toTypedArray())),
        )
    }
}

/**
 * Блок живого лога эксплойта: раскрывается от шапки и сворачивается к ней
 * анимацией высоты; строки идут снизу вверх, новые — ближе к шапке.
 */
@Composable
private fun LiveLogBlock(lines: List<com.rootmyvivo.vm.LiveLogLine>, expanded: Boolean) {
    // Появление блока в композиции тоже анимируем: сначала «не виден»,
    // затем разворачиваемся
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    androidx.compose.animation.AnimatedVisibility(
        visible = expanded && shown,
        enter = androidx.compose.animation.expandVertically(
            animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        ) + fadeIn(tween(320)),
        exit = androidx.compose.animation.shrinkVertically(
            animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        ) + fadeOut(tween(240)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                ),
        ) {
            lines.forEach { line ->
                LiveLine(line)
            }
        }
    }
}

/**
 * Строка живого лога: появляется «из ниоткуда» (маленькая, размытая, прозрачная
 * → вырастает), ANSI-коды и CVE-маркеры вычищены, счётчик попыток (attempt=N/M)
 * вынесен в чип справа — виден всегда.
 */
@Composable
private fun LiveLine(line: com.rootmyvivo.vm.LiveLogLine) {
    val (text, counter) = remember(line.id) { cleanLiveLine(line.text) }

    // Появление из ниоткуда — как у обычных строк лога
    var appeared by remember(line.id) { mutableStateOf(false) }
    LaunchedEffect(line.id) {
        kotlinx.coroutines.delay(180)
        appeared = true
    }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.45f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "liveAppearScale",
    )
    val appearAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(420),
        label = "liveAppearAlpha",
    )
    val appearBlur by animateDpAsState(
        targetValue = if (appeared) 0.dp else 9.dp,
        animationSpec = tween(450),
        label = "liveAppearBlur",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(appearAlpha)
            .blur(appearBlur)
            .padding(start = 34.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var overflow by remember(line.id) { mutableStateOf(false) }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
            onTextLayout = { overflow = it.hasVisualOverflow },
            modifier = Modifier
                .weight(1f)
                .then(if (overflow) Modifier.fadeRightEdge() else Modifier),
        )
        if (counter != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                counter,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

/** ANSI-коды и CVE-маркеры прочь; счётчик попыток — отдельным чипом. */
private fun cleanLiveLine(raw: String): Pair<String, String?> {
    var s = Regex("\\u001B\\[[0-9;]*m").replace(raw, "").trim()
    s = Regex("CVE-\\d{4}-\\d+").replace(s, "")
    val m = Regex("attempt=(\\d+)/(\\d+)").findAll(s).lastOrNull()
    val counter = m?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
    if (m != null) s = s.replaceRange(m.range, "")
    s = s.replace(Regex("\\s{2,}"), " ").trim(' ', ';', ':', ',')
    return s.ifEmpty { "…" } to counter
}

/** Мягкое затемнение правого края обрезающегося текста (без резкой границы). */
private fun Modifier.fadeRightEdge(fade: androidx.compose.ui.unit.Dp = 16.dp): Modifier =
    this
        .graphicsLayer()
        .drawWithContent {
            drawContent()
            val w = size.width
            val start = (w - fade.toPx()).coerceAtLeast(0f)
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.Transparent),
                    startX = start,
                    endX = w,
                ),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }

/**
 * AGSL-шейдер тумана: единый фильтр — и непрозрачность, и размытие одной
 * кривой (квадратичной от границы чистой зоны fogEnd к верху), поэтому они
 * не могут разойтись: где размытие — там и туман, до самого верха бокса.
 * Размытие — гауссова спиральная выборка по золотому углу, без зерна.
 */
private const val FOG_BLUR_SHADER = """
uniform shader content;
uniform float fogEnd;
uniform float maxBlur;

half4 main(float2 coord) {
    float t = clamp((fogEnd - coord.y) / fogEnd, 0.0, 1.0);
    float r = maxBlur * t * t;
    if (r < 0.5) {
        return content.eval(coord);
    }
    float wsum = 0.0;
    half4 sum = half4(0.0);
    for (int i = 0; i < 28; i++) {
        float fi = float(i) + 0.5;
        float ang = fi * 2.39996323;
        float rad = r * sqrt(fi / 28.0);
        float2 off = float2(cos(ang), sin(ang)) * rad;
        float w = exp(-(rad * rad) / (2.0 * r * r * 0.25));
        sum += content.eval(coord + off) * w;
        wsum += w;
    }
    return half4(sum.rgb / wsum, sum.a / wsum);
}
"""

