package com.example.wax.presentation.main

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.app.NotificationManagerCompat
import com.example.wax.core.auth.SpotifyAuthManager
import com.example.wax.core.auth.TokenManager
import com.example.wax.core.media.MediaSessionRepository
import com.example.wax.core.preferences.UserPreferencesRepository
import com.example.wax.data.repository.AlbumHistoryRepository
import com.example.wax.data.repository.SpotifyRepository
import com.example.wax.domain.model.Album
import com.example.wax.domain.model.TurntableSkin
import com.example.wax.domain.usecase.GetWeeklyAlbumUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MainUiState(
    val albumTitle: String = "The New Abnormal",
    val artistName: String = "The Strokes",
    val year: String = "2020",
    val coverUrl: String = "",
    val spotifyUrl: String = "",
    val isLoading: Boolean = false,
    val isPlaying: Boolean = true,
    val error: String? = null,
    // Extracted from album art via Palette — fallback to dark grey until loaded
    val vinylDominantColor: Color = Color(0xFF1A1A1A),
    val vinylVibrantColor: Color = Color(0xFF2A2A2A),
    val album: Album? = null,
    val currentTrackId: String? = null,
    val isSessionActive: Boolean = false,
    val showNotificationPrompt: Boolean = false,
    val turntableSkin: TurntableSkin = TurntableSkin.DARK
)

// One-shot events that MainActivity must act on (not suitable for UI state)
sealed class AuthEvent {
    object LaunchAuth : AuthEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val getWeeklyAlbumUseCase: GetWeeklyAlbumUseCase,
    private val tokenManager: TokenManager,
    private val repository: SpotifyRepository,
    private val spotifyAuthManager: SpotifyAuthManager,
    private val mediaSessionRepository: MediaSessionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val albumHistoryRepository: AlbumHistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Replay 0 so a re-collected observer does not re-launch auth
    private val _authEvent = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val authEvent: SharedFlow<AuthEvent> = _authEvent.asSharedFlow()

    init {
        checkAuthAndLoad()
        collectMediaSession()
        collectSkinPreference()
        checkNotificationListenerStatus()
    }

    // ── Media session ─────────────────────────────────────────────────────────

    // Collects from MediaSessionRepository (updated by MediaNotificationListenerService).
    // Matches the incoming track title against the loaded album's tracklist and
    // updates currentTrackId + isPlaying so the vinyl and bottom-sheet stay in sync.
    private fun collectMediaSession() {
        viewModelScope.launch {
            mediaSessionRepository.state.collect { mediaState ->
                val album = _uiState.value.album ?: return@collect
                val hasTitle = !mediaState.trackTitle.isNullOrEmpty()
                val matchedTrack = if (hasTitle) {
                    album.tracks.firstOrNull { track ->
                        track.name.contains(mediaState.trackTitle!!, ignoreCase = true) ||
                        mediaState.trackTitle.contains(track.name, ignoreCase = true)
                    }
                } else null
                Log.d("MainViewModel",
                    "MediaSession → title='${mediaState.trackTitle}' | " +
                    "matched='${matchedTrack?.name}' | isPlaying=${mediaState.isPlaying}"
                )
                _uiState.update { state ->
                    state.copy(
                        // Only sync isPlaying from session when a title is active.
                        // Without a title, vinyl keeps its current spin state (ambient).
                        isPlaying = if (hasTitle) mediaState.isPlaying else state.isPlaying,
                        // Only highlight a track when session reports an active title.
                        // Clear when no title (no active session or unmatched album).
                        currentTrackId = if (hasTitle) matchedTrack?.id else null
                    )
                }
            }
        }
        viewModelScope.launch {
            mediaSessionRepository.isSessionActive.collect { active ->
                _uiState.update { it.copy(isSessionActive = active) }
            }
        }
    }

    fun onPlayPause() = mediaSessionRepository.sendPlayPause()
    fun onNext() = mediaSessionRepository.sendSkipToNext()
    fun onPrevious() = mediaSessionRepository.sendSkipToPrevious()

    /** Called by SettingsScreen after clearing tokens — re-runs auth check. */
    fun onDisconnected() = checkAuthAndLoad()

    fun onTrackSelected(trackId: String) {
        _uiState.update { it.copy(currentTrackId = trackId) }
    }

    /** Called by the nav graph when the user taps a history album. */
    fun selectAlbum(album: Album) {
        _uiState.update { it.copy(album = album) }
    }

    // ── Skin preference ───────────────────────────────────────────────────────

