package me.lampu.lampcord.shared.state

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import me.lampu.lampcord.shared.gateway.GatewayManager
import me.lampu.lampcord.shared.model.*
import me.lampu.lampcord.shared.settings.Settings

class GatewayHandler(
    private val json: Json,
    private val dispatcher: GatewayEventDispatcher,
    private val scope: CoroutineScope,
    private val userStore: UserStore,
    private val settingsStore: SettingsStore,
    private val userGuildSettingsStore: UserGuildSettingsStore,
    private val guildStore: GuildStore,
    private val messageStore: MessageStore,
    private val readStateStore: ReadStateStore,
    private val experimentStore: ExperimentStore,
    private val relationshipStore: RelationshipStore,
    private val presenceStore: PresenceStore,
    private val navigationStore: NavigationStore,
    private val gatewayManager: GatewayManager,
    private val tokenStore: TokenStore,
    private val memberListStore: MemberListStore,
    private val voiceStore: VoiceStore
) {
    /** True once this connection's real READY has been applied; a snapshot must not overwrite it. */
    @kotlin.concurrent.Volatile
    var liveReadyApplied = false

    /** Draws the cached READY while the gateway connects. Returns true if it was applied. */
    fun applySnapshot(data: JsonElement): Boolean {
        if (liveReadyApplied) return false
        me.lampu.lampcord.shared.utils.Logging.i("GatewayHandler", "Applying startup snapshot")
        handleReady(GatewayPayload(op = 0, d = data, t = "READY"))
        return true
    }

    fun handleGatewayEvent(payload: GatewayPayload) {
        when (payload.t) {
            "READY" -> {
                handleReady(payload)
                liveReadyApplied = true
                val token = Settings.shared.discordToken
                payload.d?.let { data -> scope.launch(Dispatchers.Default) { StartupSnapshot.save(json, token, data) } }
            }
            "READY_SUPPLEMENTAL" -> {
                handleReadySupplemental(payload)
                dispatcher.dispatch(payload)
            }
            "RESUMED" -> {
                navigationStore.isConnected = true
                navigationStore.isConnecting = false
                messageStore.handleConnected()
                memberListStore.resubscribe()
            }
            else -> dispatcher.dispatch(payload)
        }
    }

    /**
     * With PRIORITIZED_READY_PAYLOAD, READY carries only your own member per guild; everyone
     * else's member objects and all presences arrive here, indexed like READY's guild list.
     */
    private fun handleReadySupplemental(payload: GatewayPayload) {
        val data = payload.d?.jsonObject ?: return
        runCatching {
            val guildIds = data["guilds"]?.jsonArray?.map { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
                ?: return
            data["merged_members"]?.jsonArray?.forEachIndexed { index, list ->
                val guildId = guildIds.getOrNull(index) ?: return@forEachIndexed
                val raws = list.jsonArray
                val pairs = ArrayList<Pair<String, Member>>(raws.size)
                val rawObjs = ArrayList<JsonObject?>(raws.size)
                raws.forEach { raw ->
                    val member = runCatching { json.decodeFromJsonElement<Member>(raw) }.getOrNull() ?: return@forEach
                    val userId = member.userId() ?: return@forEach
                    pairs.add(userId to member)
                    rawObjs.add(raw.jsonObject)
                }
                if (pairs.isNotEmpty()) userStore.cacheMembers(guildId, pairs, rawObjs)
            }
            val presences = data["merged_presences"]?.let { json.decodeFromJsonElement<MergedPresences>(it) }
            val updates = ArrayList<PresenceUpdate>()
            presences?.friends?.let { updates.addAll(it) }
            presences?.guilds?.forEachIndexed { index, list ->
                val guildId = guildIds.getOrNull(index) ?: return@forEachIndexed
                list.forEach { updates.add(if (it.guild_id == null) it.copy(guild_id = guildId) else it) }
            }
            presenceStore.applyPresences(updates)
        }.onFailure {
            me.lampu.lampcord.shared.utils.Logging.e("GatewayHandler", "Failed to process READY_SUPPLEMENTAL", it)
        }
    }

    private fun handleReady(payload: GatewayPayload) {
        payload.d?.let { data ->
            val user = try {
                data.jsonObject["user"]?.let { json.decodeFromJsonElement<User>(it) }
            } catch (e: Exception) { null }

            user?.let { u ->
                userStore.setCurrentUser(u, data.jsonObject["user"]?.jsonObject)
                val token = Settings.shared.discordToken
                if (token.isNotBlank()) {
                    tokenStore.addAccount(token, u)
                }
            }

            try {
                val ready = json.decodeFromJsonElement<ReadyPayload>(data)
                
                if (ready.user_settings != null && ready.user_settings is JsonObject) {
                    val settings = json.decodeFromJsonElement<UserSettings>(ready.user_settings)
                    settingsStore.userSettings = settings
                }
                
                userGuildSettingsStore.handleReady(ready)
                
                val settings = settingsStore.userSettings
                val guildOrder = settings?.guild_folders?.flatMap { folder ->
                    folder.guild_ids.mapNotNull { it.jsonPrimitive.contentOrNull }
                }?.takeIf { it.isNotEmpty() }
                    ?: settings?.guild_positions?.mapNotNull { it.jsonPrimitive.contentOrNull ?: it.toString() }
                    ?: emptyList()
                
                guildStore.setGuilds(ready.guilds, guildOrder)
                guildStore.setPrivateChannels(ready.private_channels)

                ready.merged_members?.forEachIndexed { index, members ->
                    val guild = ready.guilds.getOrNull(index) ?: return@forEachIndexed
                    val membersJson = data.jsonObject["merged_members"]?.jsonArray?.getOrNull(index)?.jsonArray
                    val pairs = ArrayList<Pair<String, Member>>(members.size)
                    val raws = ArrayList<JsonObject?>(members.size)
                    members.forEachIndexed { memberIndex, member ->
                        val userId = member.userId() ?: return@forEachIndexed
                        pairs.add(userId to member)
                        raws.add(membersJson?.getOrNull(memberIndex)?.jsonObject)
                    }
                    userStore.cacheMembers(guild.id, pairs, raws)
                }

                ready.users?.let { users ->
                    val usersJson = data.jsonObject["users"]?.jsonArray
                    userStore.handleUserUpdates(
                        users.mapIndexed { index, user -> user to usersJson?.getOrNull(index)?.jsonObject }
                    )
                }

                presenceStore.handleReady(ready)
                ready.sessions?.let { sessions ->
                    user?.id?.let { presenceStore.handleSessions(it, sessions) }
                }

                readStateStore.handleReady(ready)
                experimentStore.handleReady(ready.experiments)
                ready.relationships?.let { relationshipStore.handleReady(it) }

                voiceStore.handleReady(data.jsonObject)
                
                navigationStore.isConnected = true
                navigationStore.isConnecting = false
                messageStore.handleConnected()

                if (navigationStore.selectedGuild == null && navigationStore.selectedChannel == null && !navigationStore.isFriendsSelected) {
                    navigationStore.restoreLastState { gatewayManager.sendSubscription(it) }
                }

                memberListStore.resubscribe()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // A schema change in READY used to be swallowed here, leaving the app stuck on
                // "Connecting" forever with nothing in the logs.
                me.lampu.lampcord.shared.utils.Logging.e("GatewayHandler", "Failed to process READY", e)
                navigationStore.isConnected = true
                navigationStore.isConnecting = false
            }
        }
    }
}
