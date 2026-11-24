package me.lampu.lampcord.shared.gateway

/**
 * Decoder for Discord's `compress=zlib-stream` transport: one zlib context spans the whole
 * connection, and a message is complete when its bytes end with the Z_SYNC_FLUSH suffix
 * 00 00 FF FF. Shrinks READY several times over, which is most of the cold-start wait.
 */
expect class ZlibStreamDecoder() {
    /** Feeds one binary frame; returns the decoded JSON text once a full message has arrived. */
    fun feed(bytes: ByteArray): String?
    fun close()
}

/** False where no inflater is wired up; the gateway then connects uncompressed. */
expect val zlibStreamSupported: Boolean
