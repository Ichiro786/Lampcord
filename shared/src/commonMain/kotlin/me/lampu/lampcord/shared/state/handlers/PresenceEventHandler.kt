package me.lampu.lampcord.shared.state.handlers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import me.lampu.lampcord.shared.model.PresenceUpdate
import me.lampu.lampcord.shared.model.Session
import me.lampu.lampcord.shared.state.GatewayEventHandler
import me.lampu.lampcord.shared.state.PresenceStore
import me.lampu.lampcord.shared.state.UserStore

class PresenceEventHandler(
    private val json: Json,
    private val presenceStore: PresenceStore,
    private val userStore: UserStore
) : GatewayEventHandler {
    override val supportedEvents = setOf("PRESENCE_UPDATE", "SESSIONS_REPLACE")

    override fun handleEvent(type: String, data: JsonElement?) {
        if (data == null) return
        when (type) {
            "PRESENCE_UPDATE" -> handlePresenceUpdate(data)
            "SESSIONS_REPLACE" -> handleSessionsReplace(data)
        }
    }

    // PRESENCE_UPDATE arrives in floods on big accounts (dozens per second). Applying each one
    // on the main thread copied the whole presence and user maps and recomposed every list that
    // shows a status dot, once per event. Buffer them, decode off the main thread, and apply the
    // batch in one store update a few times a second instead.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pendingPresences = ArrayList<JsonElement>()
    private var flushJob: Job? = null

    private fun handlePresenceUpdate(data: JsonElement) {
        // handleEvent runs on the main thread, as does the flush below, so no lock is needed.
        pendingPresences.add(data)
        if (flushJob != null) return
        flushJob = scope.launch {
            delay(PRESENCE_BATCH_WINDOW_MS)
            val batch = ArrayList(pendingPresences)
            pendingPresences.clear()
            flushJob = null
            val decoded = withContext(Dispatchers.Default) {
                batch.mapNotNull { element ->
                    try {
                        json.decodeFromJsonElement<PresenceUpdate>(element)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            if (decoded.isEmpty()) return@launch
            presenceStore.applyPresences(decoded)
            val users = decoded.mapNotNull { it.user }
            if (users.isNotEmpty()) userStore.handleUserUpdates(users.map { it to null })
        }
    }

    private fun handleSessionsReplace(data: JsonElement) {
        try {
            val sessions = json.decodeFromJsonElement<List<Session>>(data)
            val userId = userStore.currentUser.value?.id ?: return
            presenceStore.handleSessions(userId, sessions)
        } catch (e: Exception) { }
    }

    private companion object {
        const val PRESENCE_BATCH_WINDOW_MS = 250L
    }
}
