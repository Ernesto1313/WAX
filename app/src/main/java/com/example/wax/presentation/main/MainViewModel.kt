package com.example.wax.presentation.main

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.wax.core.auth.SpotifyAuthManager
import com.example.wax.core.auth.TokenManager
import com.example.wax.core.media.MediaSessionRepository
import com.example.wax.core.preferences.UserPreferencesRepository
import com.example.wax.core.storage.ArtworkCacheManager
import com.example.wax.data.repository.AlbumHistoryRepository
import com.example.wax.data.repository.SpotifyRepository
import com.example.wax.domain.model.Album
import com.example.wax.domain.model.Track
import com.example.wax.domain.model.TurntableSkin
import com.example.wax.domain.usecase.GetWeeklyAlbumUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable snapshot of everything the main screen needs to render.
 *
 * @property albumTitle     Display title of the album currently shown on the turntable.
 * @property artistName     Comma-separated list of artist names for the current album.
 * @property year           Four-digit release year extracted from [Album.releaseDate].
 * @property coverUrl       URL (or local file path resolved by [ArtworkCacheManager]) of the album art.
 * @property spotifyUrl     Deep link to the album on Spotify; used as web fallback when the app is not installed.
 * @property isLoading      True while the initial auth check + album fetch is in progress (drives splash/spinner).
 * @property isPlaying      Whether Spotify is currently playing; mirrors the media session state.
 * @property error          Non-null when a recoverable error message should be shown to the user.
 * @property album          Full [Album] domain object including the track list; null before the first load.
 * @property currentTrackId Spotify track ID of the track that is currently highlighted in the tracklist.
 *                          Set either by [MediaSessionRepository] matching or by a manual user tap.
 * @property isSessionActive True when [MediaSessionRepository] has an active Spotify media session;
 *                           used by [TurntableSection] to decide whether to spin the vinyl.
 * @property showNotificationPrompt True when the notification listener permission has not been granted
 *                                  and the user has not permanently dismissed the prompt.
 * @property isNowPlaying   True when the displayed album comes from a live Spotify session
 *                          rather than the weekly curated pick. Controls the badge label.
 * @property isTracksLoading True while the track list is being fetched separately from the album object;
 *                           the UI shows a spinner instead of an empty list during this window.
 * @property selectedSkin   The visual theme ([TurntableSkin]) the user has selected in Settings,
 *                          persisted in DataStore and collected in [MainViewModel.init].
 */
data class MainUiState(
    val albumTitle: String = "The New Abnormal",
    val artistName: String = "The Strokes",
    val year: String = "2020",
    val coverUrl: String = "",
    val spotifyUrl: String = "",
    val isLoading: Boolean = false,
    val isPlaying: Boolean = true,
    val error: String? = null,
    val album: Album? = null,
    val currentTrackId: String? = null,
    val isSessionActive: Boolean = false,
    val showNotificationPrompt: Boolean = false,
    // true = live from an active Spotify session, false = weekly curated pick
    val isNowPlaying: Boolean = false,
    // true while tracks are being fetched — UI shows a spinner instead of an empty list
    val isTracksLoading: Boolean = false,
    val selectedSkin: TurntableSkin = TurntableSkin.DARK
)

/**
 * One-shot events that [MainActivity] must act on imperatively.
 *
 * These are not suitable for [MainUiState] because they represent navigation/activity-level
 * actions rather than persistent screen state (e.g. launching a browser for OAuth).
 */
sealed class AuthEvent {
    /** Instructs the activity to open the Spotify PKCE authorization URL in a browser. */
    object LaunchAuth : AuthEvent()
}

