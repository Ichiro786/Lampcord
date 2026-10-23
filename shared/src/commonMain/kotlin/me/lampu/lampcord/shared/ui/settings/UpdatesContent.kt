package me.lampu.lampcord.shared.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.lampu.lampcord.shared.state.SettingsStore
import me.lampu.lampcord.shared.ui.components.settings.Material3SettingsGroup
import me.lampu.lampcord.shared.ui.components.settings.switchSettingsItem
import me.lampu.lampcord.shared.update.APP_VERSION
import me.lampu.lampcord.shared.update.InstallResult
import me.lampu.lampcord.shared.update.NIGHTLY_BUILD
import me.lampu.lampcord.shared.update.UpdateChannel
import me.lampu.lampcord.shared.update.UpdateManager
import me.lampu.lampcord.shared.update.UpdateState
import me.lampu.lampcord.shared.update.installUpdate
import me.lampu.lampcord.shared.generated.resources.Res
import me.lampu.lampcord.shared.generated.resources.dismiss
import me.lampu.lampcord.shared.generated.resources.download
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_available_fmt
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_channel_nightly
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_channel_stable
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_check_for_updates
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_check_for_updates_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_check_now
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_checking
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_downloading_fmt
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_install_restart
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_nightly_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_ready_fmt
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_up_to_date
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_update_notifications
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_update_notifications_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_version_fmt
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_view_changelog
import me.lampu.lampcord.shared.generated.resources.try_again
import me.lampu.lampcord.shared.generated.resources.whats_new
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun UpdatesContent(
    updateManager: UpdateManager = koinInject(),
    settingsStore: SettingsStore = koinInject(),
    modifier: Modifier = Modifier,
) {
    val state by updateManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    // A build ships as exactly one application id, so it only ever updates from its own
    // channel; there is no in-place channel switch to offer.
    val channel = if (NIGHTLY_BUILD > 0) UpdateChannel.NIGHTLY else UpdateChannel.STABLE
    var showChangelog by remember { mutableStateOf(false) }
    var installNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(channel) {
        if (settingsStore.checkForUpdates) updateManager.check(channel)
    }

    Column(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.lampcord_settings_version_fmt, APP_VERSION), style = MaterialTheme.typography.bodyMedium)

        Material3SettingsGroup(
            items = listOf(
                switchSettingsItem(
                    title = stringResource(Res.string.lampcord_settings_check_for_updates),
                    description = stringResource(Res.string.lampcord_settings_check_for_updates_desc),
                    checked = settingsStore.checkForUpdates,
                    onCheckedChange = {
                        settingsStore.checkForUpdates = it
                        if (!it) updateManager.reset()
                    }
                ),
                switchSettingsItem(
                    title = stringResource(Res.string.lampcord_settings_update_notifications),
                    description = stringResource(Res.string.lampcord_settings_update_notifications_desc),
                    checked = settingsStore.updateNotifications,
                    onCheckedChange = { settingsStore.updateNotifications = it }
                )
            )
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { scope.launch { updateManager.check(channel, force = true) } },
            ) { Text(stringResource(Res.string.lampcord_settings_check_now)) }
            OutlinedButton(
                onClick = { showChangelog = true },
                enabled = (state as? UpdateState.Available) != null,
            ) { Text(stringResource(Res.string.lampcord_settings_view_changelog)) }
        }

        Text(
            if (channel == UpdateChannel.NIGHTLY) stringResource(Res.string.lampcord_settings_channel_nightly) else stringResource(Res.string.lampcord_settings_channel_stable),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (channel == UpdateChannel.NIGHTLY) {
            Text(
                stringResource(Res.string.lampcord_settings_nightly_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (val current = state) {
            is UpdateState.Checking -> StatusRow(stringResource(Res.string.lampcord_settings_checking), busy = true)
            is UpdateState.UpToDate -> StatusRow(stringResource(Res.string.lampcord_settings_up_to_date))
            is UpdateState.Idle -> Button(
                onClick = { scope.launch { updateManager.check(channel, force = true) } },
            ) { Text(stringResource(Res.string.lampcord_settings_check_now)) }

            is UpdateState.Available -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(Res.string.lampcord_settings_available_fmt, current.version),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showChangelog = true }) { Text(stringResource(Res.string.whats_new)) }
                    Button(onClick = { scope.launch { updateManager.download() } }) {
                        Text(stringResource(Res.string.download))
                    }
                }
                TextButton(onClick = { updateManager.reset() }) { Text(stringResource(Res.string.dismiss)) }
            }

            is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.lampcord_settings_downloading_fmt, current.version))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            is UpdateState.ReadyToInstall -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.lampcord_settings_ready_fmt, current.version))
                installNote?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        val result = installUpdate(
                            target = me.lampu.lampcord.shared.update.currentUpdateTarget()
                                ?: return@Button,
                            downloadedFile = current.file,
                            version = current.version,
                        )
                        installNote = when (result) {
                            is InstallResult.Unsupported -> result.reason
                            is InstallResult.Failed -> result.reason
                            InstallResult.Started -> null
                        }
                    }
                ) { Text(stringResource(Res.string.lampcord_settings_install_restart)) }
            }

            is UpdateState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    current.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(
                    onClick = { scope.launch { updateManager.check(channel, force = true) } },
                ) { Text(stringResource(Res.string.try_again)) }
            }
        }
    }

    if (showChangelog) {
        val available = state as? UpdateState.Available
        ChangelogSheet(
            version = available?.release?.version.orEmpty(),
            notes = available?.changelog.orEmpty(),
            onDismiss = { showChangelog = false },
        )
    }
}

@Composable
private fun StatusRow(text: String, busy: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) CircularProgressIndicator(modifier = Modifier.heightIn(max = 18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
