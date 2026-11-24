package me.lampu.lampcord.shared.gateway

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

private const val MAX_PAYLOAD_BYTES = 64 * 1024 * 1024

actual val zlibStreamSupported: Boolean = true

actual class ZlibStreamDecoder actual constructor() {
    private val inflater = Inflater()
    private val pending = ByteArrayOutputStream()
    private val out = ByteArrayOutputStream(64 * 1024)
    private val chunk = ByteArray(64 * 1024)

    actual fun feed(bytes: ByteArray): String? {
        if (bytes.size > MAX_PAYLOAD_BYTES - pending.size()) {
            throw DataFormatException("Compressed gateway payload is too large")
        }
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
            if (n == 0) {
                if (inflater.needsInput() || inflater.finished()) break
                throw DataFormatException("Gateway zlib stream made no progress")
            }
            if (n > MAX_PAYLOAD_BYTES - out.size()) {
                throw DataFormatException("Expanded gateway payload is too large")
            }
            out.write(chunk, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    actual fun close() {
        inflater.end()
    }
}
