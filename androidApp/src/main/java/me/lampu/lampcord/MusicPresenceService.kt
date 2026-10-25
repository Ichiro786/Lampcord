package me.lampu.lampcord

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import me.lampu.lampcord.shared.gateway.GatewayManager
import me.lampu.lampcord.shared.model.Activity
import me.lampu.lampcord.shared.model.ActivityAssets
import me.lampu.lampcord.shared.model.ActivityTimestamps
import me.lampu.lampcord.shared.settings.Settings
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MusicPresenceService : NotificationListenerService(), KoinComponent {

    private val gatewayManager: GatewayManager by inject()
    private lateinit var mediaSessionManager: MediaSessionManager
    
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)
        mediaSessionManager.addOnActiveSessionsChangedListener(
            { controllers -> updateControllers(controllers) },
            ComponentName(this, MusicPresenceService::class.java)
        )
        updateControllers(mediaSessionManager.getActiveSessions(ComponentName(this, MusicPresenceService::class.java)))
    }

    override fun onDestroy() {
        super.onDestroy()
        controllerCallbacks.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        controllerCallbacks.clear()
        if (lastActivity != null) {
            lastActivity = null
            gatewayManager.setAppActivity(SOURCE, null)
        }
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        controllerCallbacks.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        controllerCallbacks.clear()

        controllers?.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    syncPresence()
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    syncPresence()
                }
            }
            controller.registerCallback(callback)
            controllerCallbacks[controller] = callback
        }
        syncPresence()
    }

    private var lastActivity: Activity? = null

    private fun syncPresence() {
        val enabled = Settings.shared.discordToken.isNotBlank() && Settings.shared.musicPresenceEnabled
        val activeController = if (enabled) controllerCallbacks.keys.find {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        } else null

        val metadata = activeController?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (activeController == null || metadata == null || title == null) {
            // Paused, stopped or disabled: clear "Listening to" instead of leaving it stuck.
            if (lastActivity != null) {
                lastActivity = null
                gatewayManager.setAppActivity(SOURCE, null)
            }
            return
        }

        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val position = activeController.playbackState?.position ?: 0L

        val appName = when (activeController.packageName) {
            "com.spotify.music" -> "Spotify"
            "com.google.android.apps.youtube.music" -> "YouTube Music"
            "com.apple.android.music" -> "Apple Music"
            else -> activeController.packageName ?: "Music"
        }

        val now = System.currentTimeMillis()
        val start = now - position
        val previous = lastActivity
        // Playback callbacks fire constantly with a few ms of drift; reuse the previous start
        // time for the same track so identical presences are deduplicated downstream.
        val stableStart = previous?.timestamps?.start
            ?.takeIf { previous.details == title && previous.state == artist && kotlin.math.abs(it - start) < DRIFT_TOLERANCE_MS }
            ?: start

        val activity = Activity(
            name = appName,
            type = 2, // Listening
            details = title,
            state = artist,
            assets = album?.let { ActivityAssets(large_text = it) },
            timestamps = if (duration > 0) {
                ActivityTimestamps(start = stableStart, end = stableStart + duration)
            } else null
        )

        if (activity == previous) return
        lastActivity = activity
        gatewayManager.setAppActivity(SOURCE, activity)
    }

    private companion object {
        const val SOURCE = "music"
        const val DRIFT_TOLERANCE_MS = 3_000L
    }
}
