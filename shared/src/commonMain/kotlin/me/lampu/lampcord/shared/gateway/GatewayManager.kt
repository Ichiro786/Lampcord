package me.lampu.lampcord.shared.gateway

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.lampu.lampcord.shared.api.AuthApi
import me.lampu.lampcord.shared.api.RestClient
import me.lampu.lampcord.shared.model.Activity
import me.lampu.lampcord.shared.model.GatewayPayload
import me.lampu.lampcord.shared.model.Identify
import me.lampu.lampcord.shared.model.IdentifyClientState
import me.lampu.lampcord.shared.model.MemberListUpdate
import me.lampu.lampcord.shared.model.Resume
import me.lampu.lampcord.shared.utils.Logging
import me.lampu.lampcord.shared.utils.NetworkMonitor
import me.lampu.lampcord.shared.utils.getCpuCoreCount
import me.lampu.lampcord.shared.utils.getCurrentTimeMillis
import me.lampu.lampcord.shared.utils.getDeviceName
import me.lampu.lampcord.shared.utils.getMemoryMemory
import me.lampu.lampcord.shared.utils.getOsSdkVersion
import me.lampu.lampcord.shared.utils.getPlatformName
import me.lampu.lampcord.shared.utils.randomUUID
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val SEND_WINDOW_MS = 60_000L
private const val SEND_LIMIT = 110
private const val MAX_QUEUE_SIZE = 500

