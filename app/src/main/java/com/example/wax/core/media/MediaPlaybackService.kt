package com.example.wax.core.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.wax.R
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
 * Foreground service that mirrors Spotify's playback state as a persistent media notification.
 *
 * **What this service does NOT do**: It never plays audio. All media transport commands
 * (play, pause, skip) are forwarded to the active Spotify [android.media.session.MediaController]
 * via [MediaSessionRepository] — this service is purely a notification presenter and
 * [MediaSessionCompat] host.
 *
 * **Why a foreground service?**: Android kills background processes aggressively. A foreground
 * service with an ongoing notification survives even when the app is swiped away. Without it,
 * the media notification and [MediaSessionCompat] would disappear as soon as the app is removed
 * from the recents screen, breaking lock-screen and Bluetooth head-unit controls.
 *
 * **[MediaSessionCompat] + [NotificationCompat.MediaStyle]**: This combination is used instead of
 * Media3 / `SimpleBasePlayer` because it is compatible with all API levels supported by the app
 * and does not require the Media3 foreground service type manifest entry. The `MediaSessionCompat`
 * token is embedded in the notification via [MediaStyle.setMediaSession] so the OS can wire
 * hardware media keys and Bluetooth controls directly to this service.
 *
 * **Lock-screen integration**: Tapping the notification opens [LockScreenActivity] instead of
 * [com.example.wax.MainActivity]. [LockScreenActivity] checks [android.app.KeyguardManager.isKeyguardLocked]
 * and redirects to [com.example.wax.MainActivity] if the phone is already unlocked.
 */
@AndroidEntryPoint
class MediaPlaybackService : Service() {

    /** Hilt-injected repository; provides the current [MediaSessionState] and transport controls. */
    @Inject lateinit var mediaSessionRepository: MediaSessionRepository

    /**
     * Coroutine scope for all async work in this service.
     * [SupervisorJob] ensures that a failure in one child coroutine (e.g., artwork loading)
     * does not cancel the parent scope or the state-collection coroutine.
     * [Dispatchers.Main.immediate] runs collectors on the main thread without an extra post,
     * which is safe for UI-adjacent operations like building notifications.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** [MediaSessionCompat] instance that hosts metadata and playback state for the OS. */
    private lateinit var mediaSession: MediaSessionCompat

    /** Notification manager used to post and update the persistent media notification. */
    private lateinit var notificationManager: NotificationManagerCompat

    /** Last collected state; used to detect cover URL changes and avoid redundant artwork reloads. */
    private var currentState = MediaSessionState()

    /** Most recently loaded artwork bitmap; null before the first track loads or on error. */
    private var artworkBitmap: Bitmap? = null

    companion object {
        /** Notification channel ID for the persistent media playback notification. */
        private const val CHANNEL_ID = "wax_media_playback"

        /** Stable notification ID so the foreground notification is always updated in place. */
        private const val NOTIFICATION_ID = 1001

        /** Intent action sent by the play/pause notification action button and [MediaSessionCompat.Callback]. */
        const val ACTION_PLAY_PAUSE = "com.example.wax.ACTION_PLAY_PAUSE"

        /** Intent action sent by the skip-next notification action button. */
        const val ACTION_NEXT = "com.example.wax.ACTION_NEXT"

        /** Intent action sent by the skip-previous notification action button. */
        const val ACTION_PREVIOUS = "com.example.wax.ACTION_PREVIOUS"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Creates the notification channel, initializes the [MediaSessionCompat], and begins
     * collecting [MediaSessionRepository.state] to keep the notification in sync.
     */
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = NotificationManagerCompat.from(this)
        setupMediaSession()
        collectState()
    }