/**
 * ViewModel for the main turntable screen.
 *
 * Responsibilities:
 * - Checking stored tokens and loading the weekly album on startup.
 * - Handling the OAuth PKCE callback after the user authorizes via browser.
 * - Tracking the active Spotify media session to keep the turntable in sync.
 * - Fetching the full album when Spotify changes to a different album.
 * - Managing the notification listener permission prompt lifecycle.
 *
 * @param getWeeklyAlbumUseCase    Use case that selects and returns this week's curated album.
 * @param tokenManager             Reads, validates, saves, and clears OAuth tokens from secure storage.
 * @param repository               Spotify API data source (search, album tracks, token exchange).
 * @param spotifyAuthManager       Provides the PKCE code verifier generated during the auth flow.
 * @param mediaSessionRepository   Provides a flow of the current Spotify media session state.
 * @param userPreferencesRepository DataStore-backed repo for user settings (skin, dismissed flags).
 * @param albumHistoryRepository   Persists albums to the local history list.
 * @param artworkCacheManager      Saves/loads album artwork to/from internal storage.
 * @param context                  Application context; used for Coil image loading and package checks.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val getWeeklyAlbumUseCase: GetWeeklyAlbumUseCase,
    private val tokenManager: TokenManager,
    private val repository: SpotifyRepository,
    private val spotifyAuthManager: SpotifyAuthManager,
    private val mediaSessionRepository: MediaSessionRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val albumHistoryRepository: AlbumHistoryRepository,
    private val artworkCacheManager: ArtworkCacheManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Backing state flow; all mutations go through [_uiState.update] for thread safety. */
    private val _uiState = MutableStateFlow(MainUiState())

    /** Public read-only view of [_uiState] consumed by the Composable. */
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Replay 0 so a re-collected observer does not re-launch auth
    /** Emits [AuthEvent] values for imperative activity-level responses (e.g. opening browser). */
    private val _authEvent = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)

    /** Public shared flow of [AuthEvent]; consumed once by [MainActivity]. */
    val authEvent: SharedFlow<AuthEvent> = _authEvent.asSharedFlow()

    // Splash screen condition — true once the first album (or auth failure) is resolved
    /** Signals the splash screen to hide; set to true when the album loads or auth fails. */
    private val _isReady = MutableStateFlow(false)

    /** Read-only view of [_isReady]; observed by the splash screen coordinator. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // null = not yet read from DataStore; true/false = resolved
    /**
     * Whether the user has completed the onboarding flow.
     * Starts as `null` while DataStore is being read; resolves to `true` or `false`.
     */
    private val _onboardingCompleted = MutableStateFlow<Boolean?>(null)

    /** Public view of [_onboardingCompleted]; drives the nav graph's start destination. */
    val onboardingCompleted: StateFlow<Boolean?> = _onboardingCompleted.asStateFlow()

    /**
     * Initializes the ViewModel by launching several independent coroutines in parallel:
     *
     * 1. **Safety timeout** — sets [_isReady] after 3 seconds so the splash never hangs
     *    even if the network call stalls or never returns.
     * 2. **Onboarding flag** — reads the one-time onboarding completion flag from DataStore.
     * 3. **Artwork cleanup** — evicts stale cached artwork on IO to avoid disk bloat.
     * 4. **Skin preference** — collects the user's turntable skin selection and keeps [_uiState] in sync.
     * 5. **Auth + album** — [checkAuthAndLoad] checks for a valid token and loads the weekly album.
     * 6. **Media session** — [collectMediaSession] begins listening for Spotify playback events.
     * 7. **Notification prompt** — [checkNotificationListenerStatus] determines whether to show the prompt.
     */
    init {
        // Safety timeout: dismiss the splash screen after 3 seconds regardless of load outcome
        viewModelScope.launch {
            delay(3_000)
            _isReady.value = true
        }
        viewModelScope.launch {
            _onboardingCompleted.value = userPreferencesRepository.readOnboardingCompleted()
        }
        viewModelScope.launch(Dispatchers.IO) { artworkCacheManager.clearOldArtwork() }
        viewModelScope.launch {
            userPreferencesRepository.selectedSkin.collect { skin ->
                _uiState.update { it.copy(selectedSkin = skin) }
            }
        }
        checkAuthAndLoad()
        collectMediaSession()
        checkNotificationListenerStatus()
    }

    // ── Media session ─────────────────────────────────────────────────────────

    // Tracks the last album name seen from Spotify so we only hit the API when
    // the playing album actually changes.
    /** Last album name received from the media session; prevents redundant Spotify API calls. */
    private var lastAlbumName: String? = null

    /** Cancellable job for the ongoing [fetchNowPlayingAlbum] coroutine; cancelled on album change. */
    private var albumFetchJob: Job? = null

    /**
     * Collects media session state from [MediaSessionRepository], which is updated by
     * `MediaNotificationListenerService` whenever Spotify posts a notification.
     *
     * Two responsibilities run inside a single collector:
     *
     * 1. **Track matching** — when a track title arrives, the loaded album's tracklist is scanned
     *    using a bidirectional [String.contains] check (track name ⊂ title OR title ⊂ track name)
     *    to handle slight naming differences between the Spotify notification and the album API.
     *    The matched track's ID is written to [MainUiState.currentTrackId] so the tracklist row
     *    highlights automatically.
     *
     * 2. **Album change detection** — when [MediaState.albumName] differs from [lastAlbumName],
     *    [fetchNowPlayingAlbum] is called to replace the weekly pick with the live album.
     *
     * A second coroutine tracks [MediaSessionRepository.isSessionActive] separately, which drives
     * whether the vinyl platter animation runs.
     */
    private fun collectMediaSession() {
        viewModelScope.launch {
            mediaSessionRepository.state.collect { mediaState ->
                val album    = _uiState.value.album
                val hasTitle = !mediaState.trackTitle.isNullOrEmpty()

                // Track matching — only works once an album is loaded
                val matchedTrack = if (hasTitle && album != null) {
                    album.tracks.firstOrNull { track ->
                        // Use contains() in both directions to tolerate "(feat. ...)" suffixes or
                        // minor title discrepancies between the notification and the album API response.
                        track.name.contains(mediaState.trackTitle!!, ignoreCase = true) ||
                        mediaState.trackTitle.contains(track.name, ignoreCase = true)
                    }
                } else null

                _uiState.update { state ->
                    state.copy(
                        isPlaying      = if (hasTitle) mediaState.isPlaying else state.isPlaying,
                        currentTrackId = if (hasTitle) matchedTrack?.id else null
                    )
                }

                // Album change detection — fetch from Spotify when a new album starts playing
                val newAlbumName = mediaState.albumName
                if (!newAlbumName.isNullOrEmpty() && newAlbumName != lastAlbumName) {
                    lastAlbumName = newAlbumName
                    fetchNowPlayingAlbum(newAlbumName, mediaState.artistName)
                }
            }
        }
        viewModelScope.launch {
            mediaSessionRepository.isSessionActive.collect { active ->
                _uiState.update { it.copy(isSessionActive = active) }
            }
        }
    }

    /**
     * Fetches the full album from the Spotify API when the media session reports a new album
     * is playing. This replaces the weekly pick with the live "now playing" album.
     *
     * The previous [albumFetchJob] is cancelled before starting so that rapid album changes
     * (e.g. quick skipping) do not result in multiple concurrent API calls racing to write state.
     *
     * If the search result returns an album with no tracks (the `/albums` endpoint sometimes omits
     * them), tracks are fetched separately via [SpotifyRepository.getAlbumTracks] before the state
     * is updated. This prevents an empty tracklist flash in the UI.
     *
     * @param albumName  The album title received from the media session notification.
     * @param artistName The artist name received from the media session notification; used to
     *                   narrow the Spotify search query and reduce false matches.
     */
    private fun fetchNowPlayingAlbum(albumName: String, artistName: String?) {
        // Cancel any in-flight fetch for a previous album change before starting a new one
        albumFetchJob?.cancel()
        albumFetchJob = viewModelScope.launch {
            val token = getValidToken() ?: return@launch
            _uiState.update { it.copy(isTracksLoading = true) }
            try {
                val album = repository.searchAndGetAlbum(token, albumName, artistName)
                    ?: run {
                        _uiState.update { it.copy(isTracksLoading = false) }
                        return@launch
                    }

                // If the search result returned no tracks, fetch them separately to avoid an
                // empty tracklist flash before the separate tracks call completes.
                val albumWithTracks = if (album.tracks.isEmpty() && album.totalTracks > 0) {
                    val tracks = repository.getAlbumTracks(token, album.id).map { t ->
                        Track(
                            id          = t.id,
                            name        = t.name,
                            trackNumber = t.trackNumber,
                            durationMs  = t.durationMs,
                            artistNames = t.artists.map { it.name },
                            previewUrl  = t.previewUrl
                        )
                    }
                    album.copy(tracks = tracks)
                } else album

                val coverUrl = artworkCacheManager.resolveUrl(albumWithTracks.id, albumWithTracks.coverUrl)
                cacheArtworkIfNeeded(albumWithTracks.id, albumWithTracks.coverUrl)

                // Single atomic update — album enters state only once tracks are ready,
                // preventing a window where album != null but tracks is still empty.
                _uiState.update {
                    it.copy(
                        albumTitle      = albumWithTracks.name,
                        artistName      = albumWithTracks.artistNames.joinToString(", "),
                        year            = albumWithTracks.releaseDate.take(4),
                        coverUrl        = coverUrl,
                        spotifyUrl      = albumWithTracks.spotifyUrl,
                        album           = albumWithTracks,
                        currentTrackId  = null,
                        isNowPlaying    = true,
                        isTracksLoading = false,
                        error           = null
                    )
                }
                albumHistoryRepository.saveAlbum(albumWithTracks)
                if (coverUrl.isNotEmpty()) {
                    mediaSessionRepository.setAlbumCover(coverUrl)
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "fetchNowPlayingAlbum failed for '$albumName'", e)
                _uiState.update { it.copy(isTracksLoading = false) }
            }
        }
    }

    /** Forwards a play/pause media command to the active Spotify session via [MediaSessionRepository]. */
    fun onPlayPause() = mediaSessionRepository.sendPlayPause()

    /** Forwards a skip-to-next media command to the active Spotify session. */
    fun onNext() = mediaSessionRepository.sendSkipToNext()

    /** Forwards a skip-to-previous media command to the active Spotify session. */
    fun onPrevious() = mediaSessionRepository.sendSkipToPrevious()

    /** Called by SettingsScreen after clearing tokens — re-runs auth check. */
    fun onDisconnected() = checkAuthAndLoad()

    /**
     * Updates [MainUiState.currentTrackId] when the user manually taps a row in the tracklist.
     *
     * This does NOT send a play command; the UI opens the Spotify deep link directly. The ID
     * is stored here so the row highlight updates immediately without waiting for the media
     * session notification to fire.
     *
     * @param trackId The Spotify track ID of the tapped track.
     */
    fun onTrackSelected(trackId: String) {
        _uiState.update { it.copy(currentTrackId = trackId) }
    }

    /** Called by the nav graph when the user taps a history album. */
    fun selectAlbum(album: Album) {
        _uiState.update { it.copy(album = album) }
    }

    // ── Notification listener prompt ──────────────────────────────────────────

    // Called on init and every time the app resumes, so we auto-dismiss as soon
    // as the user grants access from Settings without needing a manual gesture.
    /**
     * Checks whether the notification listener permission is currently enabled and updates
     * [MainUiState.showNotificationPrompt] accordingly.
     *
     * Called on [init] and on every [Lifecycle.Event.ON_RESUME] so that the dialog automatically
     * dismisses once the user returns from the system Settings screen having granted access.
     */
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

    /**
     * Hides the notification listener permission dialog.
     *
     * @param permanent If `true`, writes a DataStore flag so the prompt is never shown again.
     *                  If `false`, hides the dialog for this session only and re-shows it on next launch.
     */
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

    /**
     * Entry point for the authentication + album load flow.
     *
     * Flow:
     * 1. Calls [getValidToken] to retrieve or silently refresh the access token.
     * 2. If a token is available → calls [loadWeeklyAlbum].
     * 3. If no token exists → dismisses the splash screen immediately (so it doesn't
     *    hang while the browser auth flow is in progress) and emits [AuthEvent.LaunchAuth]
     *    to tell the activity to open the Spotify authorization URL.
     */
    private fun checkAuthAndLoad() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val token = getValidToken()
            if (token != null) {
                loadWeeklyAlbum(token)
            } else {
                // No token — dismiss splash before launching the auth flow
                _isReady.value = true
                _uiState.update { it.copy(isLoading = false) }
                _authEvent.emit(AuthEvent.LaunchAuth)
            }
        }
    }

    /**
     * Returns a valid Spotify access token, refreshing it silently if expired.
     *
     * Refresh logic:
     * - If the stored access token is still valid (not expired), return it directly.
     * - If expired, attempt a silent refresh using the stored refresh token.
     * - If the refresh fails or no refresh token exists, clear all stored tokens and return `null`,
     *   which will trigger the full OAuth flow via [checkAuthAndLoad].
     *
     * @return A valid access token string, or `null` if the user must re-authenticate.
     */
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

    /**
     * Handles the OAuth PKCE authorization code received via the deep link redirect.
     *
     * PKCE exchange steps:
     * 1. Retrieves the code verifier that was generated when the auth URL was built.
     * 2. Exchanges the authorization [code] + verifier for an access/refresh token pair.
     * 3. Saves the tokens and immediately loads the weekly album.
     *
     * On failure the splash is dismissed and an error message is written to state so
     * the UI can show a retry option.
     *
     * @param code The one-time authorization code extracted from the redirect URI by [MainActivity].
     */
    fun handleAuthCallback(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val codeVerifier = spotifyAuthManager.getCodeVerifier()
                val dto = repository.exchangeCodeForToken(code, codeVerifier)
                tokenManager.saveTokens(dto)
                loadWeeklyAlbum(dto.accessToken)
            } catch (e: Exception) {
                _isReady.value = true
                _uiState.update { it.copy(isLoading = false, error = "Authentication failed") }
            }
        }
    }

    // ── Album loading ─────────────────────────────────────────────────────────

    /**
     * Loads the weekly curated album using [GetWeeklyAlbumUseCase] and updates [_uiState] atomically.
     *
     * **Atomic update pattern**: both [MainUiState.album] and its track list are fully resolved
     * before a single [_uiState.update] call writes them together. This prevents a race condition
     * where the UI briefly renders an album with an empty tracklist — the tracklist is fetched
     * separately if the `/albums` endpoint omits it, and the state is only updated once both
     * the album metadata and its tracks are ready.
     *
     * If a live "now playing" album has already been loaded (e.g. [fetchNowPlayingAlbum] completed
     * concurrently), the weekly album result is discarded to avoid overwriting the live data.
     *
     * @param accessToken A valid Spotify access token for making authenticated API requests.
     */
    private suspend fun loadWeeklyAlbum(accessToken: String) {
        _uiState.update { it.copy(isTracksLoading = true) }
        try {
            val album = getWeeklyAlbumUseCase(accessToken)

            // If the /albums endpoint returned no tracks, fetch them separately to avoid an
            // empty tracklist. Both fetches complete before we touch uiState.album.
            val albumWithTracks = if (album.tracks.isEmpty() && album.totalTracks > 0) {
                val tracks = repository.getAlbumTracks(accessToken, album.id).map { t ->
                    Track(
                        id          = t.id,
                        name        = t.name,
                        trackNumber = t.trackNumber,
                        durationMs  = t.durationMs,
                        artistNames = t.artists.map { it.name },
                        previewUrl  = t.previewUrl
                    )
                }
                album.copy(tracks = tracks)
            } else album

            // Don't overwrite a live "now playing" album that may have loaded concurrently
            if (_uiState.value.isNowPlaying) {
                _uiState.update { it.copy(isLoading = false, isTracksLoading = false) }
                _isReady.value = true
                return
            }

            val coverUrl = artworkCacheManager.resolveUrl(albumWithTracks.id, albumWithTracks.coverUrl)
            cacheArtworkIfNeeded(albumWithTracks.id, albumWithTracks.coverUrl)

            // Single atomic update — album only enters state once tracks are ready
            _uiState.update {
                it.copy(
                    albumTitle      = albumWithTracks.name,
                    artistName      = albumWithTracks.artistNames.joinToString(", "),
                    year            = albumWithTracks.releaseDate.take(4),
                    coverUrl        = coverUrl,
                    spotifyUrl      = albumWithTracks.spotifyUrl,
                    isLoading       = false,
                    isTracksLoading = false,
                    error           = null,
                    album           = albumWithTracks,
                    currentTrackId  = null,
                    isNowPlaying    = false
                )
            }
            _isReady.value = true
            albumHistoryRepository.saveAlbum(albumWithTracks)
            if (coverUrl.isNotEmpty()) {
                mediaSessionRepository.setAlbumCover(coverUrl)
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "loadWeeklyAlbum failed", e)
            _isReady.value = true
            _uiState.update { it.copy(isLoading = false, isTracksLoading = false, error = "Could not load album") }
        }
    }

    // ── Artwork caching ───────────────────────────────────────────────────────

    /**
     * Loads [networkUrl] via Coil on IO and writes it to internal storage via
     * [ArtworkCacheManager]. No-ops if the artwork is already cached or the URL is empty.
     *
     * @param albumId    The Spotify album ID used as the cache key.
     * @param networkUrl The remote artwork URL to download and persist.
     */
    private fun cacheArtworkIfNeeded(albumId: String, networkUrl: String) {
        if (networkUrl.isEmpty()) return
        if (artworkCacheManager.loadArtwork(albumId) != null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = ImageLoader(context).execute(
                    ImageRequest.Builder(context)
                        .data(networkUrl)
                        .allowHardware(false)
                        .size(512, 512)
                        .build()
                ) as? SuccessResult ?: return@launch
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@launch
                artworkCacheManager.saveArtwork(albumId, bitmap)
            } catch (e: Exception) {
                Log.w("MainViewModel", "Artwork caching failed for $albumId", e)
            }
        }
    }
}
