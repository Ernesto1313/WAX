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

@Singleton
class MediaSessionRepository @Inject constructor() {

    private val _state = MutableStateFlow(MediaSessionState())
    val state: StateFlow<MediaSessionState> = _state.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    // One-shot signal fired when the user unlocks the phone so LockScreenActivity can finish.
    private val _dismissLockScreen = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val dismissLockScreen: SharedFlow<Unit> = _dismissLockScreen.asSharedFlow()

    fun signalDismissLockScreen() {
        _dismissLockScreen.tryEmit(Unit)
    }

    @Volatile private var activeController: MediaController? = null

    fun update(trackTitle: String?, artistName: String?, albumName: String?, isPlaying: Boolean) {
        // Preserve coverUrl — it is set independently via setAlbumCover() and must not be wiped
        // by track/playback updates from MediaNotificationListenerService.
        _state.update { it.copy(trackTitle = trackTitle, artistName = artistName, albumName = albumName, isPlaying = isPlaying) }
    }

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

    fun sendPlayPause() {
        val ctrl = activeController ?: return
        if (_state.value.isPlaying) ctrl.transportControls.pause()
        else ctrl.transportControls.play()
    }

    fun sendSkipToNext() { activeController?.transportControls?.skipToNext() }

    fun sendSkipToPrevious() { activeController?.transportControls?.skipToPrevious() }

    /** Called by MainViewModel when the weekly album is loaded. */
    fun setAlbumCover(url: String) {
        _state.update { it.copy(coverUrl = url) }
    }

    /** Called by MainViewModel after Palette extraction so the lock screen can use the same colors. */
    fun setVinylColors(dominant: Int, vibrant: Int) {
        _state.update { it.copy(vinylDominantColor = dominant, vinylVibrantColor = vibrant) }
    }
}
