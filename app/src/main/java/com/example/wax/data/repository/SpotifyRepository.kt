package com.example.wax.data.repository

import com.example.wax.BuildConfig
import com.example.wax.data.remote.SpotifyApiService
import com.example.wax.data.remote.SpotifyTokenService
import com.example.wax.data.remote.dto.AlbumDto
import com.example.wax.data.remote.dto.TokenDto
import com.example.wax.data.remote.dto.TrackDto
import com.example.wax.domain.model.Album
import com.example.wax.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for all Spotify data in the Wax app.
 *
 * ## Repository pattern
 *
 * The repository pattern inserts an abstraction layer between the domain/UI
 * layer and the data sources (network, cache, database). Its responsibilities
 * in this app are:
 *
 * 1. **Encapsulate data sources** — ViewModels and use-cases call repository
 *    methods without knowing whether data comes from the network, a local cache,
 *    or a database. Swapping or adding a data source does not touch any code
 *    outside this class.
 *
 * 2. **Map DTOs to domain models** — [SpotifyApiService] returns raw DTO objects
 *    whose shape is dictated by Spotify's JSON contract. The repository translates
 *    these into clean domain models ([Album], [Track]) that the rest of the app
 *    depends on. If Spotify changes a field name, only this class and the DTOs need
 *    updating.
 *
 * 3. **Own credentials** — `BuildConfig.SPOTIFY_CLIENT_ID` and
 *    `BuildConfig.SPOTIFY_REDIRECT_URI` are referenced here rather than scattered
 *    across ViewModels, keeping credential usage in one auditable location.
 *
 * ## Error handling
 *
 * This repository deliberately does **not** catch exceptions. Network errors,
 * HTTP error codes (which Retrofit surfaces as [retrofit2.HttpException]), and
 * JSON parse failures propagate upward as thrown exceptions. The calling layer
 * (ViewModel or use-case) is responsible for wrapping calls in `try/catch` or
 * a `Result`/`Either` wrapper and presenting error state to the UI. This keeps
 * the repository lean and avoids swallowing errors silently.
 *
 * Installed as a `@Singleton` so the same instance (and any future in-memory
 * cache it might maintain) is shared across all injection sites.
 *
 * @param apiService Retrofit service for `https://api.spotify.com/v1/` endpoints.
 * @param tokenService Retrofit service for `https://accounts.spotify.com/api/token`.
 */
