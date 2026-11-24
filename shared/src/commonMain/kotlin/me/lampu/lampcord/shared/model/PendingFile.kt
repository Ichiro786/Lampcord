package me.lampu.lampcord.shared.model

data class PendingFile(
    val name: String,
    val data: ByteArray,
    val uri: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as PendingFile

        if (name != other.name) return false
        // Same buffer, not same content: hashing a multi-MB attachment on every Compose list
        // comparison was pure overhead, and two picks of one file are still two attachments.
        if (data !== other.data) return false
        if (uri != other.uri) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + data.size
        result = 31 * result + (uri?.hashCode() ?: 0)
        return result
    }
}
