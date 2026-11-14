package dev.trywonuf1.echoframe.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Receives media notifications and reads active MediaSessions.
 * This is the Android-supported path for third-party media metadata when the user enables
 * Notification Listener access. It does not read another app's private storage or audio.
 */
class MediaMonitorService : NotificationListenerService() {
    private val callbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var sessionManager: MediaSessionManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val component by lazy { ComponentName(this, MediaMonitorService::class.java) }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachControllers(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        runCatching {
            sessionManager?.addOnActiveSessionsChangedListener(sessionListener, component, mainHandler)
            refreshControllers()
        }.onFailure { Log.w(TAG, "Unable to observe media sessions", it) }
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionListener) }
        detachControllers()
        PlaybackBus.publish(null)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshControllers()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshControllers()
    }

    private fun refreshControllers() {
        runCatching {
            val active = sessionManager?.getActiveSessions(component).orEmpty()
            attachControllers(active)
        }.onFailure { Log.d(TAG, "Media session refresh skipped: ${it.message}") }
    }

    private fun attachControllers(controllers: List<MediaController>) {
        val wanted = controllers.filter { controller ->
            controller.packageName in SUPPORTED_PACKAGES || hasUsableMetadata(controller)
        }
        callbacks.keys.toList().forEach { old ->
            if (wanted.none { it.sessionToken == old.sessionToken }) {
                callbacks.remove(old)?.let { callback -> runCatching { old.unregisterCallback(callback) } }
            }
        }
        wanted.forEach { controller ->
            if (callbacks.keys.none { it.sessionToken == controller.sessionToken }) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publishBestSession()
                    override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = publishBestSession()
                    override fun onSessionDestroyed() = refreshControllers()
                }
                callbacks[controller] = callback
                runCatching { controller.registerCallback(callback, mainHandler) }
            }
        }
        publishBestSession()
    }

    private fun publishBestSession() {
        val controller = callbacks.keys
            .sortedByDescending { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            .firstOrNull { hasUsableMetadata(it) }
        if (controller == null) {
            PlaybackBus.publish(null)
            return
        }

        val metadata = controller.metadata
        val state = controller.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
        val position = state?.position ?: 0L
        val speed = state?.playbackSpeed ?: 1f

        if (title.isBlank() || artist.isBlank()) {
            PlaybackBus.publish(null)
            return
        }
        PlaybackBus.publish(
            PlaybackSnapshot(
                packageName = controller.packageName,
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                positionMs = position,
                speed = speed,
                isPlaying = playing
            )
        )
    }

    private fun hasUsableMetadata(controller: MediaController): Boolean {
        val metadata = controller.metadata ?: return false
        return !metadata.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank()
    }

    private fun detachControllers() {
        callbacks.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        callbacks.clear()
    }

    companion object {
        private const val TAG = "EchoMediaMonitor"
        private val SUPPORTED_PACKAGES = setOf(
            "com.spotify.music",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music"
        )
    }
}
