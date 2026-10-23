package me.lampu.lampcord.shared.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.lampu.lampcord.shared.state.SettingsStore
import me.lampu.lampcord.shared.ui.components.settings.*
import me.lampu.lampcord.shared.model.UserSettings
import me.lampu.lampcord.shared.settings.Settings
import androidx.compose.ui.text.font.FontWeight
import me.lampu.lampcord.shared.generated.resources.Res
import me.lampu.lampcord.shared.generated.resources.accessibility
import me.lampu.lampcord.shared.generated.resources.accessibility_prefers_reduced_motion
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_allow_accessibility_detection
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_allow_accessibility_detection_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_detection
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_motion
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_reduced_motion_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_saturation
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_visual
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun AccessibilitySettings(
    onBack: () -> Unit,
    settingsStore: SettingsStore = koinInject()
) {
    SettingsSubScreen(
        title = stringResource(Res.string.accessibility),
        onNavigateBack = onBack
    ) {
        AccessibilitySettingsContent(settingsStore = settingsStore)
    }
}

@Composable
fun AccessibilitySettingsContent(settingsStore: SettingsStore = koinInject()) {
    val userSettings = settingsStore.userSettings
    Column(modifier = Modifier.fillMaxWidth()) {
        Material3SettingsGroup(
            title = stringResource(Res.string.lampcord_settings_visual),
            items = listOf(
                Material3SettingsItem(
                    title = { Text(stringResource(Res.string.lampcord_settings_saturation)) },
                    description = {
                        var saturation by remember { mutableStateOf(1f) }
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text("${(saturation * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Slider(
                                value = saturation,
                                onValueChange = { saturation = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                )
            )
        )

        Material3SettingsGroup(
            title = stringResource(Res.string.lampcord_settings_motion),
            items = listOf(
                switchSettingsItem(
                    title = stringResource(Res.string.accessibility_prefers_reduced_motion),
                    description = stringResource(Res.string.lampcord_settings_reduced_motion_desc),
                    checked = Settings.shared.reduceMotion,
                    onCheckedChange = { Settings.shared.reduceMotion = it }
                )
            )
        )

        Material3SettingsGroup(
            title = stringResource(Res.string.lampcord_settings_detection),
            items = listOf(
                switchSettingsItem(
                    title = stringResource(Res.string.lampcord_settings_allow_accessibility_detection),
                    description = stringResource(Res.string.lampcord_settings_allow_accessibility_detection_desc),
                    checked = userSettings?.allow_accessibility_detection ?: false,
                    onCheckedChange = { 
                        settingsStore.updateUserSettings(UserSettings.Partial(allow_accessibility_detection = it))
                    }
                )
            )
        )
    }
}
