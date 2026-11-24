package me.lampu.lampcord.shared.state

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import me.lampu.lampcord.shared.api.AuthApi
import me.lampu.lampcord.shared.gateway.GatewayManager
import me.lampu.lampcord.shared.gateway.VoiceGatewayManager
import me.lampu.lampcord.shared.model.LoginRequest
import me.lampu.lampcord.shared.model.LoginResponse
import me.lampu.lampcord.shared.model.MFALoginRequest
import me.lampu.lampcord.shared.notifications.MessageNotifier
import me.lampu.lampcord.shared.notifications.PushTokenRegistrar
import me.lampu.lampcord.shared.settings.Settings
import me.lampu.lampcord.shared.update.UpdateManager

class SessionManager(
    val gatewayManager: GatewayManager,
    val voiceGatewayManager: VoiceGatewayManager,
    val authApi: AuthApi,
    val navigationStore: NavigationStore,
    val tokenStore: TokenStore,
    val userStore: UserStore,
    val gatewayHandler: GatewayHandler,
    val entityStore: EntityStore,
    val readStateStore: ReadStateStore,
    val userGuildSettingsStore: UserGuildSettingsStore,
    val presenceStore: PresenceStore,
    val relationshipStore: RelationshipStore,
    val guildStore: GuildStore,
    val memberListStore: MemberListStore,
    val messageStore: MessageStore,
    val typingStore: TypingStore,
    val commandStore: CommandStore,
    val voiceStore: VoiceStore,
    val updateManager: UpdateManager,
    val themeStore: ThemeStore,
    val pushTokenRegistrar: PushTokenRegistrar? = null,
    private val notifier: MessageNotifier? = null
) {
    private val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Main +
            CoroutineExceptionHandler { _, throwable ->
                me.lampu.lampcord.shared.utils.Logging.e(
                    tag = "SessionManager",
                    message = "Uncaught exception in session scope",
                    throwable = throwable
                )
            }
    )

    // READY and the startup snapshot both rebuild every store; never let them interleave.
    private val readyLock = Mutex()
    private val snapshotJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    init {
        // Collected on Default: events used to be received on Main and hopped off one by one.
        scope.launch(Dispatchers.Default) {

            gatewayManager.events.collect { payload ->
                try {
                    if (payload.op == -1 && payload.t == "AUTH_FAILED") {
                        withContext(Dispatchers.Main) { logout(Settings.shared.discordToken) }
                    } else if (payload.t == "READY") {
                        readyLock.withLock { gatewayHandler.handleGatewayEvent(payload) }
                    } else {
                        gatewayHandler.handleGatewayEvent(payload)
                    }
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    me.lampu.lampcord.shared.utils.Logging.e(
                        tag = "SessionManager",
                        message = "Failed to handle gateway event ${payload.op}:${payload.t}",
                        throwable = e
                    )
                }
            }
        }

        scope.launch {
            authApi.rest.unauthorizedEvents.collect {
                logout(Settings.shared.discordToken)
            }
        }

        val savedToken = Settings.shared.discordToken
        if (savedToken.isNotBlank()) connect(savedToken)
    }

    fun connect(token: String) {
        authApi.setToken(token)
        Settings.shared.discordToken = token
        navigationStore.isConnecting = true
        gatewayHandler.liveReadyApplied = false
        gatewayManager.connect(token)
        // Draw last session's servers and DMs while the gateway connects.
        scope.launch(Dispatchers.Default) {
            val data = StartupSnapshot.load(snapshotJson, token) ?: return@launch
            readyLock.withLock {
                if (Settings.shared.discordToken == token) gatewayHandler.applySnapshot(data)
            }
        }
        pushTokenRegistrar?.register()
    }

    fun disconnect() {
        notifier?.dismissAllNotifications()
        voiceStore.disconnectFromVoice()
        gatewayManager.disconnect()
        navigationStore.isConnected = false
        navigationStore.isConnecting = false
        authApi.setToken(null)
        Settings.shared.discordToken = ""
        clearAllStores()
    }

    fun shutdown() {
        disconnect()
        gatewayManager.close()
        voiceGatewayManager.close()
        updateManager.close()
        themeStore.close()
        authApi.rest.closeClient()
        scope.cancel()
    }

    fun switchAccount(token: String) {
        disconnect()
        connect(token)
    }

    fun logout(token: String) {
        val cleanToken = token.trim()
        tokenStore.removeAccount(cleanToken)
        if (Settings.shared.discordToken.trim() == cleanToken) StartupSnapshot.clear()
        if (Settings.shared.discordToken.trim() == cleanToken) {
            disconnect()
        }
    }

    private fun clearAllStores() {
        navigationStore.clear()
        entityStore.clear()
        readStateStore.clear()
        userGuildSettingsStore.clear()
        presenceStore.clear()
        userStore.clear()
        relationshipStore.clear()
        guildStore.clear()
        memberListStore.clear()
        messageStore.clear()
        typingStore.clear()
        commandStore.clear()
        voiceStore.clear()
    }

    suspend fun login(email: String, pass: String): LoginResponse? {
        val fingerprint = authApi.getFingerprint() ?: ""
        return authApi.login(LoginRequest(email, pass), fingerprint)
    }

    suspend fun verifyMFA(ticket: String, code: String, type: String): LoginResponse? {
        val fingerprint = authApi.getFingerprint() ?: ""
        val res = authApi.loginMFA(MFALoginRequest(ticket, code), fingerprint, type)
        if (res?.token != null) {
            connect(res.token)
        }
        return res
    }
}
