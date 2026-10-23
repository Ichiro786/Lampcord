package me.lampu.lampcord.shared.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.lampu.lampcord.shared.state.SettingsStore
import me.lampu.lampcord.shared.ui.components.settings.*
import me.lampu.lampcord.shared.generated.resources.Res
import me.lampu.lampcord.shared.generated.resources.form_label_mobile_notifications_inapp_label
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_enable_notifications
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_enable_notifications_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_incoming_call_sound
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_incoming_call_sound_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_message_sound
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_message_sound_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_show_in_app_notifications
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_show_in_app_notifications_desc
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_show_message_preview
import me.lampu.lampcord.shared.generated.resources.lampcord_settings_show_message_preview_desc
import me.lampu.lampcord.shared.generated.resources.notifications
import me.lampu.lampcord.shared.generated.resources.sounds
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun NotificationsSettings(
    onBack: () -> Unit,
    settingsStore: SettingsStore = koinInject()
) {
    SettingsSubScreen(
        title = stringResource(Res.string.notifications),
        onNavigateBack = onBack
    ) {
        NotificationsSettingsContent(settingsStore = settingsStore)
    }
}

@Composable
fun NotificationsSettingsContent(settingsStore: SettingsStore = koinInject()) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Material3SettingsGroup(
            title = stringResource(Res.string.notifications),
            items = listOf(
                switchSettingsItem(
                    title = stringResource(Res.string.lampcord_settings_enable_notifications),
                    description = stringResource(Res.string.lampcord_settings_enable_notifications_desc),
                    checked = settingsStore.notificationsEnabled,
                    onCheckedChange = { settingsStore.notificationsEnabled = it }
                ),
                switchSettingsItem(
                    title = stringResource(Res.string.lampcord_settings_show_message_preview),
                    description = stringResource(Res.string.lampcord_settings_show_message_preview_desc),
                    checked = settingsStore.showMessagePreview,
                    onCheckedChange = { settingsStore.showMessagePreview = it }
                )
            )
        )

        Material3SettingsGroup(
            title = stringResource(Res.string.sounds),
            items = listOf(
                switchSettingsItem(
                    title = stringResource(Res.string.lampcord_settings_message_sound),
                    description = stringResource(Res.string.lampcord_settings_message_sound_desc),
                    checked = settingsStore.notificationSound,
                    onCheckedChange = { settingsStore.notificationSound = it }
                ),
                switchSettingsItem(
                    title = stringResource(Res.string.lampcord_settings_incoming_call_sound),
                    description = stringResource(Res.string.lampcord_settings_incoming_call_sound_desc),
                    checked = settingsStore.incomingCallSound,
                    onCheckedChange = { settingsStore.incomingCallSound = it }
                )
            )
        )

        Material3SettingsGroup(
            title = stringResource(Res.string.form_label_mobile_notifications_inapp_label),
            items = listOf(
                switchSettingsItem(
                    title = stringResource(Res.string.lampcord_settings_show_in_app_notifications),
                    description = stringResource(Res.string.lampcord_settings_show_in_app_notifications_desc),
                    checked = settingsStore.showInAppNotifications,
                    onCheckedChange = { settingsStore.showInAppNotifications = it }
                )
            )
        )
    }
}
