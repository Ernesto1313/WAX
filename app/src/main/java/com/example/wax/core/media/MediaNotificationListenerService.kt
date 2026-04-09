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
import com.example.wax.core.preferences.UserPreferencesRepository
import com.example.wax.presentation.lockscreen.LockScreenActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    @Inject lateinit var mediaSessionRepository: MediaSessionRepository
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionManager: MediaSessionManager? = null
    private val registeredControllers = mutableListOf<MediaController>()
    private var receiverRegistered = false

    // ── Screen-event receiver ─────────────────────────────────────────────────
    // ACTION_SCREEN_OFF  → screen turned off while playing → show lock screen overlay
    // ACTION_USER_PRESENT → user swiped past keyguard       → dismiss the overlay

    private val screenEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (mediaSessionRepository.state.value.isPlaying) {
                        serviceScope.launch {
                            val theme = userPreferencesRepository.readLockScreenTheme()
                            withContext(Dispatchers.Main) {
                                startActivity(
                                    Intent(this@MediaNotificationListenerService, LockScreenActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                                        .putExtra("extra_theme", theme.key)
                                )
                            }
                        }
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

    private val sessionChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { _ -> refreshActiveSessions() }

    // ── MediaController.Callback ──────────────────────────────────────────────
    // Attached only to the Spotify controller; callbacks are therefore always
    // Spotify-sourced.

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

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(screenEventReceiver)
            receiverRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        val sm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager = sm
        sm.addOnActiveSessionsChangedListener(
            sessionChangedListener,
            ComponentName(this, MediaNotificationListenerService::class.java)
        )
        refreshActiveSessions()
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionChangedListener)
        sessionManager = null
        clearControllers()
        mediaSessionRepository.setActiveController(null)
    }

    // ── Session selection ─────────────────────────────────────────────────────

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

    private fun clearControllers() {
        registeredControllers.forEach { it.unregisterCallback(mediaCallback) }
        registeredControllers.clear()
    }

    companion object {
        private const val TAG             = "MediaNotifListener"
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}
