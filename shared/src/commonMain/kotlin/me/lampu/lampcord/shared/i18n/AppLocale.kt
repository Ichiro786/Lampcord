package me.lampu.lampcord.shared.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key

/**
 * Holds the BCP-47 language tag that Compose Resources should resolve against,
 * or `null` to follow the system locale.
 *
 * This mirrors the pattern from the Compose Multiplatform documentation
 * ("Manage local resource environment"). Each platform updates the platform
 * locale and re-provides it so [org.jetbrains.compose.resources.stringResource]
 * picks up the change.
 */
expect object LocalAppLocale {
    val current: String @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/**
 * Resolves all Compose Resources inside [content] against [languageTag].
 *
 * Pass `null` to follow the system locale. Re-keying the subtree is required so
 * that string resources are re-resolved when the language changes.
 */
@Composable
fun ProvideAppLocale(languageTag: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides languageTag) {
        key(languageTag) {
            content()
        }
    }
}