    /**
     * Handles transport command intents fired by the notification action buttons.
     *
     * Returns [START_STICKY] so the OS restarts the service if it is killed while music
     * is playing (e.g., under memory pressure) — the service recreates itself and re-attaches
     * to the Spotify media session via [MediaSessionRepository].
     *
     * @param intent Intent carrying one of [ACTION_PLAY_PAUSE], [ACTION_NEXT], or [ACTION_PREVIOUS].
     * @param flags  Delivery flags (not used).
     * @param startId Unique ID for this start request (not used).
     * @return [START_STICKY] so the service is restarted if killed.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> mediaSessionRepository.sendPlayPause()
            ACTION_NEXT      -> mediaSessionRepository.sendSkipToNext()
            ACTION_PREVIOUS  -> mediaSessionRepository.sendSkipToPrevious()
        }
        return START_STICKY
    }

    /**
     * This service is not designed to be bound; returns `null` to indicate binding is unsupported.
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the user removes the app from the recents screen.
     * Intentionally left empty to keep the service alive while a track is playing — the service
     * should only be destroyed when playback actually stops.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep service alive while track is playing
    }

    /**
     * Cancels the [serviceScope] (stopping all coroutines) and releases the [MediaSessionCompat]
     * when the service is destroyed.
     */
    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession.release()
        super.onDestroy()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    /**
     * Creates the [CHANNEL_ID] notification channel with [NotificationManager.IMPORTANCE_LOW]
     * so the media notification appears silently without a heads-up pop-up or sound.
     * [setShowBadge] is false because a media playback notification should not produce an
     * app-icon badge count.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Media Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Wax media playback controls"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Initializes the [MediaSessionCompat] with transport control callbacks.
     *
     * `FLAG_HANDLES_TRANSPORT_CONTROLS` is required on older API levels to indicate that this
     * session can respond to hardware key events. The callback methods forward all commands to
     * [MediaSessionRepository], which dispatches them to the active Spotify [android.media.session.MediaController].
     * `isActive = true` publishes the session to the OS so Bluetooth devices and the lock screen
     * can discover it.
     */
    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "WaxMediaSession").apply {
            @Suppress("DEPRECATION")
            setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()           { mediaSessionRepository.sendPlayPause() }
                override fun onPause()          { mediaSessionRepository.sendPlayPause() }
                override fun onSkipToNext()     { mediaSessionRepository.sendSkipToNext() }
                override fun onSkipToPrevious() { mediaSessionRepository.sendSkipToPrevious() }
            })
            isActive = true
        }
    }

    // ── State collection ──────────────────────────────────────────────────────

    /**
     * Launches a coroutine that collects [MediaSessionRepository.state] and reacts to changes.
     *
     * **Artwork reload**: The cover URL is compared against [currentState.coverUrl] on every
     * emission. A reload is triggered only when the URL changes, avoiding redundant [Dispatchers.IO]
     * Coil requests on every playback-state toggle.
     *
     * **Dual update**: After resolving artwork, both [updateMediaSession] and [updateNotification]
     * are called to keep the [MediaSessionCompat] metadata and the visible notification in sync.
     */
    private fun collectState() {
        serviceScope.launch {
            mediaSessionRepository.state.collect { state ->
                if (state.coverUrl != currentState.coverUrl) {
                    artworkBitmap = if (state.coverUrl.isNotEmpty()) {
                        loadArtworkBitmap(state.coverUrl)
                    } else {
                        null
                    }
                }
                currentState = state
                updateMediaSession(state)
                updateNotification(state)
            }
        }
    }

    // ── MediaSession metadata ─────────────────────────────────────────────────

    /**
     * Updates [MediaSessionCompat] with the current track metadata and playback state.
     *
     * The metadata (title, artist, album art) is consumed by the OS to populate the lock-screen
     * media controls and Bluetooth head-unit displays. The playback state with
     * [PlaybackStateCompat.ACTION_PLAY_PAUSE], [PlaybackStateCompat.ACTION_SKIP_TO_NEXT], and
     * [PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS] advertises which transport commands are supported.
     *
     * @param state The latest [MediaSessionState] from [MediaSessionRepository].
     */
    private fun updateMediaSession(state: MediaSessionState) {
        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  state.trackTitle ?: "")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, state.artistName ?: "")
        artworkBitmap?.let {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
        }
        mediaSession.setMetadata(metaBuilder.build())

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (state.isPlaying) PlaybackStateCompat.STATE_PLAYING
                else PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                1f
            )
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    // ── Notification ──────────────────────────────────────────────────────────

    /**
     * Builds and posts the foreground media notification, or stops the foreground state
     * when nothing is playing and no track title is available.
     *
     * **Stop-foreground logic**: If there is no track title AND playback is not active, the
     * notification is removed via [ServiceCompat.STOP_FOREGROUND_REMOVE] and the service
     * exits the foreground state. The service itself remains running in the background so it
     * can re-enter the foreground immediately when the next track starts.
     *
     * **Content and full-screen intents**: Both point to [LockScreenActivity] so that tapping
     * the notification opens the lock-screen player (which in turn redirects to
     * [com.example.wax.MainActivity] if the phone is already unlocked). The full-screen intent
     * with `highPriority = false` is used to show the player automatically when the notification
     * appears on the lock screen without an intrusive heads-up popup.
     *
     * **[setSilent] and [setOnlyAlertOnce]**: Silence the notification so rapid metadata updates
     * (track changes, play/pause toggles) do not produce repeated sound or vibration events.
     *
     * @param state The latest [MediaSessionState] used to populate the notification content.
     */
    private fun updateNotification(state: MediaSessionState) {
        val hasTrack = !state.trackTitle.isNullOrEmpty()
        if (!hasTrack && !state.isPlaying) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            return
        }

        // Tapping the notification always routes to LockScreenActivity.
        // LockScreenActivity will redirect to MainActivity when the phone is not locked.
        val lockScreenIntent = Intent(this, LockScreenActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            this, 0, lockScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Full-screen intent: shown automatically on lock screen when the notification posts.
        // Requires USE_FULL_SCREEN_INTENT permission (auto-granted on API <34; user-granted on 34+).
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 5, lockScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (state.isPlaying) android.R.drawable.ic_media_pause
                            else android.R.drawable.ic_media_play
        val playPauseLabel = if (state.isPlaying) "Pause" else "Play"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setLargeIcon(artworkBitmap)
            .setContentTitle(state.trackTitle ?: "")
            .setContentText(state.artistName ?: "")
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", pendingIntentFor(ACTION_PREVIOUS, 1))
            .addAction(playPauseIcon, playPauseLabel,                     pendingIntentFor(ACTION_PLAY_PAUSE, 2))
            .addAction(android.R.drawable.ic_media_next,     "Next",      pendingIntentFor(ACTION_NEXT, 3))
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setFullScreenIntent(fullScreenPendingIntent, false)

        val notification = builder.build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Creates a [PendingIntent] that fires an [Intent] at this service with the given [action].
     *
     * Used to wire the notification action buttons (Previous, Play/Pause, Next) back to
     * [onStartCommand] so tapping them dispatches the correct transport command.
     *
     * @param action      One of [ACTION_PLAY_PAUSE], [ACTION_NEXT], or [ACTION_PREVIOUS].
     * @param requestCode Unique request code to distinguish the three PendingIntents; reusing
     *                    the same code would cause the system to reuse the same PendingIntent
     *                    and the action field would be ignored.
     * @return A [PendingIntent.getService] intent targeting this service.
     */
    private fun pendingIntentFor(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, MediaPlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ── Artwork loading ───────────────────────────────────────────────────────

    /**
     * Downloads and decodes the album artwork at [url] using Coil on [Dispatchers.IO].
     *
     * `allowHardware(false)` is required because hardware-backed bitmaps cannot be read
     * as pixel data by [NotificationCompat.Builder.setLargeIcon] — the system needs a
     * software [Bitmap] that it can copy into the notification shade.
     *
     * @param url The remote or locally-cached artwork URL to load.
     * @return A software [Bitmap] at 512×512 px, or `null` on any error.
     */
    private suspend fun loadArtworkBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val result = ImageLoader(this@MediaPlaybackService).execute(
                ImageRequest.Builder(this@MediaPlaybackService)
                    .data(url)
                    .allowHardware(false)
                    .size(512, 512)
                    .build()
            ) as? SuccessResult ?: return@withContext null
            (result.drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }
}
