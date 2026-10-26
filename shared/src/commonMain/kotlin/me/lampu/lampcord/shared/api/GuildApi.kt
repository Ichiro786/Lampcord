package me.lampu.lampcord.shared.api

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.CancellationException
import me.lampu.lampcord.shared.model.ApplicationCommandIndex
import me.lampu.lampcord.shared.model.AuditLog
import me.lampu.lampcord.shared.model.Ban
import me.lampu.lampcord.shared.model.Emoji
import me.lampu.lampcord.shared.model.Guild
import me.lampu.lampcord.shared.model.InteractionRequest
import me.lampu.lampcord.shared.model.Invite
import me.lampu.lampcord.shared.model.Member
import me.lampu.lampcord.shared.model.Role
import me.lampu.lampcord.shared.model.UserGuildSettings
import me.lampu.lampcord.shared.utils.Logging

class GuildApi(private val rest: RestClient) {

    suspend fun getGuild(guildId: String): Guild? {
        return try {
            val response = rest.httpClient.get("${rest.apiBase}/guilds/$guildId") {
                standardHeaders(rest)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error fetching guild: ${e.message}")
            null
        }
    }

    suspend fun getGuildPreview(guildId: String): Guild? {
        return try {
            val response = rest.httpClient.get("${rest.apiBase}/guilds/$guildId/preview") {
                standardHeaders(rest)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error fetching guild preview: ${e.message}")
            null
        }
    }

    suspend fun getGuildWidget(guildId: String): kotlinx.serialization.json.JsonObject? {
        return try {
            val response = rest.httpClient.get("${rest.apiBase}/guilds/$guildId/widget.json") {
                standardHeaders(rest)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateGuild(guildId: String, partial: Guild.Partial): Boolean {
        return try {
            val response = rest.httpClient.patch("${rest.apiBase}/guilds/$guildId") {
                standardHeaders(rest)
                contentType(ContentType.Application.Json)
                setBody(partial)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error updating guild: ${e.message}")
            false
        }
    }

    suspend fun getGuildRoles(guildId: String): List<Role> {
        return try {
            rest.httpClient.get("${rest.apiBase}/guilds/$guildId/roles") {
                standardHeaders(rest)
            }.body<List<Role>>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createRole(guildId: String): Role? {
        return try {
            rest.httpClient.post("${rest.apiBase}/guilds/$guildId/roles") {
                standardHeaders(rest)
                setBody(mapOf("name" to "new role"))
            }.body<Role>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateRole(guildId: String, roleId: String, partial: Role.Partial): Role? {
        return try {
            rest.httpClient.patch("${rest.apiBase}/guilds/$guildId/roles/$roleId") {
                standardHeaders(rest)
                setBody(partial)
            }.body<Role>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteRole(guildId: String, roleId: String): Boolean {
        return try {
            val response = rest.httpClient.delete("${rest.apiBase}/guilds/$guildId/roles/$roleId") {
                standardHeaders(rest)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getGuildBans(guildId: String): List<Ban> {
        return try {
            rest.httpClient.get("${rest.apiBase}/guilds/$guildId/bans") {
                standardHeaders(rest)
            }.body<List<Ban>>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun unbanUser(guildId: String, userId: String): Boolean {
        return try {
            val response = rest.httpClient.delete("${rest.apiBase}/guilds/$guildId/bans/$userId") {
                standardHeaders(rest)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getGuildEmojis(guildId: String): List<Emoji> {
        return try {
            rest.httpClient.get("${rest.apiBase}/guilds/$guildId/emojis") {
                standardHeaders(rest)
            }.body<List<Emoji>>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createEmoji(guildId: String, name: String, image: String): Emoji? {
        return try {
            rest.httpClient.post("${rest.apiBase}/guilds/$guildId/emojis") {
                standardHeaders(rest)
                setBody(mapOf("name" to name, "image" to image))
            }.body<Emoji>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateEmoji(guildId: String, emojiId: String, name: String): Emoji? {
        return try {
            rest.httpClient.patch("${rest.apiBase}/guilds/$guildId/emojis/$emojiId") {
                standardHeaders(rest)
                setBody(mapOf("name" to name))
            }.body<Emoji>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteEmoji(guildId: String, emojiId: String): Boolean {
        return try {
            val response = rest.httpClient.delete("${rest.apiBase}/guilds/$guildId/emojis/$emojiId") {
                standardHeaders(rest)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getGuildStickers(guildId: String): List<me.lampu.lampcord.shared.model.Sticker> {
        return try {
            rest.httpClient.get("${rest.apiBase}/guilds/$guildId/stickers") {
                standardHeaders(rest)
            }.body()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getContentTypeForFile(name: String): ContentType {
        return when {
            name.endsWith(".png", true) -> ContentType.Image.PNG
            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> ContentType.Image.JPEG
            name.endsWith(".gif", true) -> ContentType.Image.GIF
            name.endsWith(".webp", true) -> ContentType.parse("image/webp")
            name.endsWith(".mp4", true) -> ContentType.Video.MP4
            name.endsWith(".mov", true) -> ContentType.Video.QuickTime
            name.endsWith(".webm", true) -> ContentType.Video.Any
            else -> ContentType.Application.OctetStream
        }
    }

    suspend fun createSticker(guildId: String, name: String, description: String?, tags: String?, file: Pair<String, ByteArray>): me.lampu.lampcord.shared.model.Sticker? {
        return try {
            val (filename, bytes) = file
            val response = rest.httpClient.post("${rest.apiBase}/guilds/$guildId/stickers") {
                standardHeaders(rest)
                setBody(MultiPartFormDataContent(
                    formData {
                        append("name", name)
                        if (!description.isNullOrBlank()) append("description", description)
                        if (!tags.isNullOrBlank()) append("tags", tags)
                        append("file", bytes, Headers.build {
                            append(HttpHeaders.ContentType, getContentTypeForFile(filename).toString())
                            append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$filename\"")
                        })
                    }
                ))
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateSticker(guildId: String, stickerId: String, name: String? = null, description: String? = null, tags: String? = null): me.lampu.lampcord.shared.model.Sticker? {
        return try {
            val payload = buildJsonObject {
                if (name != null) put("name", name)
                if (description != null) put("description", description)
                if (tags != null) put("tags", tags)
            }
            val response = rest.httpClient.patch("${rest.apiBase}/guilds/$guildId/stickers/$stickerId") {
                standardHeaders(rest)
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteSticker(guildId: String, stickerId: String): Boolean {
        return try {
            val response = rest.httpClient.delete("${rest.apiBase}/guilds/$guildId/stickers/$stickerId") {
                standardHeaders(rest)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getGuildInvites(guildId: String): List<Invite> {
        return try {
            rest.httpClient.get("${rest.apiBase}/guilds/$guildId/invites") {
                standardHeaders(rest)
            }.body<List<Invite>>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteInvite(inviteCode: String): Boolean {
        return try {
            val response = rest.httpClient.delete("${rest.apiBase}/invites/$inviteCode") {
                standardHeaders(rest)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun resolveInvite(code: String): Invite? {
        return try {
            val response = rest.httpClient.get("${rest.apiBase}/invites/$code") {
                standardHeaders(rest)
                parameter("with_counts", true)
                parameter("with_expiration", true)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Invite", "Error resolving invite $code: ${e.message}")
            null
        }
    }

    suspend fun joinGuild(inviteCode: String): Guild? {
        return try {
            val response = rest.httpClient.post("${rest.apiBase}/invites/$inviteCode") {
                standardHeaders(rest)
            }
            if (response.status.isSuccess()) response.body<Invite>().guild else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Invite", "Error joining with code $inviteCode: ${e.message}")
            null
        }
    }

    suspend fun getGuildAuditLog(guildId: String): AuditLog? {
        return try {
            rest.httpClient.get("${rest.apiBase}/guilds/$guildId/audit-logs") {
                standardHeaders(rest)
            }.body<AuditLog>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getGuildMembers(guildId: String, limit: Int = 100, after: String? = null): List<Member> {
        return try {
            val response = rest.httpClient.get("${rest.apiBase}/guilds/$guildId/members") {
                standardHeaders(rest)
                parameter("limit", limit)
                if (after != null) {
                    parameter("after", after)
                }
            }
            if (response.status.isSuccess()) {
                response.body()
            } else {
                val errorBody = response.bodyAsText()
                Logging.e("Guild", "Error fetching members: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error fetching members: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchGuildMembers(guildId: String, query: String = "", limit: Int = 100): List<Member> {
        return try {
            val response = rest.httpClient.get("${rest.apiBase}/guilds/$guildId/members/search") {
                standardHeaders(rest)
                parameter("query", query)
                parameter("limit", limit)
            }
            if (response.status.isSuccess()) {
                response.body()
            } else {
                val errorBody = response.bodyAsText()
                Logging.e("Members", "Error searching members: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error searching members: ${e.message}")
            emptyList()
        }
    }

    /**
     * Discord's own computation of the current user's base permissions in every guild
     * (GET /users/@me/guilds). Used as a floor for channel visibility so a missing or stale
     * role in our cache can't hide channels the user can actually see.
     */
    suspend fun getMyGuildPermissions(): Map<String, String> {
        val result = HashMap<String, String>()
        var after: String? = null
        try {
            repeat(10) {
                val response = rest.httpClient.get("${rest.apiBase}/users/@me/guilds") {
                    standardHeaders(rest)
                    parameter("limit", 200)
                    after?.let { parameter("after", it) }
                }
                if (!response.status.isSuccess()) return result
                val page = rest.json.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return result
                page.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    obj["permissions"]?.jsonPrimitive?.contentOrNull?.let { result[id] = it }
                }
                if (page.size < 200) return result
                after = (page.last() as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull ?: return result
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error fetching guild permissions: ${e.message}")
        }
        return result
    }

    suspend fun getGuildMember(guildId: String, userId: String): Member? {
        return try {
            val response = rest.httpClient.get("${rest.apiBase}/guilds/$guildId/members/$userId") {
                standardHeaders(rest)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error fetching member: ${e.message}")
            null
        }
    }

    suspend fun leaveGuild(guildId: String): Boolean {
        return try {
            val response = rest.httpClient.delete("${rest.apiBase}/users/@me/guilds/$guildId") {
                standardHeaders(rest)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Auth", "Error leaving guild: ${e.message}")
            false
        }
    }

    suspend fun getCommandIndex(guildId: String): ApplicationCommandIndex? {
        return try {
            val response = rest.httpClient.get("${rest.apiBase}/guilds/$guildId/application-command-index") {
                standardHeaders(rest)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Command", "Error getting command index: ${e.message}")
            null
        }
    }

    suspend fun sendInteraction(request: InteractionRequest): Boolean {
        return try {
            val response = rest.httpClient.post("${rest.apiBase}/interactions") {
                standardHeaders(rest)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Interaction", "Error sending interaction: ${e.message}")
            false
        }
    }

    suspend fun onboardSelectedChannels(guildId: String, channelIds: List<String>): Boolean {
        return try {
            val response = rest.httpClient.put("${rest.apiBase}/guilds/$guildId/members/@me/channels") {
                standardHeaders(rest)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("channel_ids", buildJsonArray {
                        channelIds.forEach { add(it) }
                    })
                })
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error onboarding channels: ${e.message}")
            false
        }
    }

    /**
     * Discord's PATCH body takes channel_overrides as a map keyed by channel id, not the
     * array the gateway sends, and has no guild_id field. Nulls are dropped so unset
     * fields aren't reset, except mute_config, where null is meaningful when unmuting.
     */
    private fun userGuildSettingsBody(settings: UserGuildSettings.Partial): JsonObject {
        val encoded = rest.json.encodeToJsonElement(UserGuildSettings.Partial.serializer(), settings).jsonObject
        return buildJsonObject {
            encoded.forEach { (key, value) ->
                when {
                    key == "guild_id" -> Unit
                    key == "channel_overrides" -> Unit

                    value is JsonNull && key != "mute_config" -> Unit
                    else -> put(key, value)
                }
            }
            if (settings.mute_config == null && settings.muted == false && "mute_config" !in encoded) put("mute_config", JsonNull)
            // Every field is written out: the shared Json drops defaults, which used to turn
            // "muted = false" into nothing and made unmuting a channel a no-op.
            settings.channel_overrides?.let { overrides ->
                put("channel_overrides", buildJsonObject {
                    overrides.forEach { o ->
                        put(o.channel_id, buildJsonObject {
                            put("muted", o.muted)
                            put("message_notifications", o.message_notifications)
                            put("collapsed", o.collapsed)
                            put("flags", o.flags)
                            o.mute_config?.let { mc ->
                                put("mute_config", buildJsonObject {
                                    put("end_time", mc.end_time?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull)
                                    put("selected_time_window", mc.selected_time_window ?: -1)
                                })
                            } ?: put("mute_config", JsonNull)
                        })
                    }
                })
            }
        }
    }

    suspend fun updateUserGuildSettings(guildId: String, settings: UserGuildSettings.Partial): Boolean {
        return try {
            val response = rest.httpClient.patch("${rest.apiBase}/users/@me/guilds/$guildId/settings") {
                standardHeaders(rest)
                contentType(ContentType.Application.Json)
                setBody(userGuildSettingsBody(settings))
            }
            if (!response.status.isSuccess()) {
                Logging.e("Guild", "Guild settings PATCH rejected (${response.status.value}): ${runCatching { response.bodyAsText() }.getOrNull()?.take(300)}")
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error updating guild settings: ${e.message}")
            false
        }
    }

    suspend fun modifyGuildMemberRoles(guildId: String, userId: String, roles: List<String>): Boolean {
        return try {
            val response = rest.httpClient.patch("${rest.apiBase}/guilds/$guildId/members/$userId") {
                standardHeaders(rest)
                contentType(ContentType.Application.Json)
                setBody(mapOf("roles" to roles))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error updating member roles: ${e.message}")
            false
        }
    }

    suspend fun updateSelfMember(guildId: String, partial: Member.Partial): Member? {
        return try {
            val response = rest.httpClient.patch("${rest.apiBase}/guilds/$guildId/members/@me") {
                standardHeaders(rest)
                contentType(ContentType.Application.Json)
                setBody(partial)
            }
            if (response.status.isSuccess()) response.body() else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logging.e("Guild", "Error updating self member: ${e.message}")
            null
        }
    }
}
