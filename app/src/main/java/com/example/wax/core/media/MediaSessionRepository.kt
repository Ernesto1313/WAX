package com.example.wax.core.media

import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class MediaSessionState(
    val trackTitle: String? = null,
    val artistName: String? = null,
    val isPlaying: Boolean = false,
    val coverUrl: String = ""
)

@Singleton
class MediaSessionRepository @Inject constructor() {

    private val _state = MutableStateFlow(MediaSessionState())
    val state: StateFlow<MediaSessionState> = _state.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    @Volatile private var activeController: MediaController? = null

    fun update(trackTitle: String?, artistName: String?, isPlaying: Boolean) {
        // Preserve coverUrl — it is set independently via setAlbumCover() and must not be wiped
        // by track/playback updates from MediaNotificationListenerService.
        _state.update { it.copy(trackTitle = trackTitle, artistName = artistName, isPlaying = isPlaying) }
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
}
