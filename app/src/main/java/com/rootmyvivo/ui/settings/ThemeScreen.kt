package com.rootmyvivo.ui.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.data.AppThemeName
import com.rootmyvivo.data.ThemeMode
import com.rootmyvivo.ui.common.ChoiceDialog
import com.rootmyvivo.ui.common.ChoiceDialogItem
import com.rootmyvivo.ui.common.SettingsDivider
import com.rootmyvivo.ui.common.SettingsGroup
import com.rootmyvivo.ui.common.SettingsRow
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState

/** Экран «Тема»: выбор оформления, режим светлое/тёмное, динамика, жесты. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(vm: MainViewModel, state: UiState, onClose: () -> Unit) {
    var modeDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme_screen_title)) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Оформление ──
            SettingsGroup(title = stringResource(R.string.theme_screen_style)) {
                ThemeCard(
                    name = stringResource(R.string.theme_name_monet),
                    description = stringResource(R.string.theme_name_monet_desc),
                    swatches = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    selected = state.settings.appTheme == AppThemeName.MONET,
                    onClick = { vm.updateSettings { it.copy(appTheme = AppThemeName.MONET) } },
                )
                SettingsDivider()
                ThemeCard(
                    name = stringResource(R.string.theme_name_monet_old),
                    description = stringResource(R.string.theme_name_monet_old_desc),
                    swatches = listOf(
                        androidx.compose.ui.graphics.Color(0xFF7C4DFF),
                        androidx.compose.ui.graphics.Color(0xFF00BFA5),
                        androidx.compose.ui.graphics.Color(0xFFEDEDF4),
                    ),
                    selected = state.settings.appTheme == AppThemeName.MONET_OLD,
                    onClick = { vm.updateSettings { it.copy(appTheme = AppThemeName.MONET_OLD) } },
                )
                SettingsDivider()
                ThemeCard(
                    name = stringResource(R.string.theme_name_originos),
                    description = stringResource(R.string.theme_name_originos_desc),
                    swatches = listOf(
                        androidx.compose.ui.graphics.Color(0xFF5C6470),
                        androidx.compose.ui.graphics.Color(0xFFDFE3EB),
                        androidx.compose.ui.graphics.Color(0xFFF3F3F6),
                    ),
                    selected = state.settings.appTheme == AppThemeName.ORIGIN_OS,
                    onClick = { vm.updateSettings { it.copy(appTheme = AppThemeName.ORIGIN_OS) } },
                )
            }

            // ── Режим и цвета ──
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_theme),
                    description = themeModeName(state.settings.themeMode),
                    icon = Icons.Rounded.BrightnessAuto,
                    onClick = { modeDialog = true },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.dynamic_colors),
                    description = stringResource(R.string.dynamic_colors_desc),
                    icon = Icons.Rounded.Palette,
                    trailing = {
                        Switch(
                            checked = state.settings.dynamicColors,
                            onCheckedChange = { v -> vm.updateSettings { it.copy(dynamicColors = v) } },
                        )
                    },
                )
            }

            // ── Жесты ──
            SettingsGroup {
                SettingsRow(
                    title = stringResource(R.string.settings_predictive_back),
                    description = stringResource(R.string.settings_predictive_back_desc),
                    icon = Icons.Rounded.Gesture,
                    trailing = {
                        Switch(
                            checked = state.settings.predictiveBack,
                            onCheckedChange = { v -> vm.updateSettings { it.copy(predictiveBack = v) } },
                        )
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (modeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            closeLabel = stringResource(R.string.action_close),
            onDismiss = { modeDialog = false },
            items = listOf(
                ChoiceDialogItem(stringResource(R.string.theme_auto), selected = state.settings.themeMode == ThemeMode.AUTO),
                ChoiceDialogItem(stringResource(R.string.theme_light), selected = state.settings.themeMode == ThemeMode.LIGHT),
                ChoiceDialogItem(stringResource(R.string.theme_dark), selected = state.settings.themeMode == ThemeMode.DARK),
            ),
            onSelect = { i ->
                vm.updateSettings { it.copy(themeMode = ThemeMode.entries[i]) }
            },
        )
    }
}

@Composable
private fun themeModeName(mode: ThemeMode): String = when (mode) {
    ThemeMode.AUTO -> stringResource(R.string.theme_auto)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

/** Карточка темы: превью-палитра, название, описание, галочка выбора. */
@Composable
private fun ThemeCard(
    name: String,
    description: String,
    swatches: List<androidx.compose.ui.graphics.Color>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // превью: три полоски палитры в скруглённом прямоугольнике
        Row(
            Modifier
                .size(width = 52.dp, height = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            swatches.forEach { c ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(c),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
