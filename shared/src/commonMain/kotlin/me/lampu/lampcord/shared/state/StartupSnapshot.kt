package me.lampu.lampcord.shared.state

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.lampu.lampcord.shared.utils.Logging
import me.lampu.lampcord.shared.utils.deleteInternalFile
import me.lampu.lampcord.shared.utils.readInternalFile
import me.lampu.lampcord.shared.utils.writeInternalFile

/**
 * The last READY, slimmed, so the next launch can draw servers, channels, DMs and unreads
 * straight away while the gateway connects. The live READY replaces it as soon as it lands.
 * Holds no token: the session, auth and analytics fields are dropped before it is written,
 * and the file is excluded from backups. Only the last account's snapshot is kept.
 */
object StartupSnapshot {
    private const val FILE = "startup-snapshot.json"
    private const val FORMAT = 1

    private val DROPPED_KEYS = setOf(
        "auth_token", "session_id", "resume_gateway_url", "auth_session_id_hash",
        "analytics_token", "sessions", "experiments", "guild_experiments", "merged_presences",
        "presences", "connected_accounts", "consents", "tutorial", "geo_ordered_rtc_regions",
        "api_code_version", "static_client_session_id", "notification_settings", "_trace",
    )

    private fun keyFor(token: String): String = token.trim().hashCode().toUInt().toString(16)

    fun save(json: Json, token: String, ready: JsonElement) {
        if (token.isBlank()) return
        runCatching {
            val slim = JsonObject(ready.jsonObject.filterKeys { it !in DROPPED_KEYS })
            val wrapped = buildJsonObject {
                put("format", JsonPrimitive(FORMAT))
                put("key", JsonPrimitive(keyFor(token)))
                put("d", slim)
            }
            writeInternalFile(FILE, json.encodeToString(JsonElement.serializer(), wrapped))
        }.onFailure { Logging.w("StartupSnapshot", "Could not save snapshot", it) }
    }

    fun load(json: Json, token: String): JsonElement? {
        if (token.isBlank()) return null
        return runCatching {
            val text = readInternalFile(FILE) ?: return null
            val root = json.parseToJsonElement(text).jsonObject
            if (root["format"]?.jsonPrimitive?.contentOrNull != FORMAT.toString()) return null
            if (root["key"]?.jsonPrimitive?.contentOrNull != keyFor(token)) return null
            root["d"]
        }.onFailure { Logging.w("StartupSnapshot", "Could not read snapshot", it) }.getOrNull()
    }

    fun clear() {
        runCatching { deleteInternalFile(FILE) }
    }
}
