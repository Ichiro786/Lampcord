package me.lampu.lampcord.shared.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.lampu.lampcord.shared.state.SettingsStore
import me.lampu.lampcord.shared.ui.components.settings.*
import me.lampu.lampcord.shared.model.UserSettings
import me.lampu.lampcord.shared.ui.icons.Icons
import me.lampu.lampcord.shared.ui.components.ExpressiveSwitch
import me.lampu.lampcord.shared.utils.getPlatformName
import me.lampu.lampcord.shared.generated.resources.Res
import me.lampu.lampcord.shared.generated.resources.developer_mode
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_advanced
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_clear_cache
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_clear_cache_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_data_management
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_developer_mode_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_developer_settings
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_reset_app_state
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_reset_app_state_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_show_hidden_channels
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_show_hidden_channels_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_use_custom_titlebar
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_use_custom_titlebar_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_wayland_scaling
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_wayland_scaling_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_window
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun AdvancedSettings(
    onBack: () -> Unit,
    settingsStore: SettingsStore = koinInject()
) {
    SettingsSubScreen(
        title = stringResource(Res.string.lampcord_settings_advanced),
        onNavigateBack = onBack
    ) {
        AdvancedSettingsContent(settingsStore = settingsStore)
    }
}

@Composable
fun AdvancedSettingsContent(settingsStore: SettingsStore = koinInject()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val isDesktop = remember { getPlatformName() != "android" && getPlatformName() != "ios" }
        val isWindows = remember { getPlatformName() == "windows" }
        val devMode = settingsStore.userSettings?.developer_mode ?: false
        val labelUseCustomTitlebar = stringResource(Res.string.lampcord_settings_use_custom_titlebar)
        val descUseCustomTitlebar = stringResource(Res.string.lampcord_settings_use_custom_titlebar_desc)
        val labelWaylandScaling = stringResource(Res.string.lampcord_settings_wayland_scaling)
        val descWaylandScaling = stringResource(Res.string.lampcord_settings_wayland_scaling_desc)
        Material3SettingsGroup(
            title = stringResource(Res.string.lampcord_settings_developer_settings),
            items = listOf(
                switchSettingsItem(
                    title = stringResource(Res.string.developer_mode),
                    description = stringResource(Res.string.lampcord_settings_developer_mode_desc),
                    checked = devMode,
                    onCheckedChange = {
                        settingsStore.updateUserSettings(UserSettings.Partial(developer_mode = it))
                    }
                ),
                switchSettingsItem(
                    title = stringResource(Res.string.lampcord_settings_show_hidden_channels),
                    description = stringResource(Res.string.lampcord_settings_show_hidden_channels_desc),
                    checked = settingsStore.showHiddenChannels,
                    onCheckedChange = {
                        settingsStore.showHiddenChannels = it
                    }
                )
            )
        )

        if (isDesktop) {
            Material3SettingsGroup(
                title = stringResource(Res.string.lampcord_settings_window),
                items = buildList {
                    // Windows always uses the in-app title bar; see useSystemWindowFrame in the desktop Main.kt.
                    if (!isWindows) add(switchSettingsItem(
                        title = labelUseCustomTitlebar,
                        description = descUseCustomTitlebar,
                        checked = settingsStore.useCustomTitlebar,
                        onCheckedChange = { settingsStore.useCustomTitlebar = it }
                    ))
                    add(switchSettingsItem(
                        title = labelWaylandScaling,
                        description = descWaylandScaling,
                        checked = settingsStore.enableWaylandScaling,
                        onCheckedChange = {
                            settingsStore.enableWaylandScaling = it
                        }
                    ))
                }
            )
        }

        var proxyUrlText by remember { mutableStateOf(settingsStore.httpProxyUrl) }

        Material3SettingsGroup(
            title = "Network & Proxy",
            content = {
                OutlinedTextField(
                    value = proxyUrlText,
                    onValueChange = {
                        proxyUrlText = it
                        settingsStore.httpProxyUrl = it.trim()
                    },
                    label = { Text("HTTP Proxy URL") },
                    placeholder = { Text("e.g. http://127.0.0.1:8080") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true
                )
                Text(
                    text = "Warning: Routing traffic through a proxy may put your account at risk. Leave blank to use environment variables (HTTPS_PROXY / ALL_PROXY) or direct connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        )

        Material3SettingsGroup(
            title = stringResource(Res.string.lampcord_settings_data_management),
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(Res.string.lampcord_settings_clear_cache)) },
                    description = { Text(stringResource(Res.string.lampcord_settings_clear_cache_desc)) },
                    onClick = { /* TODO */ }
                ),
                Material3SettingsItem(
                    title = { Text(stringResource(Res.string.lampcord_settings_reset_app_state)) },
                    description = { Text(stringResource(Res.string.lampcord_settings_reset_app_state_desc)) },
                    onClick = { /* TODO */ }
                )
            )
        )
    }
}
