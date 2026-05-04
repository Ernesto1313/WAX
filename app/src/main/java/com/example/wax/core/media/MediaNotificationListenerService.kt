package com.example.wax.core.media

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.wax.presentation.lockscreen.LockScreenActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * System-bound service that gets notification-listener access, then uses that privilege
 * to obtain the active Spotify MediaController from MediaSessionManager.
 *
 * Only Spotify (com.spotify.music) sessions are used — all other apps (SoundCloud, YouTube,
 * etc.) are explicitly ignored so stale sessions from inactive apps can never override the
 * Spotify state.
 *
 * Requires the user to enable "Notification access" for Wax in
 * Settings → Apps → Special app access → Notification access.
 */
@AndroidEntryPoint
class MediaNotificationListenerService : NotificationListenerService() {

    /** Hilt-injected repository; receives track metadata and playback state updates. */
    @Inject lateinit var mediaSessionRepository: MediaSessionRepository

    /** System service used to enumerate active media sessions and register a change listener. */
    private var sessionManager: MediaSessionManager? = null

    /**
     * All [MediaController] instances to which [mediaCallback] is currently registered.
     * Kept as a list so they can be bulk-unregistered in [clearControllers] before
     * re-subscribing to a fresh set of sessions.
     */
    private val registeredControllers = mutableListOf<MediaController>()

    /**
     * Guards against calling [unregisterReceiver] when [screenEventReceiver] was never
     * registered, which would throw an [IllegalArgumentException].
     */
    private var receiverRegistered = false

    // ── Screen-event receiver ─────────────────────────────────────────────────
    // ACTION_SCREEN_OFF  → screen turned off while playing → show lock screen overlay
    // ACTION_USER_PRESENT → user swiped past keyguard       → dismiss the overlay

