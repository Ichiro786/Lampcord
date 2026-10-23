package me.lampu.lampcord.shared.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null

    private val local = staticCompositionLocalOf { Locale.getDefault().toString() }

    actual val current: String
        @Composable get() = local.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (default == null) {
            default = Locale.getDefault()
        }
        val newLocale = when (value) {
            null -> default!!
            else -> Locale.forLanguageTag(value)
        }
        // androidx.compose.ui.text.intl.Locale.current on desktop reads
        // java.util.Locale.getDefault() directly.
        Locale.setDefault(newLocale)
        return local.provides(newLocale.toString())
    }
}
