package me.lampu.lampcord.shared.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import me.lampu.lampcord.shared.model.*
import me.lampu.lampcord.shared.utils.Logging

class UserStore {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _users = MutableStateFlow<Map<String, User>>(emptyMap())
    val users: StateFlow<Map<String, User>> = _users.asStateFlow()

    private val _members = MutableStateFlow<Map<String, Map<String, Member>>>(emptyMap())
    val members: StateFlow<Map<String, Map<String, Member>>> = _members.asStateFlow()

    fun setCurrentUser(user: User?, raw: JsonObject? = null) {
        Logging.i("UserStore", "Setting current user: ${user?.username} (${user?.id})")
        _currentUser.value = user
        if (user != null) handleUserUpdate(user, raw)
    }

    fun handleUserUpdate(user: User, raw: JsonObject? = null) {
        handleUserUpdates(listOf(user to raw))
    }

    fun handleUserUpdates(updates: List<Pair<User, JsonObject?>>) {
        if (updates.isEmpty()) return
        _users.update { current ->
            val next = current.toMutableMap()
            for ((user, raw) in updates) next[user.id] = mergeUser(next[user.id], user, raw)
            next
        }
        val currentId = _currentUser.value?.id ?: return
        if (updates.any { it.first.id == currentId }) {
            _users.value[currentId]?.let { _currentUser.value = it }
        }
    }

    private fun mergeUser(existing: User?, user: User, raw: JsonObject?): User {
        if (existing == null) return user
        return existing.copy(
            username = user.username ?: existing.username,
            global_name = raw.pick("global_name", user.global_name, existing.global_name),
            avatar = raw.pick("avatar", user.avatar, existing.avatar),
            avatar_decoration_data = if (raw?.containsKey("avatar_decoration_data") == true) user.avatar_decoration_data else (user.avatar_decoration_data ?: existing.avatar_decoration_data),
            discriminator = user.discriminator ?: existing.discriminator,
            public_flags = user.public_flags ?: existing.public_flags,
            flags = user.flags ?: existing.flags,
            accent_color = raw.pick("accent_color", user.accent_color, existing.accent_color),
            banner = raw.pick("banner", user.banner, existing.banner),
            bio = raw.pick("bio", user.bio, existing.bio),
            pronouns = raw.pick("pronouns", user.pronouns, existing.pronouns),
            display_name_styles = if (raw?.containsKey("display_name_styles") == true) user.display_name_styles else (user.display_name_styles ?: existing.display_name_styles)
        )
    }

    fun cacheMember(guildId: String, userId: String, member: Member, raw: JsonObject? = null) {
        cacheMembers(guildId, listOf(userId to member), listOf(raw))
    }

    fun cacheMembers(guildId: String, members: List<Pair<String, Member>>, raws: List<JsonObject?>? = null) {
        if (members.isEmpty()) return
        _members.update { current ->
            val guildMembers = current[guildId]?.toMutableMap() ?: HashMap(members.size * 2)
            members.forEachIndexed { i, (userId, member) ->
                guildMembers[userId] = mergeMember(guildMembers[userId], member, raws?.getOrNull(i))
            }
            current + (guildId to guildMembers)
        }
        val users = ArrayList<Pair<User, JsonObject?>>(members.size)
        members.forEachIndexed { i, (_, member) ->
            member.user?.let { users.add(it to (raws?.getOrNull(i)?.get("user") as? JsonObject)) }
        }
        handleUserUpdates(users)
    }

    private fun mergeMember(existing: Member?, member: Member, raw: JsonObject?): Member {
        if (existing == null) return member
        return existing.copy(
            user = member.user ?: existing.user,
            nick = raw.pick("nick", member.nick, existing.nick),
            avatar = raw.pick("avatar", member.avatar, existing.avatar),
            roles = if (raw?.containsKey("roles") == true) member.roles else member.roles.ifEmpty { existing.roles },
            display_name_styles = if (raw?.containsKey("display_name_styles") == true) member.display_name_styles else (member.display_name_styles ?: existing.display_name_styles),
            premium_since = raw.pick("premium_since", member.premium_since, existing.premium_since),
            pending = member.pending ?: existing.pending,
            permissions = member.permissions ?: existing.permissions,
            communication_disabled_until = raw.pick("communication_disabled_until", member.communication_disabled_until, existing.communication_disabled_until),
            deaf = member.deaf,
            mute = member.mute,
            flags = member.flags,
            presence = member.presence ?: existing.presence
        )
    }

    fun getUser(userId: String): User? = _users.value[userId]

    fun getMember(guildId: String, userId: String): Member? {
        return _members.value[guildId]?.get(userId)
    }

    fun removeGuild(guildId: String) {
        if (!_members.value.containsKey(guildId)) return
        _members.update { current ->
            if (!current.containsKey(guildId)) current else current - guildId
        }
    }

    fun clear() {
        _users.value = emptyMap()
        _members.value = emptyMap()
        _currentUser.value = null
    }

    fun getCurrentMember(guildId: String?): Member? {
        val userId = _currentUser.value?.id ?: return null
        if (guildId == null) return null
        return getMember(guildId, userId)
    }
}

/**
 * When the raw payload carries [key], its value wins even if it is null: that is how Discord
 * says a field was cleared (nickname removed, bio emptied, timeout lifted). Without the raw
 * payload, fall back to keeping what we already had.
 */
private fun <T> JsonObject?.pick(key: String, incoming: T?, existing: T?): T? =
    if (this?.containsKey(key) == true) incoming else incoming ?: existing

