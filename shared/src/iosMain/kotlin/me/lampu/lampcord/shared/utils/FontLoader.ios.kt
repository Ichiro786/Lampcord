package me.lampu.lampcord.shared.utils

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle

actual fun loadFont(
    path: String,
    weight: FontWeight,
    style: FontStyle
): Font {

    val bytes = if (path.startsWith("/")) {
        readBytes(path)
    } else {
        ResourceLoader.readBytes(path) ?: ResourceLoader.readBytes("compose-resources/$path")
    } ?: error("Font is missing: $path")
    return androidx.compose.ui.text.platform.Font(path, bytes, weight, style)
}
