package com.rootmyvivo.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.R
import com.rootmyvivo.data.DeviceInfo
import com.rootmyvivo.data.RootMethod
import com.rootmyvivo.ui.common.SettingsDivider
import com.rootmyvivo.ui.common.SettingsGroup
import com.rootmyvivo.ui.common.SettingsRow
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState

/**
 * Окно пейлоада: метод рута (авто / DirtyFrag / GhostLock) и кастомный
 * payload.so. Открытие — тап по строке «Пейлоад» на главной; дизайн и
 * навигация — как у остальных окон (боковой переход, те же секции).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadScreen(
    vm: MainViewModel,
    state: UiState,
    onClose: () -> Unit,
) {
    // SAF-выбор кастомного payload.so — без скачиваний, деплоится именно файл
    val pickPayload = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::onCustomPayloadPicked) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.payload_screen_title)) },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Метод рута ──
            SettingsGroup(title = stringResource(R.string.payload_method_title)) {
                val dfOk = state.device?.dirtyfragCompatible() == DeviceInfo.DfCompat.OK
                MethodRow(
                    label = stringResource(R.string.payload_method_auto),
                    description = stringResource(
                        if (dfOk) R.string.payload_method_auto_df else R.string.payload_method_auto_gl,
                    ),
                    selected = state.settings.rootMethod == RootMethod.AUTO,
                    onClick = { vm.updateSettings { it.copy(rootMethod = RootMethod.AUTO) } },
                )
                SettingsDivider()
                MethodRow(
                    label = "DirtyFrag",
                    description = stringResource(
                        if (dfOk) R.string.payload_method_df_ok else R.string.payload_method_df_unsupported,
                    ),
                    selected = state.settings.rootMethod == RootMethod.DIRTYFRAG,
                    onClick = { vm.updateSettings { it.copy(rootMethod = RootMethod.DIRTYFRAG) } },
                )
                SettingsDivider()
                MethodRow(
                    label = "GhostLock",
                    description = stringResource(R.string.payload_method_gl),
                    selected = state.settings.rootMethod == RootMethod.GHOSTLOCK,
                    onClick = { vm.updateSettings { it.copy(rootMethod = RootMethod.GHOSTLOCK) } },
                )
            }

            // ── Кастомный payload.so ──
            SettingsGroup(title = stringResource(R.string.payload_custom_title)) {
                state.customPayload?.let { cp ->
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Code, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    stringResource(R.string.home_custom_payload_selected),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Description, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        cp.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        stringResource(
                                            R.string.home_custom_payload_size,
                                            "%.1f".format(cp.size / 1048576.0),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = vm::clearCustomPayload) {
                                    Text(stringResource(R.string.home_custom_payload_cancel))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = { pickPayload.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_pick_payload), maxLines = 1)
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Строка выбора метода: радио + текст, выбор анимирован цветом. */
@Composable
private fun MethodRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250),
        label = "methodTint",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = tint,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
