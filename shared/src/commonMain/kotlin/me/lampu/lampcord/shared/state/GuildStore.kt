package me.lampu.lampcord.shared.state

import me.lampu.lampcord.shared.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.lampu.lampcord.shared.api.GuildApi
import me.lampu.lampcord.shared.model.*
import me.lampu.lampcord.shared.utils.Logging
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Clock
import kotlin.time.Duration

class GuildStore(
    private val guildApi: GuildApi,
    private val errorStore: AppErrorStore,
    private val selectionStore: SelectionStore,
    private val entityStore: EntityStore,
    private val userGuildSettingsStore: UserGuildSettingsStore,
    private val readStateStore: ReadStateStore,
    private val userStore: UserStore,
    private val scope: CoroutineScope
) {
    private companion object {
        // Discord channel override flag OPT_IN_ENABLED (1 << 12). 1 << 10 is UNREADS_ALL_MESSAGES,
        // which the old value toggled by mistake, so opt-ins never stuck.
        const val CHANNEL_OPT_IN_FLAG = 1 shl 12
        // User guild settings flag OPT_IN_CHANNELS_ON (1 << 14): only opted-in channels are shown.
        const val GUILD_OPT_IN_CHANNELS_ON = 1 shl 14
        // Discord rejects very large ack-bulk bodies; official clients send 100 at a time.
        const val ACK_BULK_CHUNK = 100
    }

    private val _guildIds = MutableStateFlow<List<String>>(emptyList())
    val guilds: StateFlow<List<Guild>> = combine(_guildIds, entityStore.guilds) { ids, allGuilds ->
        ids.mapNotNull { allGuilds[it] }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _privateChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val privateChannels: StateFlow<List<Channel>> = combine(_privateChannelIds, entityStore.channels) { ids, allChannels ->
        ids.mapNotNull { allChannels[it] }
            .sortedByDescending { it.lastMessageId()?.toLongOrNull() ?: 0L }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val allGuildChannels = entityStore.channels

    fun setGuilds(newGuilds: List<Guild>, order: List<String>) {
        Logging.i("GuildStore", "Setting ${newGuilds.size} guilds")
        newGuilds.forEach { entityStore.updateGuild(it) }
        val sortedIds = if (order.isNotEmpty()) {
            val orderIndex = order.withIndex().associate { it.value to it.index }
            newGuilds.map { it.id }.sortedWith(compareBy({ orderIndex[it] ?: -1 }, { it }))
        } else {
            newGuilds.sortedWith(Comparator { a, b ->
                val mutedA = userGuildSettingsStore.isGuildMuted(a.id)
                val mutedB = userGuildSettingsStore.isGuildMuted(b.id)
                if (mutedA != mutedB) return@Comparator if (mutedA) 1 else -1

                val j1 = userStore.getCurrentMember(a.id)?.joined_at ?: ""
                val j2 = userStore.getCurrentMember(b.id)?.joined_at ?: ""

                if (j1.isBlank() && j2.isNotBlank()) return@Comparator -1
                if (j1.isNotBlank() && j2.isBlank()) return@Comparator 1
                val joinedCompare = j1.compareTo(j2)
                if (joinedCompare != 0) return@Comparator joinedCompare

                val n1 = a.name ?: ""
                val n2 = b.name ?: ""
                n1.compareTo(n2, ignoreCase = true)
            }).map { it.id }
        }
        _guildIds.value = sortedIds
        refreshGuildPermissions()
    }

    private var permissionsJob: kotlinx.coroutines.Job? = null

    /** Pulls Discord's computed base permissions for each guild (see GuildApi.getMyGuildPermissions). */
    fun refreshGuildPermissions() {
        permissionsJob?.cancel()
        permissionsJob = scope.launch {
            val permissions = guildApi.getMyGuildPermissions()
            permissions.forEach { (guildId, perms) ->
                val guild = entityStore.guilds.value[guildId] ?: return@forEach
                if (guild.permissions != perms) entityStore.updateGuild(guild.copy(permissions = perms))
            }
        }
    }

    fun handleGuildCreate(guild: Guild, order: List<String>) {
        Logging.i("GuildStore", "Handling GUILD_CREATE: ${guild.name} (${guild.id})")
        entityStore.updateGuild(guild)
        if (guild.id !in _guildIds.value) {
            val newList = _guildIds.value + guild.id
            _guildIds.value = if (order.isNotEmpty()) {
                val orderIndex = order.withIndex().associate { it.value to it.index }
                newList.sortedWith(compareBy({ orderIndex[it] ?: -1 }, { it }))
            } else {
                val allGuilds = newList.mapNotNull { id -> entityStore.guilds.value[id] }
                allGuilds.sortedWith(Comparator { a, b ->
                    val mutedA = userGuildSettingsStore.isGuildMuted(a.id)
                    val mutedB = userGuildSettingsStore.isGuildMuted(b.id)
                    if (mutedA != mutedB) return@Comparator if (mutedA) 1 else -1

                    val j1 = userStore.getCurrentMember(a.id)?.joined_at ?: ""
                    val j2 = userStore.getCurrentMember(b.id)?.joined_at ?: ""

                    if (j1.isBlank() && j2.isNotBlank()) return@Comparator -1
                    if (j1.isNotBlank() && j2.isBlank()) return@Comparator 1
                    val joinedCompare = j1.compareTo(j2)
                    if (joinedCompare != 0) return@Comparator joinedCompare

                    val n1 = a.name ?: ""
                    val n2 = b.name ?: ""
                    n1.compareTo(n2, ignoreCase = true)
                }).map { it.id }
            }
        }
    }

    fun joinCompleted(guild: Guild) {
        entityStore.updateGuild(guild)
        _guildIds.update { if (guild.id in it) it else it + guild.id }
    }

    fun handleGuildDelete(guildId: String) {
        Logging.i("GuildStore", "Handling GUILD_DELETE: $guildId")
        _guildIds.value -= guildId
        entityStore.removeGuild(guildId)
    }

    fun reorderGuilds(order: List<String>) {
        if (order.isEmpty()) return
        val currentIds = _guildIds.value
        val orderIndex = order.withIndex().associate { it.value to it.index }
        val sortedIds = currentIds.sortedWith(compareBy({ orderIndex[it] ?: -1 }, { it }))
        if (sortedIds != currentIds) {
            _guildIds.value = sortedIds
        }
    }

    fun setPrivateChannels(channels: List<Channel>) {
        channels.forEach { entityStore.updateChannel(it) }
        _privateChannelIds.value = channels.map { it.id }.toSet()
    }

    fun handleChannelCreateOrUpdate(channel: Channel) {
        entityStore.updateChannel(channel)
        val updated = entityStore.channels.value[channel.id] ?: channel
        if (updated.guild_id == null && (updated.type == 1 || updated.type == 3)) {
            _privateChannelIds.update { it + updated.id }
        }
    }

    fun handleChannelDelete(channel: Channel) {
        _privateChannelIds.update { it - channel.id }
        entityStore.removeChannel(channel.id)
    }

    fun updateGuild(guildId: String, partial: Guild.Partial) {
        scope.launch {
            if (guildApi.updateGuild(guildId, partial)) {
                entityStore.guilds.value[guildId]?.let { g ->
                    entityStore.updateGuild(g.merge(partial))
                }
            }
        }
    }

    fun markGuildAsRead(guildId: String) {
        markGuildsAsRead(listOf(guildId), "guild")
    }

    fun markFolderAsRead(folder: GuildFolder) {
        val guildIds = folder.guild_ids.mapNotNull { it.jsonPrimitive.contentOrNull }.distinct()
        markGuildsAsRead(guildIds, "folder")
    }

    /** Whether the current user can see [channel]; true while the data needed to decide is missing. */
    fun canViewChannel(channel: Channel): Boolean {
        val guildId = channel.guild_id ?: return true
        val guild = entityStore.guilds.value[guildId] ?: return true
        val userId = userStore.currentUser.value?.id ?: return true
        val member = userStore.getMember(guildId, userId) ?: return true
        return PermissionHelper.canViewChannel(member, guild, channel, userId)
    }

    private fun markGuildsAsRead(guildIds: Collection<String>, target: String, categoryId: String? = null) {
        if (guildIds.isEmpty()) return
        scope.launch {
            try {
                val guildSet = guildIds.toSet()
                val readStates = allGuildChannels.value.values
                    .asSequence()
                    .filter { it.guild_id in guildSet && it.type != 4 }
                    .filter { categoryId == null || it.parent_id == categoryId }
                    .filter {
                        readStateStore.isUnread(it) || readStateStore.getMentionCount(it.id) > 0
                    }
                    // Acking a channel we can't see makes Discord reject the whole batch.
                    .filter { canViewChannel(it) }
                    .mapNotNull { channel ->
                        readStateStore.mostRecentMessageId(channel)
                            ?.let { channel.id to it }
                    }
                    .toMap()

                if (readStates.isEmpty()) return@launch
                var failed = 0
                readStates.entries.chunked(ACK_BULK_CHUNK).forEach { chunk ->
                    if (!readStateStore.ackBulk(chunk.associate { it.key to it.value })) failed++
                }
                if (failed > 0) {
                    errorStore.pushError("Failed to mark $target as read.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                errorStore.pushError("Error marking $target as read: ${e.message}")
            }
        }
    }

    fun leaveGuild(guildId: String, onLeave: () -> Unit) {
        scope.launch {
            try {
                if (guildApi.leaveGuild(guildId)) {
                    handleGuildDelete(guildId)
                    onLeave()
                } else {
                    errorStore.pushError("Failed to leave guild.")
                }
            } catch (e: Exception) {
                errorStore.pushError("Error leaving guild: ${e.message}")
            }
        }
    }

    fun toggleMuteGuild(guildId: String) {
        if (userGuildSettingsStore.isGuildMuted(guildId)) unmuteGuild(guildId)
        else muteGuild(guildId, null)
    }

    fun unmuteGuild(guildId: String) {
        val effectiveGuildId = if (guildId == "@me") null else guildId
        val partial = UserGuildSettings.Partial(guild_id = effectiveGuildId, muted = false, mute_config = null)
        userGuildSettingsStore.handlePartialUpdate(partial)
        scope.launch {
            guildApi.updateUserGuildSettings(guildId, partial)
        }
    }

    fun muteGuild(guildId: String, duration: Duration?) {
        val effectiveGuildId = if (guildId == "@me") null else guildId
        val muteConfig = if (duration != null) {
            val endTime = (Clock.System.now() + duration).toString()
            MuteConfig(end_time = endTime)
        } else MuteConfig(end_time = null)

        val partial = UserGuildSettings.Partial(guild_id = effectiveGuildId, muted = true, mute_config = muteConfig)
        userGuildSettingsStore.handlePartialUpdate(partial)
        scope.launch {
            guildApi.updateUserGuildSettings(guildId, partial)
        }
    }
    fun isChannelOptedIn(guildId: String, channelId: String): Boolean {
        val effectiveGuildId = if (guildId == "@me") null else guildId
        val settings = userGuildSettingsStore.userGuildSettings.value[effectiveGuildId] ?: return true
        val override = settings.channel_overrides.find { it.channel_id == channelId }
        if (override != null && (override.flags and CHANNEL_OPT_IN_FLAG) != 0) return true
        // Outside opt-in mode every channel is shown, so it counts as opted in.
        return (settings.flags and GUILD_OPT_IN_CHANNELS_ON) == 0 && override == null
    }

    fun setChannelOptIn(guildId: String, channelId: String, enabled: Boolean) {
        val effectiveGuildId = if (guildId == "@me") null else guildId
        val previous = userGuildSettingsStore.userGuildSettings.value[effectiveGuildId]
        val guildSettings = previous ?: UserGuildSettings(guild_id = effectiveGuildId)
        val currentOverrides = guildSettings.channel_overrides.toMutableList()
        val index = currentOverrides.indexOfFirst { it.channel_id == channelId }
        val existingFlags = if (index != -1) currentOverrides[index].flags else 0
        val newFlags = if (enabled) existingFlags or CHANNEL_OPT_IN_FLAG else existingFlags and CHANNEL_OPT_IN_FLAG.inv()

        val newOverride = if (index != -1) {
            currentOverrides[index].copy(flags = newFlags)
        } else {
            ChannelOverride(channel_id = channelId, flags = newFlags)
        }
        if (index != -1) currentOverrides[index] = newOverride else currentOverrides.add(newOverride)

        // Apply locally first so Browse Channels shows the new state when reopened,
        // then send only the changed override (Discord merges overrides by channel id).
        userGuildSettingsStore.handlePartialUpdate(
            UserGuildSettings.Partial(guild_id = effectiveGuildId, channel_overrides = currentOverrides)
        )
        scope.launch {
            val ok = guildApi.updateUserGuildSettings(
                guildId,
                UserGuildSettings.Partial(channel_overrides = listOf(newOverride))
            )
            if (!ok) {
                previous?.let {
                    userGuildSettingsStore.handlePartialUpdate(
                        UserGuildSettings.Partial(guild_id = effectiveGuildId, channel_overrides = it.channel_overrides)
                    )
                }
                errorStore.pushError("Couldn't save channel visibility.")
            }
        }
    }

    fun toggleMuteChannel(guildId: String, channelId: String) {
        val effectiveGuildId = if (guildId == "@me") null else guildId
        val guildSettings = userGuildSettingsStore.userGuildSettings.value[effectiveGuildId] ?: UserGuildSettings(guild_id = effectiveGuildId)
        val currentOverrides = guildSettings.channel_overrides.toMutableList()
        val index = currentOverrides.indexOfFirst { it.channel_id == channelId }
        
        val newOverride = if (index != -1) {
            currentOverrides[index].copy(muted = !currentOverrides[index].muted)
        } else {
            ChannelOverride(channel_id = channelId, muted = true)
        }
        
        if (index != -1) currentOverrides[index] = newOverride else currentOverrides.add(newOverride)

        val partial = UserGuildSettings.Partial(guild_id = effectiveGuildId, channel_overrides = currentOverrides)
        userGuildSettingsStore.handlePartialUpdate(partial)

        scope.launch {
            guildApi.updateUserGuildSettings(guildId, partial)
        }
    }

    fun muteChannelForDuration(guildId: String, channelId: String, duration: Duration?) {
        val effectiveGuildId = if (guildId == "@me") null else guildId
        val guildSettings = userGuildSettingsStore.userGuildSettings.value[effectiveGuildId] ?: UserGuildSettings(guild_id = effectiveGuildId)
        val currentOverrides = guildSettings.channel_overrides.toMutableList()
        val index = currentOverrides.indexOfFirst { it.channel_id == channelId }
        val muteConfig = if (duration != null) {
            MuteConfig(end_time = (Clock.System.now() + duration).toString())
        } else null

        val newOverride = if (index != -1) {
            currentOverrides[index].copy(muted = true, mute_config = muteConfig)
        } else {
            ChannelOverride(channel_id = channelId, muted = true, mute_config = muteConfig)
        }
        if (index != -1) currentOverrides[index] = newOverride else currentOverrides.add(newOverride)

        val partial = UserGuildSettings.Partial(guild_id = effectiveGuildId, channel_overrides = currentOverrides)
        userGuildSettingsStore.handlePartialUpdate(partial)

        scope.launch {
            guildApi.updateUserGuildSettings(guildId, partial)
        }
    }

    fun unmuteChannel(guildId: String, channelId: String) {
        val effectiveGuildId = if (guildId == "@me") null else guildId
        val guildSettings = userGuildSettingsStore.userGuildSettings.value[effectiveGuildId] ?: UserGuildSettings(guild_id = effectiveGuildId)
        val currentOverrides = guildSettings.channel_overrides.toMutableList()
        val index = currentOverrides.indexOfFirst { it.channel_id == channelId }
        if (index != -1) {
            currentOverrides[index] = currentOverrides[index].copy(muted = false, mute_config = null)
        } else {
            currentOverrides.add(ChannelOverride(channel_id = channelId, muted = false, mute_config = null))
        }

        val partial = UserGuildSettings.Partial(guild_id = effectiveGuildId, channel_overrides = currentOverrides)
        userGuildSettingsStore.handlePartialUpdate(partial)

        scope.launch {
            guildApi.updateUserGuildSettings(guildId, partial)
        }
    }

    fun setChannelNotificationMode(guildId: String, channelId: String, mode: Int) {
        val effectiveGuildId = if (guildId == "@me") null else guildId
        val guildSettings = userGuildSettingsStore.userGuildSettings.value[effectiveGuildId] ?: UserGuildSettings(guild_id = effectiveGuildId)
        val currentOverrides = guildSettings.channel_overrides.toMutableList()
        val index = currentOverrides.indexOfFirst { it.channel_id == channelId }

        val newOverride = if (index != -1) {
            currentOverrides[index].copy(message_notifications = mode)
        } else {
            ChannelOverride(channel_id = channelId, message_notifications = mode)
        }
        if (index != -1) currentOverrides[index] = newOverride else currentOverrides.add(newOverride)

        val partial = UserGuildSettings.Partial(guild_id = effectiveGuildId, channel_overrides = currentOverrides)
        userGuildSettingsStore.handlePartialUpdate(partial)

        scope.launch {
            guildApi.updateUserGuildSettings(guildId, partial)
        }
    }

    fun setServerDMsAllowed(guildId: String, allowed: Boolean) {
        scope.launch {
            guildApi.updateUserGuildSettings(guildId, UserGuildSettings.Partial(message_notifications = if (allowed) 0 else 2))
        }
    }

    fun setHideMutedChannels(guildId: String, hide: Boolean) {
        scope.launch {
            guildApi.updateUserGuildSettings(guildId, UserGuildSettings.Partial(hide_muted_channels = hide))
        }
    }

    fun markCategoryAsRead(categoryId: String) {
        val channel = allGuildChannels.value[categoryId] ?: return
        val guildId = channel.guild_id ?: return
        markGuildsAsRead(listOf(guildId), "category", categoryId)
    }

    fun isFolderUnread(folder: GuildFolder): Boolean {
        return folder.guild_ids.any { el ->
            val id = el.jsonPrimitive.contentOrNull ?: return@any false
            isGuildUnread(id)
        }
    }

    fun getFolderMentionCount(folder: GuildFolder): Int {
        return folder.guild_ids.sumOf { el ->
            val id = el.jsonPrimitive.contentOrNull ?: return@sumOf 0
            getGuildMentionCount(id)
        }
    }

    fun isChannelMuted(guildId: String?, channelId: String): Boolean =
        userGuildSettingsStore.isChannelMuted(guildId, channelId)

    fun isGuildUnread(guildId: String): Boolean {
        if (userGuildSettingsStore.isGuildMuted(guildId)) return false
        return allGuildChannels.value.values.any { 
            // Threads arrive from THREAD_LIST_SYNC without read states; counting them kept servers unread forever.
            it.guild_id == guildId && it.type != 4 && it.type !in 10..12 && !userGuildSettingsStore.isChannelMuted(it.guild_id, it.id) &&
                readStateStore.isUnread(it) && canViewChannel(it)
        }
    }

    fun getGuildMentionCount(guildId: String): Int {
        return allGuildChannels.value.values.filter { it.guild_id == guildId }.sumOf { readStateStore.getMentionCount(it.id) }
    }

    fun getForumThreads(channelId: String): List<Channel> {
        return allGuildChannels.value.values.filter { it.parent_id == channelId }
    }

    fun clear() {
        _guildIds.value = emptyList()
        _privateChannelIds.value = emptySet()
    }
}
