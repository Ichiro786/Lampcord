package me.lampu.lampcord.shared.utils

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import me.lampu.lampcord.shared.settings.FontOption

expect fun loadFont(
    path: String,
    weight: FontWeight = FontWeight.Normal,
    style: FontStyle = FontStyle.Normal
): Font

fun appFontFamily(option: FontOption, customFontPath: String): FontFamily = when (option) {
    FontOption.SYSTEM -> FontFamily.Default
    FontOption.INTER -> bundledFont("Inter", "otf")
    FontOption.MAPLE_MONO -> bundledFont("MapleMono", "ttf")
    FontOption.CUSTOM -> if (customFontPath.isNotEmpty()) {
        runCatching { FontFamily(loadFont(customFontPath)) }.getOrDefault(FontFamily.Default)
    } else {
        FontFamily.Default
    }
}

/**
 * Copies a picked font into app storage and returns the path the loader can read.
 *
 * FilePicker only reports the file's display name, never its real path, so persisting that name
 * left [loadFont] looking for a file that does not exist. Storing the bytes and returning the
 * absolute path works on every platform, including when the picked document's URI permission is
 * lost after the picker closes.
 */
fun importCustomFont(displayName: String, bytes: ByteArray): String {
    val extension = displayName.substringAfterLast('.', "").lowercase()
        .takeIf { it in CUSTOM_FONT_EXTENSIONS } ?: "ttf"
    val fileName = "custom_font.$extension"
    // Remove any previously imported font so switching extensions does not leave orphan files.
    CUSTOM_FONT_EXTENSIONS.forEach { deleteInternalFile("custom_font.$it") }
    writeInternalBytes(fileName, bytes)
    return getInternalFilePath(fileName)
}

private val CUSTOM_FONT_EXTENSIONS = setOf("ttf", "otf", "ttc", "woff", "woff2")

private fun bundledFont(prefix: String, extension: String): FontFamily {
    val fonts = listOf(
        "Regular" to FontWeight.Normal,
        "Medium" to FontWeight.Medium,
        "SemiBold" to FontWeight.SemiBold,
        "Bold" to FontWeight.Bold
    ).mapNotNull { (name, weight) ->
        runCatching { loadFont("font/$prefix-$name.$extension", weight) }.getOrNull()
    }
    return if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
}