    private fun collectSkinPreference() {
        viewModelScope.launch {
            userPreferencesRepository.turntableSkin.collect { skin ->
                _uiState.update { it.copy(turntableSkin = skin) }
            }
        }
    }

    // ── Notification listener prompt ──────────────────────────────────────────

    // Called on init and every time the app resumes, so we auto-dismiss as soon
    // as the user grants access from Settings without needing a manual gesture.
    fun checkNotificationListenerStatus() {
        viewModelScope.launch {
            val isEnabled = NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)

            if (isEnabled) {
                _uiState.update { it.copy(showNotificationPrompt = false) }
                return@launch
            }

            val permanentlyDismissed = userPreferencesRepository.readIsNotifListenerDismissed()
            _uiState.update { it.copy(showNotificationPrompt = !permanentlyDismissed) }
        }
    }

    // permanent = true  → write DataStore flag, never prompt again
    // permanent = false → hide for this session only, prompt again on next launch
    fun dismissNotificationPrompt(permanent: Boolean) {
        viewModelScope.launch {
            if (permanent) {
                userPreferencesRepository.setNotifListenerDismissed()
            }
            _uiState.update { it.copy(showNotificationPrompt = false) }
        }
    }

    // ── Auth check ────────────────────────────────────────────────────────────

    private fun checkAuthAndLoad() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val token = getValidToken()
            if (token != null) {
                loadWeeklyAlbum(token)
            } else {
                _uiState.update { it.copy(isLoading = false) }
                _authEvent.emit(AuthEvent.LaunchAuth)
            }
        }
    }

    // Returns a valid access token, refreshing silently if expired.
    // Returns null when no credentials are stored at all.
    private suspend fun getValidToken(): String? {
        if (tokenManager.isTokenValid()) return tokenManager.getAccessToken()
        val refresh = tokenManager.getRefreshToken() ?: return null
        return try {
            val dto = repository.refreshToken(refresh)
            tokenManager.saveTokens(dto)
            dto.accessToken
        } catch (e: Exception) {
            tokenManager.clearTokens()
            null
        }
    }

    // ── OAuth callback ────────────────────────────────────────────────────────

    fun handleAuthCallback(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val codeVerifier = spotifyAuthManager.getCodeVerifier()
                val dto = repository.exchangeCodeForToken(code, codeVerifier)
                tokenManager.saveTokens(dto)
                loadWeeklyAlbum(dto.accessToken)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Authentication failed") }
            }
        }
    }

    // ── Album loading ─────────────────────────────────────────────────────────

    private suspend fun loadWeeklyAlbum(accessToken: String) {
        try {
            val album = getWeeklyAlbumUseCase(accessToken)
            Log.d("MainViewModel", "Album loaded: '${album.name}' | coverUrl='${album.coverUrl}' | spotifyUrl='${album.spotifyUrl}' | tracks=${album.tracks.size}")
            _uiState.update {
                it.copy(
                    albumTitle = album.name,
                    artistName = album.artistNames.joinToString(", "),
                    year = album.releaseDate.take(4),
                    coverUrl = album.coverUrl,
                    spotifyUrl = album.spotifyUrl,
                    isLoading = false,
                    error = null,
                    album = album,
                    currentTrackId = null,
                )
            }
            albumHistoryRepository.saveAlbum(album)
            if (album.coverUrl.isNotEmpty()) {
                mediaSessionRepository.setAlbumCover(album.coverUrl)
                extractVinylColors(album.coverUrl)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "loadWeeklyAlbum failed", e)
            _uiState.update { it.copy(isLoading = false, error = "Could not load album") }
        }
    }

    // ── Palette extraction ────────────────────────────────────────────────────

    // Loads a downscaled bitmap via Coil, extracts dominant + vibrant swatches,
    // and updates the vinyl gradient colors in state.
    private suspend fun extractVinylColors(imageUrl: String) {
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Palette needs a software-backed bitmap
                .size(200, 200)       // Downscale — we only need colors, not full res
                .build()

            val result = ImageLoader(context).execute(request) as? SuccessResult ?: return
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return

            // generate() is blocking — run on Default dispatcher
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).generate()
            }

            val dominant = palette.getDominantColor(0xFF1A1A1A.toInt())
            val vibrant = palette.getVibrantColor(dominant)

            _uiState.update {
                it.copy(
                    vinylDominantColor = Color(dominant),
                    vinylVibrantColor = Color(vibrant)
                )
            }
        } catch (e: Exception) {
            // Keep dark grey fallback — no state update needed
        }
    }
}
