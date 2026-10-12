package me.lampu.lampcord.shared.utils

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle

actual fun loadFont(
    path: String,
    weight: FontWeight,
    style: FontStyle
): Font {
    // Compose Multiplatform syncs source-set resources into the app bundle under
    // "compose-resources", while ResourceLoader reads from the bundle root; check both.
    val bytes = ResourceLoader.readBytes(path)
        ?: ResourceLoader.readBytes("compose-resources/$path")
        ?: error("Font resource is missing: $path")
    return androidx.compose.ui.text.platform.Font(path, bytes, weight, style)
}
