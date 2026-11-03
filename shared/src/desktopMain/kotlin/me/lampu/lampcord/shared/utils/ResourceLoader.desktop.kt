package me.lampu.lampcord.shared.utils

actual object ResourceLoader {
    actual fun readText(path: String): String? {
        return readBytes(path)?.decodeToString()
    }

    actual fun readBytes(path: String): ByteArray? {
        val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: ResourceLoader::class.java.getResourceAsStream("/$path")
            ?: ResourceLoader::class.java.getResourceAsStream(path)
            ?: return null

        return try {
            stream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }
}
