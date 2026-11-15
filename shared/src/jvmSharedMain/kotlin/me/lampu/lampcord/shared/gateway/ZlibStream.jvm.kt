package me.lampu.lampcord.shared.gateway

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

actual val zlibStreamSupported: Boolean = true

actual class ZlibStreamDecoder actual constructor() {
    private val inflater = Inflater()
    private val pending = ByteArrayOutputStream()
    private val out = ByteArrayOutputStream(64 * 1024)
    private val chunk = ByteArray(64 * 1024)

    actual fun feed(bytes: ByteArray): String? {
        pending.write(bytes)
        val size = pending.size()
        if (size < 4) return null
        val buf = pending.toByteArray()
        if (buf[size - 4] != 0.toByte() || buf[size - 3] != 0.toByte() ||
            buf[size - 2] != 0xFF.toByte() || buf[size - 1] != 0xFF.toByte()
        ) return null
        pending.reset()
        inflater.setInput(buf, 0, size)
        out.reset()
        while (true) {
            val n = inflater.inflate(chunk)
            if (n > 0) out.write(chunk, 0, n)
            if (n == 0 && (inflater.needsInput() || inflater.finished())) break
        }
        return out.toString(Charsets.UTF_8.name())
    }

    actual fun close() {
        inflater.end()
    }
}
