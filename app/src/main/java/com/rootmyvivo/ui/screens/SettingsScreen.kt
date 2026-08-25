package com.rootmyvivo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Экран настроек: язык, тема, о приложении.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedLanguage: String,
    onLanguageChange: (String) -> Unit,
    useDynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(48.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Settings, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        }

        // ── Язык ──
        SettingsSection("Язык / Language") {
            var expanded by remember { mutableStateOf(false) }
            
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = when (selectedLanguage) {
                        "ru" -> "Русский"
                        "zh" -> "中文"
                        else -> "English"
                    },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("English") }, onClick = {
                        onLanguageChange("en"); expanded = false })
                    DropdownMenuItem(text = { Text("Русский") }, onClick = {
                        onLanguageChange("ru"); expanded = false })
                    DropdownMenuItem(text = { Text("中文") }, onClick = {
                        onLanguageChange("zh"); expanded = false })
                }
            }
        }

        // ── Внешний вид ──
        SettingsSection("Внешний вид") {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Material You цвета", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = useDynamicColor,
                    onCheckedChange = onDynamicColorChange,
                )
            }
        }

        // ── О приложении ──
        SettingsSection("О приложении") {
            ListItem(
                headlineContent = { Text("RootMyVivo") },
                supportingContent = { Text("v0.1.0-alpha") },
                leadingContent = { Icon(Icons.Rounded.Info, null) },
            )
            ListItem(
                headlineContent = { Text("Автор") },
                supportingContent = { Text("@zenyxx-xd") },
                leadingContent = { Icon(Icons.Rounded.Person, null) },
            )
            ListItem(
                headlineContent = { Text("GitHub") },
                supportingContent = { Text("github.com/zenyxx-xd/RootMyVivo") },
                leadingContent = { Icon(Icons.Rounded.Code, null) },
            )
        }

        // ── Дисклеймер ──
        Surface(shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)) {
            Column(Modifier.padding(14.dp)) {
                Text("⚠ Дисклеймер", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Используй только на устройствах которыми владеешь. Возможны kernel panic — это нормально. Авторы не несут ответственности за последствия.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(14.dp)) { content() }
        }
    }
}
