package me.lampu.lampcord.shared.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalEncodingApi::class)
class FavoriteGifProtoTest {

    @Test
    fun `favorites are newest first`() {
        val older = favorite("https://example.invalid/older.gif", "older-src", order = 1)
        val newer = favorite("https://example.invalid/newer.gif", "newer-src", order = 2)
        val proto = bytesField(2, older + newer)

        val decoded = decodeFavoriteGifs(Base64.Default.encode(proto))

        assertEquals(listOf("https://example.invalid/newer.gif", "https://example.invalid/older.gif"), decoded.map { it.url })
    }

    private fun favorite(url: String, src: String, order: Int): ByteArray {
        val gif = stringField(2, src) +
            varintField(3, 100) +
            varintField(4, 100) +
            varintField(5, order) +
            varintField(1, 1)
        val entry = stringField(1, url) + bytesField(2, gif)
        return bytesField(1, entry)
    }

    private fun bytesField(field: Int, bytes: ByteArray): ByteArray =
        tag(field, 2) + varint(bytes.size) + bytes

    private fun stringField(field: Int, value: String): ByteArray =
        bytesField(field, value.encodeToByteArray())

    private fun varintField(field: Int, value: Int): ByteArray =
        tag(field, 0) + varint(value)

    private fun tag(field: Int, wireType: Int): ByteArray =
        varint((field shl 3) or wireType)

    private fun varint(value: Int): ByteArray {
        var remaining = value
        val out = mutableListOf<Byte>()
        while (remaining and 0x7f.inv() != 0) {
            out += ((remaining and 0x7f) or 0x80).toByte()
            remaining = remaining ushr 7
        }
        out += remaining.toByte()
        return out.toByteArray()
    }
}
