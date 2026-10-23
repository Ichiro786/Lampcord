package me.lampu.lampcord.shared.i18n

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Value used by settings to mean "follow the device language". */
const val LANGUAGE_SYSTEM = "system"

/** A language offered in the language picker. */
data class AppLanguage(
    /** BCP-47 tag, matching the qualifier of the Compose Resources directory. */
    val languageTag: String,
    val englishName: String,
    val nativeName: String,
)

/**
 * The 30 locales Discord exposes in its language picker. Kept in the same order
 * as Discord's `WidgetSettingsLanguage`.
 */
val SUPPORTED_LANGUAGES: List<AppLanguage> = listOf(
    AppLanguage("en-US", "English, USA", "English, USA"),
    AppLanguage("en-GB", "English, UK", "English, UK"),
    AppLanguage("bg", "Bulgarian", "български"),
    AppLanguage("cs", "Czech", "Čeština"),
    AppLanguage("da", "Danish", "Dansk"),
    AppLanguage("de", "German", "Deutsch"),
    AppLanguage("el", "Greek", "Ελληνικά"),
    AppLanguage("es-ES", "Spanish", "Español"),
    AppLanguage("fi", "Finnish", "Suomi"),
    AppLanguage("fr", "French", "Français"),
    AppLanguage("hi", "Hindi", "हिंदी"),
    AppLanguage("hr", "Croatian", "Hrvatski"),
    AppLanguage("hu", "Hungarian", "Magyar"),
    AppLanguage("it", "Italian", "Italiano"),
    AppLanguage("ja", "Japanese", "日本語"),
    AppLanguage("ko", "Korean", "한국어"),
    AppLanguage("lt", "Lithuanian", "Lietuviškai"),
    AppLanguage("nl", "Dutch", "Nederlands"),
    AppLanguage("no", "Norwegian", "Norsk"),
    AppLanguage("pl", "Polish", "Polski"),
    AppLanguage("pt-BR", "Portuguese, Brazilian", "Português do Brasil"),
    AppLanguage("ro", "Romanian", "Română"),
    AppLanguage("ru", "Russian", "Русский"),
    AppLanguage("sv-SE", "Swedish", "Svenska"),
    AppLanguage("th", "Thai", "ไทย"),
    AppLanguage("tr", "Turkish", "Türkçe"),
    AppLanguage("uk", "Ukrainian", "Українська"),
    AppLanguage("vi", "Vietnamese", "Tiếng Việt"),
    AppLanguage("zh-CN", "Chinese, Simplified", "中文"),
    AppLanguage("zh-TW", "Chinese, Traditional", "繁體中文"),
)

/** Finds a supported language by tag, or null for the system/unknown. */
fun findLanguage(languageTag: String?): AppLanguage? =
    languageTag?.let { tag -> SUPPORTED_LANGUAGES.firstOrNull { it.languageTag == tag } }

/**
 * Name shown for the currently selected language in the settings list.
 * [systemName] is the localized label for "System".
 */
fun languageDisplayName(languageTag: String, systemName: String): String {
    if (languageTag == LANGUAGE_SYSTEM) return systemName
    return findLanguage(languageTag)?.nativeName ?: "English, USA"
}

/**
 * Replaces Discord's ICU-style named placeholders (`{name}`) in a raw string.
 *
 * Discord keeps `{...}` tokens in its translations and substitutes them in
 * code. Compose Resources can only substitute positional `%1$s` arguments, so
 * named placeholders are resolved here instead.
 */
fun String.formatNamed(vararg args: Pair<String, Any?>): String {
    if (args.isEmpty()) return this
    var result = this
    for ((key, value) in args) {
        result = result.replace("{$key}", value?.toString() ?: "")
    }
    return result
}

fun String.formatNamed(args: Map<String, Any?>): String {
    if (args.isEmpty()) return this
    var result = this
    for ((key, value) in args) {
        result = result.replace("{$key}", value?.toString() ?: "")
    }
    return result
}

/**
 * Loads [resource] and substitutes Discord-style named placeholders.
 * Equivalent to `stringResource(resource)` when no args are supplied.
 */
@Composable
fun localized(resource: StringResource, vararg args: Pair<String, Any?>): String =
    stringResource(resource).formatNamed(*args)

/**
 * Loads a plural [resource] for [quantity] and substitutes Discord's named
 * placeholders. `{#}` is the conventional placeholder for the quantity.
 */
@Composable
fun localizedPlural(
    resource: PluralStringResource,
    quantity: Int,
    vararg args: Pair<String, Any?>,
): String {
    var text = pluralStringResource(resource, quantity).replace("{#}", quantity.toString())
    text = text.formatNamed(*args)
    return text
}
