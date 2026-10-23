package me.lampu.lampcord.shared.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults

actual object LocalAppLocale {
    private const val LANG_KEY = "AppleLanguages"

    private val default: String = (
        NSUserDefaults.standardUserDefaults.stringArrayForKey(LANG_KEY)?.firstOrNull() as? String
    ) ?: "en-US"

    private val local = staticCompositionLocalOf { default }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val newLocale = value ?: default
        if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LANG_KEY)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(listOf(newLocale), LANG_KEY)
        }
        return local.provides(newLocale)
    }
}
