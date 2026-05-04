package com.example.wax.core.media

import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Immutable snapshot of the current Spotify media session state, shared between
 * [MediaNotificationListenerService], [MediaPlaybackService], and the UI layer.
 *
 * @property trackTitle       Title of the currently playing track, or `null` if no session is active.
 * @property artistName       Primary artist name of the current track, or `null`.
 * @property albumName        Album name of the current track as reported by the notification; used
 *                            by [com.example.wax.presentation.main.MainViewModel] to detect album
 *                            changes and trigger a new Spotify API fetch.
 * @property isPlaying        Whether Spotify is actively playing (not paused or stopped).
 * @property coverUrl         URL of the album artwork resolved by [MainViewModel]; stored here so
 *                            [com.example.wax.presentation.lockscreen.LockScreenActivity] can read
 *                            it without going through the ViewModel.
 * @property vinylDominantColor ARGB integer of the dominant color extracted from the album art via
 *                            Palette; stored here so the lock screen can use the same colors as the
 *                            main screen without a separate extraction pass. Defaults to a dark grey.
 * @property vinylVibrantColor ARGB integer of the vibrant color extracted from the album art.
 *                            Defaults to a slightly lighter dark grey.
 */
data class MediaSessionState(
    val trackTitle: String? = null,
    val artistName: String? = null,
    val albumName: String? = null,
    val isPlaying: Boolean = false,
    val coverUrl: String = "",
    // ARGB ints extracted by Palette — stored here so LockScreenActivity can read them
    // without going through MainViewModel. Defaults match the fallback dark greys in MainUiState.
    val vinylDominantColor: Int = 0xFF1A1A1A.toInt(),
    val vinylVibrantColor: Int = 0xFF2A2A2A.toInt()
)

/**
 * Singleton in-process bus that carries Spotify media session state between three independent
 * components that cannot easily share a ViewModel:
 *
 * - **[MediaNotificationListenerService]** — writes track metadata and playback state via [update]
 *   and [setActiveController] whenever Spotify posts a notification.
 * - **[MediaPlaybackService]** — reads [state] to render the media notification and update the
 *   [android.support.v4.media.session.MediaSessionCompat] metadata.
 * - **[com.example.wax.presentation.main.MainViewModel]** — collects [state] to keep the UI in
 *   sync; writes [coverUrl] and Palette colors after image processing.
 * - **[com.example.wax.presentation.lockscreen.LockScreenActivity]** — collects [state] for the
 *   fullscreen lock-screen player and listens on [dismissLockScreen] for the unlock signal.
 *
 * All fields are exposed as [StateFlow] or [SharedFlow] so collectors are automatically updated
 * when the listener service pushes new data. The `@Singleton` scope ensures every component
 * reads from and writes to the same instance.
 */
@Singleton
class MediaSessionRepository @Inject constructor() {

    /** Backing mutable state flow; updated atomically via [MutableStateFlow.update]. */
    private val _state = MutableStateFlow(MediaSessionState())

    /**
     * Public read-only stream of the current media session state. Emits a new [MediaSessionState]
     * every time any field changes (track title, playback state, cover URL, etc.).
     */
    val state: StateFlow<MediaSessionState> = _state.asStateFlow()

    /** Backing flag for whether a Spotify [MediaController] is currently active. */
    private val _isSessionActive = MutableStateFlow(false)

    /**
     * `true` when [MediaNotificationListenerService] has registered a live Spotify
     * [MediaController]; `false` after it disconnects or when no Spotify session exists.
     * Consumed by [com.example.wax.presentation.main.MainViewModel] to decide whether the
     * vinyl platter animation should run in idle (decorative) or active (live) mode.
     */
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    /**
     * One-shot signal fired when the user dismisses the keyguard (unlocks the device).
     * `extraBufferCapacity = 1` ensures the emission is not dropped if no collector is
     * active at the exact moment it is fired (e.g., if the Activity is briefly in the background).
     * Consumed by [com.example.wax.presentation.lockscreen.LockScreenActivity] to call [finish].
     */
    // One-shot signal fired when the user unlocks the phone so LockScreenActivity can finish.
    private val _dismissLockScreen = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Public shared flow that emits [Unit] once when the device is unlocked.
     * [com.example.wax.presentation.lockscreen.LockScreenActivity] collects this inside
     * `repeatOnLifecycle(STARTED)` so the lock-screen overlay is dismissed cleanly.
     */
    val dismissLockScreen: SharedFlow<Unit> = _dismissLockScreen.asSharedFlow()

