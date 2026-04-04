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
import com.example.wax.MainActivity
import com.example.wax.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that shows a media notification mirroring Spotify playback state
 * from MediaSessionRepository. Uses MediaSessionCompat + NotificationCompat.MediaStyle
 * so it works with all Media3/MediaCompat versions (no SimpleBasePlayer required).
 *
 * Audio is never played here — transport commands are forwarded to Spotify via
 * MediaSessionRepository.
 */
@AndroidEntryPoint
class MediaPlaybackService : Service() {

    @Inject lateinit var mediaSessionRepository: MediaSessionRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManagerCompat

    private var currentState = MediaSessionState()
    private var artworkBitmap: Bitmap? = null

    companion object {
        private const val CHANNEL_ID = "wax_media_playback"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY_PAUSE = "com.example.wax.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.wax.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.wax.ACTION_PREVIOUS"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = NotificationManagerCompat.from(this)
        setupMediaSession()
        collectState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> mediaSessionRepository.sendPlayPause()
            ACTION_NEXT      -> mediaSessionRepository.sendSkipToNext()
            ACTION_PREVIOUS  -> mediaSessionRepository.sendSkipToPrevious()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep service alive while track is playing
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession.release()
        super.onDestroy()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

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

    private fun updateNotification(state: MediaSessionState) {
        val hasTrack = !state.trackTitle.isNullOrEmpty()
        if (!hasTrack && !state.isPlaying) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            return
        }

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
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

        val notification = builder.build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun pendingIntentFor(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, MediaPlaybackService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // ── Artwork loading ───────────────────────────────────────────────────────

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