class GatewayManager(
    private val client: HttpClient,
    private val authApi: AuthApi,
    private val rest: RestClient,
    private val readStateStore: me.lampu.lampcord.shared.state.ReadStateStore? = null,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) {
    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private val _events = MutableSharedFlow<GatewayPayload>(
        extraBufferCapacity = 4096,
        // SUSPEND, not DROP_OLDEST: when the collector falls behind (e.g. while READY is
        // processed) the socket reader waits instead of silently discarding dispatch events,
        // which desynced message/channel state.
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    val events: SharedFlow<GatewayPayload> = _events.asSharedFlow()

    private val queue = ArrayDeque<GatewayPayload>()
    private val lazyQueue = LinkedHashMap<String, GatewayPayload>()
    private val queueLock = Mutex()
    private val queueChannel = Channel<Unit>(Channel.CONFLATED)
    private val sendTimes = ArrayDeque<Long>()
    @Volatile private var authenticated = false

    private var heartbeatJob: Job? = null
    @Volatile private var heartbeatAckReceived = true
    private var timeSpentJob: Job? = null
    private var helloTimeoutJob: Job? = null
    private var lastSequence: Int? = null
    var sessionId: String? = null
    private var resumeGatewayUrl: String? = null
    @Volatile private var cachedGatewayUrl: String? = null
    private var clientHeartbeatSessionId = randomUUID()
    // Same launch identity as REST's X-Super-Properties, so both look like one client launch.
    private val clientLaunchId get() = rest.launchId
    private val launchSignature get() = rest.launchSignature

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var reconnectAttempt = 0
    private val maxReconnectDelay = 30.seconds

    private val guildSubscriptions = mutableMapOf<String, GuildSubscriptionState>()

    private class GuildSubscriptionState {
        var typing: Boolean = true
        var threads: Boolean = true
        var activities: Boolean = true
        val members = mutableSetOf<String>()
        val channels = mutableMapOf<String, List<List<Int>>>()
        val threadMemberLists = mutableSetOf<String>()
        private val channelOrder = mutableListOf<String>()

        fun updateChannel(channelId: String, listId: String, ranges: List<List<Int>>, isThread: Boolean) {
            if (isThread) {
                threadMemberLists.add(channelId)
            } else {
                channels[channelId] = ranges
                channelOrder.remove(channelId)
                channelOrder.add(channelId)
                if (channelOrder.size > 5) {
                    val oldest = channelOrder.removeAt(0)
                    channels.remove(oldest)
                }
            }
        }
    }

    fun connect(token: String) {
        // A reconnect keeps queued payloads and guild subscriptions; only an explicit
        // disconnect() throws them away.
        destroy(clearState = false)
        Logging.i("Gateway", "Connecting to Discord Gateway...")
        connectionJob = scope.launch {
            val self = coroutineContext[Job]
            try {
                // Fetched once per process: it never changes, and resumes use resume_gateway_url.
                val gatewayUrl = cachedGatewayUrl
                    ?: authApi.getGatewayUrl()?.also { cachedGatewayUrl = it }
                    ?: "wss://gateway.discord.gg"
                val url = (resumeGatewayUrl ?: gatewayUrl).removeSuffix("/") + "/?v=9&encoding=json"
                Logging.d("Gateway", "Gateway URL: $url")

                val userAgent = when (getPlatformName()) {
                    "android" -> "Discord-Android/341200;RNA"
                    "ios" -> "Discord/105180 CFNetwork/1410.0.3 Darwin/22.4.0"
                    else -> me.lampu.lampcord.shared.api.desktopUserAgent()
                }

                startHelloTimeout(token)

                client.webSocket(
                    urlString = url,
                    request = {
                        header("User-Agent", userAgent)
                        header("Origin", "https://discord.com")
                        header("Accept-Language", "en-US,en;q=0.9")
                        header("Cache-Control", "no-cache")
                        // The gateway is idle between heartbeats (every ~41s), so the HTTP
                        // socket read timeout must not apply. Liveness is tracked by the
                        // heartbeat ACK handler instead.
                        timeout {
                            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                        }
                    }
                ) {
                    Logging.i("Gateway", "WebSocket connected.")
                    session = this
                    reconnectAttempt = 0
                    authenticated = false
                    sendTimes.clear()
                    val sender = launch { drainQueue(this@webSocket) }

                    while (isActive) {
                        try {
                            val frame = incoming.receive()
                            if (frame is Frame.Text) {
                                val payload = decodePayload(frame.readText())
                                lastSequence = payload.s ?: lastSequence
                                handlePayload(payload, token)
                                if (payload.op == 0) _events.emit(payload)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: ClosedReceiveChannelException) {
                            Logging.w("Gateway", "WebSocket terminated unexpectedly", e)
                            val reason = withTimeoutOrNull(2.seconds) { closeReason.await() }
                            when (reason?.code?.toInt()) {
                                4003, 4004 -> {
                                    _events.emit(GatewayPayload(op = -1, t = "AUTH_FAILED"))
                                    disconnect("auth failed ${reason.code}")
                                }
                                // Invalid seq / session timed out: a resume can only fail, so start fresh.
                                4007, 4009 -> {
                                    sessionId = null
                                    lastSequence = null
                                    resumeGatewayUrl = null
                                }
                                // Invalid shard, sharding required, bad API version, invalid or
                                // disallowed intents: reconnecting just loops on the same error.
                                4010, 4011, 4012, 4013, 4014 -> {
                                    Logging.e("Gateway", "Fatal gateway close ${reason.code}: ${reason.message}")
                                    disconnect("fatal close ${reason.code}")
                                }
                            }
                            break
                        } catch (e: Exception) {
                            Logging.e("Gateway", "Error in WebSocket loop", e)
                            break
                        }
                    }
                    sender.cancel()
                    val reason = withContext(NonCancellable) {
                        withTimeoutOrNull(1.seconds) { closeReason.await() }
                    }
                    Logging.w("Gateway", "WebSocket closed: code=${reason?.code} reason=${reason?.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logging.e("Gateway", "Connection failed", e)
            } finally {
                // If connect() already started a replacement (op 7, HELLO timeout), this cleanup
                // runs late and must not tear down the new connection's session and heartbeat.
                val current = connectionJob
                if (current == null || current === self) {
                    Logging.i("Gateway", "Cleaning up connection...")
                    session = null
                    authenticated = false
                    stopHeartbeat()
                    stopTimeSpentUpdates()
                    stopHelloTimeout()
                    withContext(NonCancellable) { queueLock.withLock { lazyQueue.clear() } }
                }

                if (isActive) {
                    reconnectAttempt++
                    val delay = (1.seconds * (1 shl (reconnectAttempt - 1).coerceAtMost(5))).coerceAtMost(maxReconnectDelay)
                    Logging.i("Gateway", "Reconnecting in $delay (attempt $reconnectAttempt)...")
                    delay(delay)
                    if (!NetworkMonitor.isOnline.value) {
                        Logging.i("Gateway", "Offline. Waiting for network...")
                        NetworkMonitor.isOnline.first { it }
                        reconnectAttempt = 0
                    }
                    connect(token)
                }
            }
        }
    }

    fun disconnect(reason: String = "requested") {
        Logging.i("Gateway", "Disconnecting: $reason")
        destroy(clearState = true)
    }

    private fun destroy(clearState: Boolean) {
        connectionJob?.cancel()
        connectionJob = null
        session = null
        authenticated = false
        stopHeartbeat()
        stopTimeSpentUpdates()
        stopHelloTimeout()
        if (!clearState) return
        guildSubscriptions.clear()
        scope.launch {
            queueLock.withLock {
                lazyQueue.clear()
                queue.clear()
            }
            presenceLock.withLock {
                appActivities.clear()
                lastPresenceJson = null
            }
        }
    }

    fun close() {
        destroy(clearState = true)
        scope.cancel()
    }

    private fun decodePayload(text: String): GatewayPayload {
        val payload = json.decodeFromString<GatewayPayload>(text)

        if (payload.t != "GUILD_MEMBER_LIST_UPDATE") return payload
        val data = payload.d ?: return payload

        val update = runCatching { json.decodeFromJsonElement<MemberListUpdate>(data) }.getOrNull() ?: return payload
        return payload.copy(d = null).also { it.decoded = update }
    }

    private fun enqueue(payload: GatewayPayload) {
        scope.launch {
            queueLock.withLock {
                queue.addLast(payload)
                while (queue.size > MAX_QUEUE_SIZE) {
                    val presenceIdx = queue.indexOfFirst { it.op == 3 }
                    queue.removeAt(if (presenceIdx >= 0) presenceIdx else 0)
                }
            }
            queueChannel.trySend(Unit)
        }
    }

    private fun enqueueLazy(guildId: String, payload: GatewayPayload) {
        scope.launch {
            queueLock.withLock { lazyQueue[guildId] = payload }
            queueChannel.trySend(Unit)
        }
    }

    private suspend fun drainQueue(socket: DefaultClientWebSocketSession) {
        for (signal in queueChannel) {
            while (true) {
                var next = queueLock.withLock { if (authenticated) queue.removeFirstOrNull() else null }
                if (next == null) {
                    val hasLazy = queueLock.withLock { authenticated && lazyQueue.isNotEmpty() }
                    if (!hasLazy) break
                    delay(100.milliseconds)
                    next = queueLock.withLock {
                        lazyQueue.keys.firstOrNull()?.let { lazyQueue.remove(it) }
                    } ?: continue
                }
                awaitSendSlot()
                if (Logging.debugEnabled) Logging.d("Gateway", "send op=${next.op} window60s=${sendTimes.size}")
                try {
                    socket.send(json.encodeToString(next))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logging.e("Gateway", "Send failed for op ${next.op}", e)
                    // Put it back so it goes out after the reconnect, and force that reconnect
                    // instead of leaving the queue silently stalled on a broken socket.
                    // Lazy guild requests (op 14) are superseded by newer ones; don't resurrect them.
                    if (next.op != 14) queueLock.withLock { queue.addFirst(next) }
                    // Not 1000/1001: those invalidate the session and would prevent a resume.
                    runCatching { socket.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "send failed")) }
                    return
                }
            }
        }
    }

    private suspend fun awaitSendSlot() {
        while (true) {
            val now = getCurrentTimeMillis()
            while (sendTimes.isNotEmpty() && now - sendTimes.first() >= SEND_WINDOW_MS) sendTimes.removeFirst()
            if (sendTimes.size < SEND_LIMIT) break
            val wait = SEND_WINDOW_MS - (now - sendTimes.first())
            Logging.w("Gateway", "Send budget exhausted, pausing ${wait}ms")
            delay(wait.milliseconds)
        }
        sendTimes.addLast(getCurrentTimeMillis())
    }

    private suspend fun handlePayload(payload: GatewayPayload, token: String) {
        when (payload.op) {
            10 -> {
                stopHelloTimeout()
                val heartbeatInterval = payload.d?.jsonObject?.get("heartbeat_interval")?.jsonPrimitive?.let {
                    it.longOrNull ?: it.doubleOrNull?.toLong()
                } ?: 41250
                startHeartbeat(heartbeatInterval)

                if (sessionId != null && lastSequence != null) {
                    resume(token)
                } else {
                    identify(token)
                }
            }
            0 -> {
                when (payload.t) {
                    "READY" -> {
                        val data = payload.d?.jsonObject
                        sessionId = data?.get("session_id")?.jsonPrimitive?.content
                        resumeGatewayUrl = data?.get("resume_gateway_url")?.jsonPrimitive?.content
                        Logging.i("Gateway", "READY. Session: $sessionId")
                        markAuthenticated()
                        startTimeSpentUpdates()
                        // A fresh session starts with no activities; republish ours if any.
                        scope.launch {
                            presenceLock.withLock {
                                if (appActivities.isNotEmpty()) sendMergedPresenceLocked(force = true)
                            }
                        }
                    }
                    "RESUMED" -> {
                        Logging.i("Gateway", "RESUMED session $sessionId")
                        markAuthenticated()
                    }
                }
            }
            1 -> {
                sendHeartbeat()
            }
            7 -> {
                Logging.i("Gateway", "Server requested reconnect (op 7)")
                connect(token)
            }
            9 -> {
                val resumable = payload.d?.jsonPrimitive?.boolean ?: false
                Logging.w("Gateway", "Invalid session. Resumable: $resumable")
                if (!resumable) {
                    sessionId = null
                    lastSequence = null
                    resumeGatewayUrl = null
                }
                // Discord asks for a random 1-5s wait; resume when it says the session survives.
                delay((1000L..5000L).random().milliseconds)
                if (resumable) resume(token) else identify(token)
            }
            11 -> {
                heartbeatAckReceived = true
            }
        }
    }

    private fun startHeartbeat(interval: Long) {
        Logging.d("Gateway", "Starting heartbeat every ${interval}ms")
        heartbeatJob?.cancel()
        heartbeatAckReceived = true
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(interval.milliseconds)
                if (!heartbeatAckReceived) {
                    session?.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Heartbeat ACK timeout"))
                    return@launch
                }
                heartbeatAckReceived = false
                sendHeartbeat()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun markAuthenticated() {
        authenticated = true
        queueChannel.trySend(Unit)
    }

    private fun startHelloTimeout(token: String) {
        helloTimeoutJob?.cancel()
        helloTimeoutJob = scope.launch {
            delay(20.seconds)
            Logging.w("Gateway", "No HELLO within 20s, reconnecting")
            connect(token)
        }
    }

    private fun stopHelloTimeout() {
        helloTimeoutJob?.cancel()
        helloTimeoutJob = null
    }

    private fun startTimeSpentUpdates() {
        timeSpentJob?.cancel()
        timeSpentJob = scope.launch {
            while (isActive) {
                sendUpdateTimeSpent()
                delay(30.minutes)
                clientHeartbeatSessionId = randomUUID()
            }
        }
    }

    private fun stopTimeSpentUpdates() {
        timeSpentJob?.cancel()
        timeSpentJob = null
    }

    private suspend fun sendHeartbeat() {
        val payload = GatewayPayload(
            op = 1,
            d = lastSequence?.let { JsonPrimitive(it) } ?: JsonNull
        )
        sendNow(payload)
    }

    fun sendExpeditedHeartbeat() {
        scope.launch {
            sendHeartbeat()
        }
    }

    private fun sendUpdateTimeSpent() {
        val payload = GatewayPayload(
            op = 41,
            d = buildJsonObject {
                put("initialization_timestamp", JsonPrimitive(getCurrentTimeMillis()))
                put("session_id", JsonPrimitive(clientHeartbeatSessionId))
                put("client_launch_id", JsonPrimitive(clientHeartbeatSessionId))
            }
        )
        enqueue(payload)
    }

    private suspend fun resume(token: String) {
        val id = sessionId ?: return identify(token)
        val seq = lastSequence ?: return identify(token)
        Logging.i("Gateway", "Resuming session $id at sequence $seq")
        val resume = Resume(
            token = token,
            session_id = id,
            seq = seq
        )
        val payload = GatewayPayload(op = 6, d = json.encodeToJsonElement(resume))
        sendNow(payload)
    }

    private suspend fun identify(token: String) {
        Logging.i("Gateway", "Identifying...")
        val platform = getPlatformName()
        val isMobile = platform == "android" || platform == "ios"

        val properties = buildMap {
            if (isMobile) {
                put("os", JsonPrimitive(if (platform == "android") "Android" else "iOS"))
                put("browser", JsonPrimitive(if (platform == "android") "Discord Android" else "Discord iOS"))
                put("device", JsonPrimitive(getDeviceName()))
            } else {
                put("os", JsonPrimitive(platform.replaceFirstChar { it.uppercase() }))
                put("browser", JsonPrimitive("Discord Client"))
                put("device", JsonPrimitive(""))
            }
            put("system_locale", JsonPrimitive("en-US"))
            put("has_client_mods", JsonPrimitive(false))
            put("client_version", JsonPrimitive(if (isMobile) "341.0 - rn" else me.lampu.lampcord.shared.api.DESKTOP_CLIENT_VERSION))
            put("release_channel", JsonPrimitive("stable"))
            put("device_vendor_id", JsonPrimitive(rest.vendorId))
            put("design_id", JsonPrimitive(if (isMobile) 2 else 0))
            put("browser_user_agent", JsonPrimitive(""))
            put("browser_version", JsonPrimitive(""))
            put("os_version", JsonPrimitive(getOsSdkVersion()))
            put("client_build_number", JsonPrimitive(if (isMobile) 6081 else me.lampu.lampcord.shared.api.DESKTOP_CLIENT_BUILD))
            put("client_event_source", JsonNull)
            put("client_launch_id", JsonPrimitive(clientLaunchId))
            put("launch_signature", JsonPrimitive(launchSignature))
            put("client_heartbeat_session_id", JsonPrimitive(clientHeartbeatSessionId))
            put("client_app_state", JsonPrimitive("active"))
            put("accessibility_features", JsonPrimitive(0))
            put("accessibility_support_enabled", JsonPrimitive(false))
            put("client_performance_cpu", JsonPrimitive(getCpuCoreCount()))
            put("client_performance_memory", JsonPrimitive(getMemoryMemory()))
            put("cpu_core_count", JsonPrimitive(getCpuCoreCount()))
        }

        val highestLastMessageId = readStateStore?.readStates?.value?.values
            ?.mapNotNull { it.lastMessageId() }
            ?.maxByOrNull { it.toLongOrNull() ?: 0L }
            ?.toLongOrNull() ?: 0L

        val identify = Identify(
            token = token,
            properties = properties,
            capabilities = 351,
            large_threshold = 100,
            compress = false,
            client_state = IdentifyClientState(
                guild_hashes = emptyMap(),
                highest_last_message_id = highestLastMessageId,
                read_state_version = 0,
                user_guild_settings_version = -1
            )
        )
        val payload = GatewayPayload(op = 2, d = json.encodeToJsonElement(identify))
        sendNow(payload)
    }

    suspend fun sendPayload(payload: GatewayPayload) {
        enqueue(payload)
    }

    /**
     * Asks the gateway for members whose username or nickname matches [query].
     *
     * Search only covers what the client already holds, so a user who has never been cached —
     * an offline member, typically — is invisible until they are pulled in here. The reply
     * arrives as GUILD_MEMBERS_CHUNK.
     */
    fun requestGuildMembers(
        guildId: String,
        query: String? = null,
        userIds: List<String>? = null,
        limit: Int = 100
    ) {
        val payload = GatewayPayload(
            op = 8,
            d = buildJsonObject {
                put("guild_id", JsonPrimitive(guildId))
                query?.let { put("query", it) }
                userIds?.let { ids ->
                    put("user_ids", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
                }
                put("limit", limit)
                put("presences", true)
            }
        )
        enqueue(payload)
    }

    private suspend fun sendNow(payload: GatewayPayload) {
        session?.send(json.encodeToString(payload))
    }

    /**
     * The user's own status and custom status, supplied by the settings layer. Every presence
     * update this session sends is built on top of it, so an app activity (music, RPC) never
     * wipes the custom status or resets online/idle/dnd.
     */
    var presenceBase: () -> PresenceBase = { PresenceBase() }

    data class PresenceBase(
        val status: String? = null,
        val customStatusText: String? = null,
        val customStatusEmojiId: String? = null,
        val customStatusEmojiName: String? = null
    )

    private val appActivities = LinkedHashMap<String, Activity>()
    private val presenceLock = Mutex()
    private var lastPresenceJson: String? = null

    /**
     * Sets (or clears, with null) the activity published under [source], e.g. "music" or an RPC
     * client id. Activities from different sources coexist; identical updates are not resent,
     * which keeps media seek/metadata churn from burning the presence rate limit.
     */
    fun setAppActivity(source: String, activity: Activity?) {
        scope.launch {
            presenceLock.withLock {
                val changed = if (activity == null) appActivities.remove(source) != null
                else appActivities.put(source, activity) != activity
                if (changed) sendMergedPresenceLocked(force = false)
            }
        }
    }

    /** Re-publishes the merged presence, e.g. after the user changed status or custom status. */
    fun refreshPresence() {
        scope.launch {
            presenceLock.withLock {
                if (appActivities.isNotEmpty()) sendMergedPresenceLocked(force = false)
            }
        }
    }

    private fun sendMergedPresenceLocked(force: Boolean) {
        val base = presenceBase()
        val d = buildJsonObject {
            put("status", JsonPrimitive(base.status ?: "online"))
            put("activities", buildJsonArray {
                if (base.customStatusText != null || base.customStatusEmojiName != null) {
                    add(buildJsonObject {
                        put("name", "Custom Status")
                        put("type", 4)
                        base.customStatusText?.let { put("state", it) }
                        if (base.customStatusEmojiName != null) {
                            put("emoji", buildJsonObject {
                                put("name", base.customStatusEmojiName)
                                base.customStatusEmojiId?.let { put("id", it) }
                            })
                        }
                    })
                }
                appActivities.values.forEach { add(json.encodeToJsonElement<Activity>(it)) }
            })
            put("afk", JsonPrimitive(false))
            put("since", JsonPrimitive(0))
        }
        val encoded = d.toString()
        if (!force && encoded == lastPresenceJson) return
        lastPresenceJson = encoded
        enqueue(GatewayPayload(op = 3, d = d))
    }

    /** Low-level presence update. Prefer [setAppActivity], which preserves status and custom status. */
    fun updatePresence(status: String?, activities: List<Activity>?) {
        val payload = GatewayPayload(
            op = 3,
            d = buildJsonObject {
                put("status", JsonPrimitive(status ?: presenceBase().status ?: "online"))
                put("activities", buildJsonArray {
                    activities?.forEach { activity ->
                        add(json.encodeToJsonElement<Activity>(activity))
                    }
                })
                put("afk", JsonPrimitive(false))
                put("since", JsonPrimitive(0))
            }
        )
        enqueue(payload)
    }

    fun sendSubscription(guildId: String) {
        val state = guildSubscriptions.getOrPut(guildId) { GuildSubscriptionState() }
        val payload = GatewayPayload(
            op = 14,
            d = buildJsonObject {
                put("guild_id", guildId.toLong())
                put("typing", state.typing)
                put("threads", state.threads)
                put("activities", state.activities)
                put("members", buildJsonArray { state.members.forEach { add(it.toLong()) } })
                put("channels", buildJsonObject {
                    state.channels.forEach { (chanId, ranges) ->
                        put(chanId, buildJsonArray {
                            ranges.forEach { range ->
                                add(buildJsonArray { add(range[0]); add(range[1]) })
                            }
                        })
                    }
                })
                put("thread_member_lists", buildJsonArray { state.threadMemberLists.forEach { add(it.toLong()) } })
            }
        )
        enqueueLazy(guildId, payload)
    }

    fun sendVoiceStateUpdate(guildId: String?, channelId: String?, selfMute: Boolean = false, selfDeaf: Boolean = false, selfVideo: Boolean = false) {
        val payload = GatewayPayload(
            op = 4,
            d = buildJsonObject {
                put("guild_id", guildId?.toLongOrNull())
                put("channel_id", channelId?.toLongOrNull())
                put("self_mute", JsonPrimitive(selfMute))
                put("self_deaf", JsonPrimitive(selfDeaf))
                put("self_video", JsonPrimitive(selfVideo))
            }
        )
        enqueue(payload)
    }

    fun sendLazyRequest(guildId: String, channelId: String, listId: String, ranges: List<List<Int>>, isThread: Boolean = false) {
        val state = guildSubscriptions.getOrPut(guildId) { GuildSubscriptionState() }

        state.updateChannel(channelId, listId, ranges, isThread)

        val payload = GatewayPayload(
            op = 14,
            d = buildJsonObject {
                put("guild_id", guildId.toLong())
                put("typing", state.typing)
                put("threads", state.threads)
                put("activities", state.activities)
                put("members", buildJsonArray { state.members.forEach { add(it.toLong()) } })
                put("thread_member_lists", buildJsonArray { state.threadMemberLists.forEach { add(it.toLong()) } })
                put("channels", buildJsonObject {
                    state.channels.forEach { (lid, r) ->
                        put(lid, buildJsonArray {
                            r.forEach { range ->
                                add(buildJsonArray {
                                    add(range[0])
                                    add(range[1])
                                })
                            }
                        })
                    }
                })
            }
        )
        enqueueLazy(guildId, payload)
    }
}
