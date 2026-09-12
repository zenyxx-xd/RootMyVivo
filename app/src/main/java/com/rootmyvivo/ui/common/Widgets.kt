package com.rootmyvivo.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Стиль ReSukiSU: группа настроек — карточка со скруглёнными углами,
 * элементы внутри разделены тонкими разделителями.
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 28.dp, bottom = 8.dp),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                content()
            }
        }
    }
}

/**
 * Разделитель между строками группы.
 * indentIcon=true — выравнивание под текст строк с иконкой (72dp),
 * false — под текст строк без иконки (28dp).
 */
@Composable
fun SettingsDivider(indentIcon: Boolean = true) {
    HorizontalDivider(
        modifier = Modifier.padding(
            start = if (indentIcon) 72.dp else 28.dp,
            end = 12.dp,
        ),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/**
 * Строка настройки в стиле ReSukiSU: иконка, заголовок, описание, trailing.
 * При нажатии — пружинная анимация формы и фона (expressive motion).
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
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // expressive: углы сжимаются при нажатии (как shapeForInteraction в ReSukiSU)
    val corner by animateDpAsState(
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

    androidx.compose.foundation.layout.BoxWithConstraints {
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
                // новая тема: скруглённый квадрат вместо круга
                val iconShape = if (com.rootmyvivo.ui.theme.LocalAppStyle.current.tintedSquareIcons) {
                    RoundedCornerShape(14.dp)
                } else {
                    CircleShape
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(iconShape)
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

// ─────────── Диалог выбора (язык / тема / KSU) ───────────

@Immutable
data class ChoiceDialogItem(
    val label: String,
    val description: String? = null,
    val selected: Boolean = false,
)

/**
 * Диалог выбора в стиле приложения: строки с радио-точками и плавающей
 * плашкой, которая пружинно переезжает к выбранному варианту.
 *
 * @param closeOnSelect true — закрывать после выбора (язык/тема),
 *   false — оставить открытым, чтобы увидеть анимацию плашки (рут-менеджер).
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
    val style = com.rootmyvivo.ui.theme.LocalAppStyle.current

    // новая тема: диалог вырастает из 92% с мягкой пружиной
    var appeared by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appeared = true }
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
                            val top by animateDpAsState(
                                targetValue = with(density) { target.top.toDp() },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "pillTop",
                            )
                            val pillHeight by animateDpAsState(
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
