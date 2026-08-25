package com.rootmyvivo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.AppSettings
import com.rootmyvivo.MainViewModel
import com.rootmyvivo.ThemeMode
import com.rootmyvivo.core.KsuVariant

/**
 * Экран настроек: язык, тема, KSU вариант, каталог, о приложении.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    vm: MainViewModel,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(40.dp))

        // ── Заголовок ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Text("Настройки", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
        }

        // ── Язык ──
        SettingsCard(title = "Язык", icon = Icons.Rounded.Language) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChip("ru", "Русский", settings.language == "ru") {
                    vm.updateSettings { it.copy(language = "ru") }
                }
                LanguageChip("en", "English", settings.language == "en") {
                    vm.updateSettings { it.copy(language = "en") }
                }
                LanguageChip("zh", "中文", settings.language == "zh") {
                    vm.updateSettings { it.copy(language = "zh") }
                }
            }
        }

        // ── Внешний вид ──
        SettingsCard(title = "Внешний вид", icon = Icons.Rounded.Palette) {
            // Тема: авто / светлая / тёмная
            Text("Тема", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(ThemeMode.AUTO, "Авто", Icons.Rounded.BrightnessAuto,
                    settings.themeMode == ThemeMode.AUTO) {
                    vm.updateSettings { it.copy(themeMode = ThemeMode.AUTO) }
                }
                ThemeChip(ThemeMode.LIGHT, "Светлая", Icons.Rounded.LightMode,
                    settings.themeMode == ThemeMode.LIGHT) {
                    vm.updateSettings { it.copy(themeMode = ThemeMode.LIGHT) }
                }
                ThemeChip(ThemeMode.DARK, "Тёмная", Icons.Rounded.DarkMode,
                    settings.themeMode == ThemeMode.DARK) {
                    vm.updateSettings { it.copy(themeMode = ThemeMode.DARK) }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(10.dp))

            // Динамические цвета Material You
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Material You",
                        style = MaterialTheme.typography.bodyLarge)
                    Text("Цвета из системы (Android 12+)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = settings.dynamicColors,
                    onCheckedChange = { v -> vm.updateSettings { it.copy(dynamicColors = v) } },
                )
            }
        }

        // ── Рут ──
        val selectedKsu by vm.state.collectAsState()
        SettingsCard(title = "Рут-менеджер", icon = Icons.Rounded.Shield) {
            Text("Какой KSU устанавливать после эксплойта",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            KsuVariant.entries.forEach { variant ->
                val selected = selectedKsu.selectedKsu == variant
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .clickable { vm.selectKsu(variant) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { vm.selectKsu(variant) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(variant.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text("${variant.repo} · ${variant.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ── Каталог пейлоадов ──
        SettingsCard(title = "Каталог пейлоадов", icon = Icons.Rounded.CloudSync) {
            var url by remember(settings.catalogUrl) { mutableStateOf(settings.catalogUrl) }
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL каталога") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                supportingText = { Text("JSON с описанием поддерживаемых устройств") },
                trailingIcon = {
                    if (url != com.rootmyvivo.core.CatalogClient.DEFAULT_URL) {
                        IconButton(onClick = {
                            vm.updateSettings { it.copy(catalogUrl = com.rootmyvivo.core.CatalogClient.DEFAULT_URL) }
                        }) {
                            Icon(Icons.Rounded.Restore, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { vm.updateSettings { it.copy(catalogUrl = url.trim()) } },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Сохранить")
            }
        }

        // ── О приложении ──
        SettingsCard(title = "О приложении", icon = Icons.Rounded.Info) {
            AboutRow("Версия", "0.1.0-alpha")
            AboutRow("Автор", "@zenyxx-xd")
            AboutRow("Исходный код", "github.com/zenyxx-xd/RootMyVivo")
            AboutRow("Каталог", "github.com/zenyxx-xd/RootMyVivo-Payloads")
            AboutRow("Эксплойт", "CVE-2026-43499 (GhostLock)")
        }

        // ── Дисклеймер ──
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
        ) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.Warning, null,
                    tint = MaterialTheme.colorScheme.onErrorContainer)
                Column {
                    Text("Дисклеймер",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Используй только на устройствах которыми владеешь. Возможны kernel panic — это нормально. Авторы не несут ответственности за последствия.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ═══════════════════════ Компоненты ═══════════════════════

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun RowScope.LanguageChip(code: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "langBg"
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        label = "langFg"
    )
    Box(
        Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
            .background(bg).clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RowScope.ThemeChip(mode: ThemeMode, label: String, icon: ImageVector,
                      selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "themeBg"
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        label = "themeFg"
    )
    Row(
        Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
            .background(bg).clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = fg)
        Spacer(Modifier.width(5.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium,
            maxLines = 1, softWrap = false)
    }
}@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}