    /**
     * Emits on [dismissLockScreen] to signal that the keyguard has been dismissed.
     * Called by [MediaNotificationListenerService] when it receives [android.content.Intent.ACTION_USER_PRESENT].
     * Uses [MutableSharedFlow.tryEmit] (non-suspending) because the caller is a [android.content.BroadcastReceiver].
     */
    fun signalDismissLockScreen() {
        _dismissLockScreen.tryEmit(Unit)
    }

    /**
     * The active Spotify [MediaController] used to send transport commands.
     * `@Volatile` ensures visibility across threads — the listener service writes from its
     * own thread, while [sendPlayPause], [sendSkipToNext], and [sendSkipToPrevious] may be
     * called from the main thread via [MediaPlaybackService].
     */
    @Volatile private var activeController: MediaController? = null

    /**
     * Updates track metadata and playback state from a [android.media.MediaMetadata] or
     * [android.media.session.PlaybackState] callback.
     *
     * **Why [coverUrl] is NOT updated here**: The cover URL is determined asynchronously by
     * [com.example.wax.presentation.main.MainViewModel] after it fetches the full album from the
     * Spotify API and resolves the cached/network URL. Updating it here (from the media session
     * metadata) would overwrite the cached/resolved URL with the raw CDN URL every time
     * the playback state changes.
     *
     * @param trackTitle The track title from [android.media.MediaMetadata.METADATA_KEY_TITLE].
     * @param artistName The artist from [android.media.MediaMetadata.METADATA_KEY_ARTIST].
     * @param albumName  The album name from [android.media.MediaMetadata.METADATA_KEY_ALBUM].
     * @param isPlaying  Whether [android.media.session.PlaybackState.STATE_PLAYING] is set.
     */
    fun update(trackTitle: String?, artistName: String?, albumName: String?, isPlaying: Boolean) {
        // Preserve coverUrl — it is set independently via setAlbumCover() and must not be wiped
        // by track/playback updates from MediaNotificationListenerService.
        _state.update { it.copy(trackTitle = trackTitle, artistName = artistName, albumName = albumName, isPlaying = isPlaying) }
    }

    /**
     * Called by [MediaNotificationListenerService] when the set of active Spotify sessions changes.
     *
     * When [controller] is non-null, it is stored so transport commands can be forwarded.
     * When `null` (Spotify session ended), [_state] is reset to defaults — preserving only
     * [MediaSessionState.coverUrl] so the album art does not flash away before the ViewModel
     * has had a chance to handle the session change.
     *
     * @param controller The active Spotify [MediaController], or `null` if no session exists.
     */
    fun setActiveController(controller: MediaController?) {
        activeController = controller
        _isSessionActive.value = controller != null
        // When no controller is available reset playback state so stale track/play
        // info is not shown. coverUrl is preserved — it is set independently by
        // MainViewModel and must survive session changes.
        if (controller == null) {
            _state.update { MediaSessionState(coverUrl = it.coverUrl) }
        }
    }

    /**
     * Sends a play or pause command to the active Spotify session based on the current
     * [MediaSessionState.isPlaying] value. No-ops silently when there is no active controller.
     */
    fun sendPlayPause() {
        val ctrl = activeController ?: return
        if (_state.value.isPlaying) ctrl.transportControls.pause()
        else ctrl.transportControls.play()
    }

    /**
     * Forwards a skip-to-next command to the active Spotify session.
     * No-ops silently when there is no active controller.
     */
    fun sendSkipToNext() { activeController?.transportControls?.skipToNext() }

    /**
     * Forwards a skip-to-previous command to the active Spotify session.
     * No-ops silently when there is no active controller.
     */
    fun sendSkipToPrevious() { activeController?.transportControls?.skipToPrevious() }

    /**
     * Sets the album artwork URL on the current state.
     * Called by [com.example.wax.presentation.main.MainViewModel] after the full album is loaded
     * and the artwork URL is resolved (cache hit → `file://` URI, miss → CDN URL).
     *
     * @param url The resolved artwork URL to store for [MediaPlaybackService] and the lock screen.
     */
    fun setAlbumCover(url: String) {
        _state.update { it.copy(coverUrl = url) }
    }

    /**
     * Stores the dominant and vibrant ARGB colors extracted from the album art via Palette.
     * Called by [com.example.wax.presentation.main.MainViewModel] after extraction so that
     * [com.example.wax.presentation.lockscreen.LockScreenActivity] can apply the same tinted
     * background without running its own Palette pass.
     *
     * @param dominant ARGB integer of the Palette dominant color.
     * @param vibrant  ARGB integer of the Palette vibrant color.
     */
    fun setVinylColors(dominant: Int, vibrant: Int) {
        _state.update { it.copy(vinylDominantColor = dominant, vinylVibrantColor = vibrant) }
    }
}
