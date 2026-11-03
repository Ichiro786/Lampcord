package me.lampu.lampcord.shared.state

import me.lampu.lampcord.shared.model.Channel
import me.lampu.lampcord.shared.model.Member
import me.lampu.lampcord.shared.model.User

object FinderMatching {

    fun aliases(user: User, members: Map<String, Map<String, Member>>): List<String> {
        val names = ArrayList<String>(4)
        user.username?.let { names.add(it) }
        user.global_name?.takeIf { it.isNotBlank() && it != user.username }?.let { names.add(it) }
        members.values.forEach { guildMembers ->
            guildMembers[user.id]?.nick?.takeIf { it.isNotBlank() }?.let { names.add(it) }
        }
        return names.distinct()
    }

    fun rank(user: User, aliases: List<String>, query: String): Float? {
        val username = user.username ?: return null
        if (fuzzyMatch(query, username)) return 2f
        val nicks = aliases.filter { it != username }
        if (nicks.any { it.contains(query, ignoreCase = true) }) return 1.5f
        if (nicks.any { fuzzyMatch(query, it) }) return 1f
        return null
    }

    fun fuzzyMatch(query: String, target: String): Boolean {
        if (query.isEmpty()) return true
        val haystack = target.lowercase()
        var index = 0
        for (character in query.lowercase()) {
            index = if (character == ' ') {
                haystack.indexOf('-', index).let { if (it == -1) haystack.indexOf(' ', index) else it }
            } else {
                haystack.indexOf(character, index)
            }
            if (index == -1) return false
            index++
        }
        return true
    }

    fun dmRecipient(channel: Channel, users: Map<String, User>): User? {
        if (channel.guild_id != null && channel.type != 1 && channel.type != 3) return null
        val embedded = channel.recipients?.firstOrNull()
        if (embedded != null) return embedded
        val id = channel.recipient_ids?.firstOrNull() ?: return null
        return users[id]
    }
}
