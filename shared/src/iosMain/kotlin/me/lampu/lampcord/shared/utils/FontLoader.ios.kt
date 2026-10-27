package me.lampu.lampcord.shared.utils

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle

actual fun loadFont(
    path: String,
    weight: FontWeight,
    style: FontStyle
): Font {
    // Custom fonts (imported picker files and downloaded theme fonts) are written to the app's
    // storage directory and addressed by absolute path. Bundled fonts are Compose resources, which
    // live in the bundle root or under the "compose-resources" directory.
    val bytes = if (path.startsWith("/")) {
        readBytes(path)
    } else {
        ResourceLoader.readBytes(path) ?: ResourceLoader.readBytes("compose-resources/$path")
    } ?: error("Font is missing: $path")
    return androidx.compose.ui.text.platform.Font(path, bytes, weight, style)
}