    /**
     * [BroadcastReceiver] that listens for screen-off and unlock events to manage the
     * lock-screen turntable overlay.
     *
     * - [Intent.ACTION_SCREEN_OFF]: if Spotify is actively playing when the screen turns off,
     *   [LockScreenActivity] is started so the turntable appears on the lock screen immediately.
     * - [Intent.ACTION_USER_PRESENT]: fired when the user swipes past the keyguard; signals
     *   [MediaSessionRepository.signalDismissLockScreen] so [LockScreenActivity] can finish.
     *
     * Registered in [onCreate] and unregistered in [onDestroy] using [receiverRegistered] as
     * a guard against double-unregistration.
     */
    private val screenEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (mediaSessionRepository.state.value.isPlaying) {
                        startActivity(
                            Intent(this@MediaNotificationListenerService, LockScreenActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    mediaSessionRepository.signalDismissLockScreen()
                }
            }
        }
    }

    // ── Session-change listener ───────────────────────────────────────────────
    // Fired by the OS whenever the set of active MediaSessions changes (app opened,
    // closed, moved to background, etc.). Re-runs the Spotify filter each time.

    /**
     * Listener registered with [MediaSessionManager] that re-evaluates the set of active
     * Spotify sessions whenever the OS reports a change (e.g., Spotify is opened, closed, or
     * moved to the background). Delegates to [refreshActiveSessions] on every invocation.
     */
    private val sessionChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { _ -> refreshActiveSessions() }

    // ── MediaController.Callback ──────────────────────────────────────────────
    // Attached only to the Spotify controller; callbacks are therefore always
    // Spotify-sourced.

    /**
     * Callback registered on the active Spotify [MediaController].
     *
     * - [onMetadataChanged]: called when the track changes; extracts title, artist, and album
     *   from the new [android.media.MediaMetadata] and forwards them to [MediaSessionRepository.update].
     * - [onPlaybackStateChanged]: called when Spotify pauses, plays, or stops; updates only the
     *   [MediaSessionRepository] playback flag while preserving the previously stored metadata.
     *
     * Unregistered via [clearControllers] when sessions change or when the listener disconnects.
     */
    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val title     = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist    = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val album     = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val isPlaying = registeredControllers.any {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            }
            mediaSessionRepository.update(title, artist, album, isPlaying)
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
            val current   = mediaSessionRepository.state.value
            mediaSessionRepository.update(current.trackTitle, current.artistName, current.albumName, isPlaying)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Registers [screenEventReceiver] to listen for [Intent.ACTION_SCREEN_OFF] and
     * [Intent.ACTION_USER_PRESENT] broadcasts. [ContextCompat.RECEIVER_NOT_EXPORTED]
     * restricts delivery to system broadcasts only, preventing other apps from spoofing
     * these intents.
     */
    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            screenEventReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    /**
     * Unregisters [screenEventReceiver] if it was previously registered, guarded by
     * [receiverRegistered] to prevent a crash from a double-unregister.
     */
    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(screenEventReceiver)
            receiverRegistered = false
        }
        super.onDestroy()
    }

    /**
     * Called by the OS after the user grants notification listener access and the service
     * is bound. Registers [sessionChangedListener] with [MediaSessionManager] and performs
     * an initial [refreshActiveSessions] call so the repository is seeded with the current
     * Spotify state immediately, without waiting for the next session-change event.
     */
    override fun onListenerConnected() {
        val sm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager = sm
        sm.addOnActiveSessionsChangedListener(
            sessionChangedListener,
            ComponentName(this, MediaNotificationListenerService::class.java)
        )
        refreshActiveSessions()
    }

    /**
     * Called by the OS when the notification listener is revoked (e.g., the user disables
     * notification access in Settings). Removes [sessionChangedListener], clears all
     * registered [MediaController] callbacks, and resets the active controller in the
     * repository so the UI reflects the disconnected state.
     */
    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionChangedListener)
        sessionManager = null
        clearControllers()
        mediaSessionRepository.setActiveController(null)
    }

    // ── Session selection ─────────────────────────────────────────────────────

    /**
     * Queries [MediaSessionManager] for the current list of active sessions, selects the
     * first one belonging to Spotify ([SPOTIFY_PACKAGE]), and attaches [mediaCallback] to it.
     *
     * If no Spotify session is found, [MediaSessionRepository.setActiveController] is called
     * with `null` to reset the repository state. The initial metadata snapshot is read
     * immediately after connecting so the UI reflects what is already playing without
     * waiting for the next [android.media.MediaController.Callback] event.
     */
    private fun refreshActiveSessions() {
        clearControllers()
        try {
            val sessions = sessionManager?.getActiveSessions(
                ComponentName(this, MediaNotificationListenerService::class.java)
            ) ?: return

            val spotifyController = sessions.firstOrNull { it.packageName == SPOTIFY_PACKAGE }

            if (spotifyController == null) {
                mediaSessionRepository.setActiveController(null)
                return
            }

            spotifyController.registerCallback(mediaCallback)
            registeredControllers.add(spotifyController)

            // Seed initial state so UI reflects Spotify immediately on connect
            val meta      = spotifyController.metadata
            val title     = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist    = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            val album     = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            val isPlaying = spotifyController.playbackState?.state == PlaybackState.STATE_PLAYING
            mediaSessionRepository.update(title, artist, album, isPlaying)
            mediaSessionRepository.setActiveController(spotifyController)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot access media sessions — notification listener not enabled: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Unregisters [mediaCallback] from all controllers in [registeredControllers] and
     * clears the list. Called before re-subscribing to a new set of sessions so that
     * stale callbacks from previous sessions are never left dangling.
     */
    private fun clearControllers() {
        registeredControllers.forEach { it.unregisterCallback(mediaCallback) }
        registeredControllers.clear()
    }

    companion object {
        /** Log tag used for warning messages when session access is denied. */
        private const val TAG             = "MediaNotifListener"

        /** Package name of the Spotify app; used to filter media sessions to Spotify only. */
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}
