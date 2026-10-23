package me.lampu.lampcord.shared.i18n

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

actual object LocalAppLocale {
    private var default: Locale? = null

    actual val current: String
        @Composable get() = Locale.getDefault().toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        if (default == null) {
            default = Locale.getDefault()
        }
        val newLocale = when (value) {
            null -> default!!
            else -> Locale.forLanguageTag(value)
        }
        // On API 24+ androidx.compose.ui.text.intl.Locale.current resolves from
        // android.os.LocaleList.getDefault(), which Locale.setDefault updates.
        Locale.setDefault(newLocale)
        val updated = Configuration(configuration).apply { setLocale(newLocale) }
        return LocalConfiguration.provides(updated)
    }
}