@Singleton
class SpotifyRepository @Inject constructor(
    private val apiService: SpotifyApiService,
    private val tokenService: SpotifyTokenService
) {

    /**
     * Exchanges a PKCE authorization code for an access token and refresh token.
     *
     * Called exactly once after the user completes the Spotify authorization page
     * and the app receives the one-time `code` via its redirect URI deep-link.
     * Delegates to [SpotifyTokenService.getAccessToken] with the app's registered
     * [BuildConfig.SPOTIFY_REDIRECT_URI] and [BuildConfig.SPOTIFY_CLIENT_ID].
     *
     * The returned [TokenDto] should be immediately persisted via `TokenManager`
     * so the tokens survive process death.
     *
     * @param code The one-time authorization code extracted from the redirect URI.
     * @param codeVerifier The PKCE code verifier generated before the authorization
     *   flow was started, retrieved from `SpotifyAuthManager.getCodeVerifier()`.
     * @return A [TokenDto] containing the access token, refresh token, and expiry.
     * @throws retrofit2.HttpException if Spotify rejects the code (e.g. already used,
     *   expired, or `code_verifier` mismatch).
     */
    suspend fun exchangeCodeForToken(code: String, codeVerifier: String): TokenDto {
        return tokenService.getAccessToken(
            code = code,
            redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            codeVerifier = codeVerifier
        )
    }

    /**
     * Silently renews the access token using the stored refresh token.
     *
     * Called by the authentication layer when `TokenManager.isTokenValid()` returns
     * `false`. Does not require user interaction. If Spotify rotates the refresh
     * token, the new value is present in [TokenDto.refreshToken] and must be
     * persisted, replacing the old one.
     *
     * @param refreshToken The long-lived refresh token previously saved by `TokenManager`.
     * @return A [TokenDto] with a fresh access token. [TokenDto.refreshToken] may be
     *   non-null if Spotify issued a new refresh token.
     * @throws retrofit2.HttpException with HTTP 400 if the refresh token is invalid
     *   or has been revoked; the caller should treat this as a forced logout.
     */
    suspend fun refreshToken(refreshToken: String): TokenDto {
        return tokenService.refreshAccessToken(
            refreshToken = refreshToken,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID
        )
    }

    /**
     * Fetches full metadata for a single Spotify album by its ID.
     *
     * Returns a raw [AlbumDto] rather than a domain [Album] because this method
     * is used internally by [searchAndGetAlbum], which performs its own domain
     * mapping after the fetch. Direct callers that need a domain model should use
     * [searchAndGetAlbum] or call [AlbumDto.toDomain] on the result.
     *
     * The `authorization` header is constructed as `"Bearer $accessToken"` to
     * match the OAuth 2.0 bearer token format required by Spotify.
     *
     * @param accessToken A valid Spotify access token (without the `"Bearer "` prefix).
     * @param albumId The Spotify album ID (22-character Base62 string).
     * @return An [AlbumDto] with full album metadata, including an embedded first
     *   page of tracks.
     */
    suspend fun getAlbum(accessToken: String, albumId: String): AlbumDto {
        return apiService.getAlbum(
            // Prefix the token with "Bearer " as required by the OAuth 2.0 spec
            authorization = "Bearer $accessToken",
            albumId = albumId
        )
    }

    /**
     * Fetches all tracks for a Spotify album, returning up to 50 items per request.
     *
     * Unlike [getAlbum], which embeds only the first page of tracks, this method
     * targets the dedicated tracks endpoint and returns the items list directly,
     * unwrapping the [com.example.wax.data.remote.dto.TrackPageDto] envelope.
     *
     * @param accessToken A valid Spotify access token.
     * @param albumId The Spotify album ID.
     * @return A flat list of [TrackDto] objects for the album. Limited to 50 tracks
     *   per request; albums with more than 50 tracks would require pagination (not
     *   currently implemented).
     */
    suspend fun getAlbumTracks(accessToken: String, albumId: String): List<TrackDto> {
        return apiService.getAlbumTracks(
            authorization = "Bearer $accessToken",
            albumId = albumId
        ).items // Unwrap the TrackPageDto envelope and return only the items list
    }

    /**
     * Fetches a list of newly released albums from Spotify's browse catalog.
     *
     * Unwraps the nested JSON envelope (`NewReleasesDto → AlbumPageDto → items`)
     * and returns a flat list of [AlbumDto] objects. Each album in the list is a
     * partial object (no track listing); use [getAlbum] to fetch full details for
     * any individual album.
     *
     * @param accessToken A valid Spotify access token.
     * @param limit Number of albums to return. Defaults to `20`, maximum `50`.
     * @return A list of partially populated [AlbumDto] objects representing
     *   recently released albums in the user's market.
     */
    suspend fun getNewReleases(accessToken: String, limit: Int = 20): List<AlbumDto> {
        return apiService.getNewReleases(
            authorization = "Bearer $accessToken",
            limit = limit
        ).albums.items // Unwrap NewReleasesDto → AlbumPageDto → items
    }

    /**
     * Searches for an album by name and optional artist, then returns its full
     * domain model with complete track listing and metadata.
     *
     * ## Two-step fetch
     *
     * Spotify's search endpoint returns **partial** album objects: they include
     * basic fields (id, name, artists, images) but omit track listings, record
     * label, and genres. A single search result is therefore not enough to display
     * the full album detail screen.
     *
     * This method performs two sequential requests:
     * 1. `GET /search?q=...&type=album&limit=1` — find the best-matching album ID.
     * 2. `GET /albums/{id}` — fetch the full album object using that ID.
     *
     * The full album object is then mapped to a domain [Album] via [AlbumDto.toDomain].
     *
     * @param accessToken A valid Spotify access token.
     * @param albumName The album title to search for.
     * @param artistName Optional artist name. When provided, it is appended as
     *   `artist:<name>` in the Spotify search query to narrow results and reduce
     *   false positives (e.g. searching for "Love" without an artist would return
     *   many unrelated albums).
     * @return The fully populated domain [Album] for the best search match, or
     *   `null` if the search returns no results.
     */
    suspend fun searchAndGetAlbum(accessToken: String, albumName: String, artistName: String?): Album? {
        // Build the query string: include the artist: filter only when an artist name
        // is available, as an empty artist: filter can suppress valid results
        val query = if (!artistName.isNullOrEmpty()) "$albumName artist:$artistName" else albumName

        // Step 1: search for the album — result contains only partial metadata
        val searchHit = apiService.searchAlbum(
            authorization = "Bearer $accessToken",
            query = query
        ).albums.items.firstOrNull() ?: return null // No results — bail early

        // Step 2: fetch the full album using the ID from the search hit,
        // because the search response omits tracks, label, genres, etc.
        val fullDto = apiService.getAlbum(
            authorization = "Bearer $accessToken",
            albumId = searchHit.id
        )

        // Map the raw DTO to a clean domain model before returning to callers
        return fullDto.toDomain()
    }
}

