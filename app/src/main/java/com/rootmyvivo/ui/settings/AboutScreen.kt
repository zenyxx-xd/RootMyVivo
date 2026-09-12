package com.rootmyvivo.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rootmyvivo.BuildConfig
import com.rootmyvivo.R
import com.rootmyvivo.data.Prefs
import com.rootmyvivo.ui.common.SettingsDivider
import com.rootmyvivo.ui.common.SettingsGroup
import com.rootmyvivo.ui.common.SettingsRow
import com.rootmyvivo.ui.common.TrailingValue
import com.rootmyvivo.vm.MainViewModel
import com.rootmyvivo.vm.UiState

private val REPO_APP = "https://github.com/zenyxx-xd/RootMyVivo"
private val REPO_PAYLOADS = "https://github.com/zenyxx-xd/RootMyVivo-Payloads"
private val REPO_EXPLOIT = "https://github.com/zenyxx-xd/RootMyVivo-Exploit"

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.link_open_failed), Toast.LENGTH_SHORT).show()
    }
}

/**
 * «О приложении»: шапка с иконкой и версией, обновления, репозитории,
 * дисклеймер. Семь тапов по версии — разблокировка меню разработчика.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(vm: MainViewModel, state: UiState, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var versionTaps by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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

            // ── Шапка: иконка, название, версия, автор ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(
                    Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    // тап по версии: easter egg на 7 тапов — меню разработчика
                    Text(
                        "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickableNoIndicator {
                                versionTaps++
                                if (versionTaps >= 7 && !prefs.devUnlocked) {
                                    prefs.devUnlocked = true
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.settings_dev_unlocked),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                    )
                    Text(
                        stringResource(R.string.about_author),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            // ── Обновления ──
            SettingsGroup(title = stringResource(R.string.settings_updates)) {
                SettingsRow(
                    title = stringResource(R.string.settings_autoupdate),
                    description = stringResource(R.string.settings_autoupdate_desc),
                    icon = Icons.Rounded.CloudSync,
                    trailing = {
                        Switch(
                            checked = state.settings.autoUpdateCheck,
                            onCheckedChange = { v -> vm.updateSettings { it.copy(autoUpdateCheck = v) } },
                        )
                    },
                )
                SettingsDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_check_update),
                    description = state.appUpdate?.let {
                        stringResource(R.string.update_version, it.versionName)
                    },
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = { vm.checkForUpdate(manual = true) },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            // ── Репозитории ──
            SettingsGroup(title = stringResource(R.string.about_repositories)) {
                RepoRow(
                    title = stringResource(R.string.about_repo_app),
                    url = REPO_APP,
                    icon = Icons.Rounded.Code,
                )
                SettingsDivider()
                RepoRow(
                    title = stringResource(R.string.about_repo_payloads),
                    url = REPO_PAYLOADS,
                    icon = Icons.Rounded.CloudDownload,
                )
                SettingsDivider()
                RepoRow(
                    title = stringResource(R.string.about_repo_exploit),
                    url = REPO_EXPLOIT,
                    icon = Icons.Rounded.Bolt,
                )
            }

            // ── Дисклеймер ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Column {
                        Text(
                            stringResource(R.string.disclaimer_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.disclaimer_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RepoRow(title: String, url: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val context = LocalContext.current
    SettingsRow(
        title = title,
        description = url.removePrefix("https://"),
        icon = icon,
        onClick = { openUrl(context, url) },
        trailing = {
            Icon(
                Icons.Rounded.OpenInNew, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

/** Кликабельность без визуальной ряби — для easter egg по версии. */
private fun Modifier.clickableNoIndicator(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
    indication = null,
    onClick = onClick,
)
