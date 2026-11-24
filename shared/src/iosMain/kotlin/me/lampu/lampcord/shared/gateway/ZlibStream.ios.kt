package me.lampu.lampcord.shared.gateway

actual val zlibStreamSupported: Boolean = false

actual class ZlibStreamDecoder actual constructor() {
    actual fun feed(bytes: ByteArray): String? = null
    actual fun close() {}
}