// ── DTO → Domain mapper ───────────────────────────────────────────────────────

/**
 * Maps an [AlbumDto] (raw Spotify API response shape) to an [Album] domain model.
 *
 * This extension function is the **only** place in the codebase where the
 * translation from DTO to domain object occurs for albums, ensuring that if
 * Spotify changes a field name or structure, only this mapper and the DTO need
 * updating — the rest of the app stays unchanged.
 *
 * Key mapping decisions:
 * - [Album.artistNames] and [Album.artistSpotifyUrls] are extracted from the
 *   [AlbumDto.artists] list as parallel flat lists for easy consumption in the UI.
 * - [Album.coverUrl] takes the first (largest) image URL, falling back to an
 *   empty string if Spotify returns no images (uncommon, but possible).
 * - [Album.tracks] maps each [TrackDto] to a [Track] domain object. If the album
 *   object has no embedded track page (i.e. [AlbumDto.tracks] is `null`), an empty
 *   list is used rather than crashing — callers can request tracks separately.
 * - [Album.label] and [Album.genres] default to empty string / empty list when
 *   Spotify does not provide them, keeping domain objects non-nullable for simpler UI code.
 *
 * @receiver The raw [AlbumDto] to convert.
 * @return A clean [Album] domain model with all nullable DTO fields resolved to
 *   sensible non-null defaults.
 */
fun AlbumDto.toDomain(): Album = Album(
    id            = id,
    name          = name,
    // Flatten the artists list into parallel name and URL lists
    artistNames   = artists.map { it.name },
    artistSpotifyUrls = artists.map { it.externalUrls.spotify },
    // First image is the largest resolution; fall back to empty string if none
    coverUrl      = images.firstOrNull()?.url ?: "",
    releaseDate   = releaseDate,
    totalTracks   = totalTracks,
    // Map each TrackDto to a Track domain model; use emptyList() if tracks are absent
    tracks        = tracks?.items?.map { track ->
        Track(
            id          = track.id,
            name        = track.name,
            trackNumber = track.trackNumber,
            durationMs  = track.durationMs,
            artistNames = track.artists.map { it.name },
            previewUrl  = track.previewUrl
        )
    } ?: emptyList(),
    spotifyUrl = externalUrls.spotify,
    // Default null fields to empty so domain consumers don't need null checks
    label      = label ?: "",
    genres     = genres ?: emptyList()
)
