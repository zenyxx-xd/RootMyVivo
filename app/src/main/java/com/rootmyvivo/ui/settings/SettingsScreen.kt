package com.rootmyvivo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.data.Catalog
import com.rootmyvivo.data.ThemeMode
import com.rootmyvivo.root.KsuVariant
import com.rootmyvivo.ui.common.ChoiceDialog
import com.rootmyvivo.ui.common.ChoiceDialogItem
import com.rootmyvivo.ui.common.SettingsDivider
import com.rootmyvivo.ui.common.SettingsGroup
import com.rootmyvivo.ui.common.SettingsRow
import com.rootmyvivo.ui.common.TrailingValue
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    state: UiState,
    onDevOpen: () -> Unit = {},
    onAboutOpen: () -> Unit = {},
    onThemeOpen: () -> Unit = {},
) {
    var langDialog by remember { mutableStateOf(false) }
    var ksuDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp),
        )

        // ── Внешний вид ──
        SettingsGroup(title = stringResource(R.string.settings_appearance)) {
            SettingsRow(
                title = stringResource(R.string.settings_language),
                description = languageName(state.settings.language),
                icon = Icons.Rounded.Language,
                onClick = { langDialog = true },
                trailing = { Chevron() },
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.theme_screen_title),
                description = themeName(state.settings.themeMode),
                icon = Icons.Rounded.Palette,
                onClick = onThemeOpen,
                trailing = { Chevron() },
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.dynamic_colors),
                description = stringResource(R.string.dynamic_colors_desc),
                icon = Icons.Rounded.BrightnessAuto,
                trailing = {
                    Switch(
                        checked = state.settings.dynamicColors,
                        onCheckedChange = { v -> vm.updateSettings { it.copy(dynamicColors = v) } },
                    )
                },
            )
        }

        // ── Рут ──
        SettingsGroup(title = stringResource(R.string.settings_root)) {
            SettingsRow(
                title = stringResource(R.string.ksu_title),
                description = state.selectedKsu.displayName,
                icon = Icons.Rounded.Security,
                onClick = { ksuDialog = true },
                trailing = { Chevron() },
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.settings_boot_restore),
                description = stringResource(R.string.settings_boot_restore_desc),
                icon = Icons.Rounded.RestartAlt,
                trailing = {
                    Switch(
                        checked = state.settings.bootRestore,
                        onCheckedChange = { v -> vm.updateSettings { it.copy(bootRestore = v) } },
                    )
                },
            )
        }

        // ── Другое ──
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.settings_other),
                description = stringResource(R.string.settings_other_desc),
                icon = Icons.Rounded.BugReport,
                onClick = onDevOpen,
                trailing = { Chevron() },
            )
        }

        // ── О приложении ──
        SettingsGroup {
            SettingsRow(
                title = stringResource(R.string.about_title),
                icon = Icons.Rounded.Info,
                onClick = onAboutOpen,
                trailing = { Chevron() },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    val closeLabel = stringResource(R.string.action_close)

    // ── Диалог выбора языка ──
    if (langDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            closeLabel = closeLabel,
            onDismiss = { langDialog = false },
            items = listOf(
                ChoiceDialogItem(stringResource(R.string.lang_system_full), selected = state.settings.language.isEmpty()),
                ChoiceDialogItem(stringResource(R.string.lang_ru_full), selected = state.settings.language == "ru"),
                ChoiceDialogItem(stringResource(R.string.lang_en_full), selected = state.settings.language == "en"),
                ChoiceDialogItem(stringResource(R.string.lang_zh_full), selected = state.settings.language == "zh"),
            ),
            onSelect = { i ->
                vm.updateSettings { it.copy(language = listOf("", "ru", "en", "zh")[i]) }
            },
        )
    }

    // ── Диалог выбора рут-менеджера ──
    if (ksuDialog) {
        ChoiceDialog(
            title = stringResource(R.string.ksu_select),
            closeLabel = closeLabel,
            onDismiss = { ksuDialog = false },
            items = KsuVariant.entries.map { v ->
                ChoiceDialogItem(
                    label = v.displayName,
                    description = "${v.repo} · ${stringResource(ksuDesc(v))}",
                    selected = state.selectedKsu == v,
                )
            },
            onSelect = { i -> vm.selectKsu(KsuVariant.entries[i]) },
            closeOnSelect = false,
        )
    }
}

// ─────────── Компоненты ───────────

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun languageName(code: String): String = when (code) {
    "ru" -> stringResource(R.string.lang_ru_full)
    "en" -> stringResource(R.string.lang_en_full)
    "zh" -> stringResource(R.string.lang_zh_full)
    else -> stringResource(R.string.lang_system_full)
}

@Composable
private fun themeName(mode: ThemeMode): String = when (mode) {
    ThemeMode.AUTO -> stringResource(R.string.theme_auto)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun ksuDesc(v: KsuVariant): Int = when (v.id) {
    KsuVariant.KERNELSU.id -> R.string.ksu_desc_kernelsu
    KsuVariant.KSU_NEXT.id -> R.string.ksu_desc_ksunext
    KsuVariant.SUKISU.id -> R.string.ksu_desc_sukisu
    else -> R.string.ksu_desc_resukisu
}
