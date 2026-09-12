package com.rootmyvivo.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.rootmyvivo.ui.theme.LocalAppStyle

/**
 * Группа настроек.
 * Новая тема (стиль ReSukiSU): заголовок — маленькая подпись primary над
 * карточками, сами строки рисуют отдельные карточки (см. SettingsRow).
 * Monet Old: прежняя единая карточка со скруглением и разделителями.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val style = LocalAppStyle.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            if (style.isLegacy) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
                )
            } else {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp),
                )
            }
        }
        if (style.isLegacy) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    content()
                }
            }
        } else {
            // строки сами рисуют карточки; между ними — зазор из SettingsDivider
            Column {
                content()
            }
        }
    }
}

/**
 * Разделитель между строками.
 * Новая тема: зазор между отдельными карточками. Monet Old: линия.
 */
@Composable
fun SettingsDivider(indentIcon: Boolean = true) {
    val style = LocalAppStyle.current
    if (style.isLegacy) {
        HorizontalDivider(
            modifier = Modifier.padding(
                start = if (indentIcon) 72.dp else 28.dp,
                end = 12.dp,
            ),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    } else {
        Spacer(Modifier.height(5.dp))
    }
}

/**
 * Строка настройки.
 * Новая тема (ReSukiSU SettingsBaseWidget): отдельная карточка 16dp на
 * surfaceBright, пружинное сжатие углов при нажатии, иконка 24dp без
 * контейнера, заголовок titleMedium.
 * Monet Old: прежний вид (круглый контейнер иконки, прозрачный фон внутри
 * единой карточки группы).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    selected: Boolean = false,
    isError: Boolean = false,
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val style = LocalAppStyle.current

    if (style.isLegacy) {
        // ── Monet Old: прежняя реализация ──
        val corner by androidx.compose.animation.core.animateDpAsState(
            targetValue = if (pressed) 12.dp else 18.dp,
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            label = "rowCorner",
        )
        val container by animateColorAsState(
            targetValue = when {
                selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                pressed -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> Color.Transparent
            },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "rowBg",
        )
        BoxWithConstraintsLegacy {
            val trailingMax = maxWidth * 0.45f
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(
                                interactionSource = interaction,
                                indication = null,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onClick()
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon, null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (description != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (below != null) {
                        Spacer(Modifier.height(6.dp))
                        below()
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.widthIn(max = trailingMax),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        trailing()
                    }
                }
            }
        }
        return
    }

    // ── Новая тема: отдельная карточка в стиле ReSukiSU ──
    val corner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (pressed) 12.dp else 16.dp,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "rowCorner",
    )
    val container by animateColorAsState(
        targetValue = containerColor ?: when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            pressed -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceBright
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "rowBg",
    )
    val contentColor = when {
        containerColor != null -> androidx.compose.material3.contentColorFor(containerColor)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    androidx.compose.foundation.layout.BoxWithConstraints {
        val trailingMax = maxWidth * 0.45f
        Surface(
            shape = RoundedCornerShape(corner),
            color = container,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onClick()
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (description != null) 64.dp else 56.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(
                        icon, null,
                        tint = if (isError) MaterialTheme.colorScheme.error else contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor,
                    )
                    if (description != null) {
                        Spacer(Modifier.height(1.dp))
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (below != null) {
                        Spacer(Modifier.height(6.dp))
                        below()
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.widthIn(max = trailingMax),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        trailing()
                    }
                }
            }
        }
    }
}

/** Легаси-обёртка над BoxWithConstraints (чтобы не тащить импорт в две ветки). */
@Composable
private fun BoxWithConstraintsLegacy(content: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(content = content)
}

/** Trailing-значение, выровненное по правому краю (версия, ссылка и т.п.). */
@Composable
fun TrailingValue(text: String, mono: Boolean = false, maxLines: Int = 1) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = if (mono) FontFamily.Monospace else null,
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

// ─────────── Диалог выбора (язык / KSU / режим) ───────────

@Immutable
data class ChoiceDialogItem(
    val label: String,
    val description: String? = null,
    val selected: Boolean = false,
)

/**
 * Диалог выбора: строки с радио-точками и плавающей плашкой, которая
 * пружинно переезжает к выбранному варианту. В новой теме диалог
 * вырастает из 92% с мягкой пружиной.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChoiceDialog(
    title: String,
    closeLabel: String,
    onDismiss: () -> Unit,
    items: List<ChoiceDialogItem>,
    onSelect: (Int) -> Unit,
    closeOnSelect: Boolean = true,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowBounds = remember { mutableStateMapOf<Int, Rect>() }
    val selectedIndex = items.indexOfFirst { it.selected }
    val style = LocalAppStyle.current

    // новая тема: диалог вырастает из 92% с мягкой пружиной
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val dialogScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dialogScale",
    )
    val dialogAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(180),
        label = "dialogAlpha",
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = if (style.expressiveDialogs) {
                Modifier.graphicsLayer {
                    scaleX = dialogScale
                    scaleY = dialogScale
                    alpha = dialogAlpha
                }
            } else {
                Modifier
            },
        ) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )

                Box {
                    // Плавающая плашка выбора — пружинно переезжает между строками
                    if (selectedIndex >= 0 && rowBounds.isNotEmpty()) {
                        val target = rowBounds[selectedIndex]
                        if (target != null) {
                            val top by androidx.compose.animation.core.animateDpAsState(
                                targetValue = with(density) { target.top.toDp() },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "pillTop",
                            )
                            val pillHeight by androidx.compose.animation.core.animateDpAsState(
                                targetValue = with(density) { target.height.toDp() },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "pillHeight",
                            )
                            Box(
                                Modifier
                                    .offset(y = top)
                                    .padding(horizontal = 10.dp)
                                    .fillMaxWidth()
                                    .height(pillHeight)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(16.dp),
                                    ),
                            )
                        }
                    }

                    Column {
                        items.forEachIndexed { index, item ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { rowBounds[index] = it.boundsInParent() }
                            ) {
                                ChoiceRow(item = item) {
                                    onSelect(index)
                                    if (closeOnSelect) onDismiss()
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp),
                ) {
                    Text(closeLabel)
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(item: ChoiceDialogItem, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = item.selected, onClick = onClick)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            item.description?.let { desc ->
                Spacer(Modifier.height(2.dp))
                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
