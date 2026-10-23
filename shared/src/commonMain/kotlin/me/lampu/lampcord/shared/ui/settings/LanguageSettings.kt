package me.lampu.lampcord.shared.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.lampu.lampcord.shared.generated.resources.Res
import me.lampu.lampcord.shared.generated.resources.language
import me.lampu.lampcord.shared.generated.resources.language_select
import me.lampu.lampcord.shared.generated.resources.lampcord_language_system
import me.lampu.lampcord.shared.generated.resources.lampcord_language_system_description
import me.lampu.lampcord.shared.i18n.LANGUAGE_SYSTEM
import me.lampu.lampcord.shared.i18n.SUPPORTED_LANGUAGES
import me.lampu.lampcord.shared.state.SettingsStore
import me.lampu.lampcord.shared.ui.components.settings.Material3SettingsGroup
import me.lampu.lampcord.shared.ui.components.settings.Material3SettingsItem
import me.lampu.lampcord.shared.ui.components.settings.SettingsSubScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun LanguageSettings(
    onBack: () -> Unit,
    settingsStore: SettingsStore = koinInject(),
) {
    SettingsSubScreen(
        title = stringResource(Res.string.language),
        onNavigateBack = onBack,
    ) {
        LanguageSettingsContent(settingsStore = settingsStore)
    }
}

@Composable
fun LanguageSettingsContent(settingsStore: SettingsStore = koinInject()) {
    val selected = settingsStore.language

    Column(modifier = Modifier.fillMaxWidth()) {
        Material3SettingsGroup(
            title = stringResource(Res.string.language_select),
            items = buildList {
                add(
                    languageItem(
                        title = stringResource(Res.string.lampcord_language_system),
                        description = stringResource(Res.string.lampcord_language_system_description),
                        selected = selected == LANGUAGE_SYSTEM,
                        onSelect = { settingsStore.language = LANGUAGE_SYSTEM },
                    )
                )
                SUPPORTED_LANGUAGES.forEach { language ->
                    add(
                        languageItem(
                            title = language.nativeName,
                            description = language.englishName,
                            selected = selected == language.languageTag,
                            onSelect = { settingsStore.language = language.languageTag },
                        )
                    )
                }
            }
        )
    }
}

private fun languageItem(
    title: String,
    description: String?,
    selected: Boolean,
    onSelect: () -> Unit,
): Material3SettingsItem = Material3SettingsItem(
    title = { Text(title) },
    description = description?.let { { Text(it) } },
    trailingContent = {
        RadioButton(selected = selected, onClick = null)
    },
    onClick = onSelect,
)
