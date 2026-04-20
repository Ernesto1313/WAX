package com.example.wax.data.remote

import com.example.wax.data.remote.dto.AlbumDto
import com.example.wax.data.remote.dto.NewReleasesDto
import com.example.wax.data.remote.dto.SearchResultDto
import com.example.wax.data.remote.dto.TrackPageDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface for Spotify's **Web API** (`https://api.spotify.com/v1/`).
 *
 * Each method maps to a single Spotify REST endpoint. Retrofit generates a dynamic
 * proxy implementation at runtime; calling a method suspends the coroutine, executes
 * the HTTP request on OkHttp's dispatcher thread pool, deserializes the JSON response
 * body into the declared return type via [GsonConverterFactory][retrofit2.converter.gson.GsonConverterFactory],
 * and then resumes the coroutine with the result.
 *
 * ## Why `@Header("Authorization")` on every method?
 *
 * Spotify's Web API uses short-lived OAuth 2.0 bearer tokens for authentication.
 * The token must be sent in the `Authorization` header of every request in the form
 * `Bearer <access_token>`. Unlike a static API key, the token rotates (typically
 * every hour), so it cannot be baked into the Retrofit base configuration via a
 * static [okhttp3.Interceptor]. Instead, callers pass the current token at the call
 * site, keeping the service interface stateless and the token lifecycle owned by the
 * repository layer.
 */
interface SpotifyApiService {

    /**
     * Fetches the full metadata for a single Spotify album.
     *
     * Hits `GET https://api.spotify.com/v1/albums/{id}`.
     *
     * The response includes the album's name, artists, cover art images, release date,
     * total track count, record label, genres, and a first page of track listings
     * (up to 50 tracks). For albums with more than 50 tracks, subsequent pages must
     * be fetched separately via [getAlbumTracks].
     *
     * @param authorization The OAuth 2.0 bearer token in the form `"Bearer <access_token>"`,
     *   placed in the HTTP `Authorization` header to authenticate the request.
     * @param albumId The Spotify album ID (a 22-character Base62 string, e.g. `"4aawyAB9vmqN3uQ7FjRGTy"`),
     *   interpolated into the URL path via `{id}`.
     * @return An [AlbumDto] containing the full album metadata as returned by Spotify.
     */
    @GET("albums/{id}")
    suspend fun getAlbum(
        @Header("Authorization") authorization: String,
        @Path("id") albumId: String
    ): AlbumDto

    /**
     * Fetches a page of tracks belonging to a specific Spotify album.
     *
     * Hits `GET https://api.spotify.com/v1/albums/{id}/tracks`.
     *
     * This endpoint is used when the album object returned by [getAlbum] does not
     * include all tracks (Spotify caps the embedded track list at 50 items). It
     * returns lightweight [com.example.wax.data.remote.dto.TrackDto] objects that
     * include duration, track number, artist list, and an optional 30-second preview URL.
     *
     * @param authorization The OAuth 2.0 bearer token in the form `"Bearer <access_token>"`.
     * @param albumId The Spotify album ID interpolated into the URL path.
     * @param limit Maximum number of tracks to return in this page. Defaults to `50`,
     *   which is also Spotify's maximum per request. Values between 1 and 50 are valid.
     * @return A [TrackPageDto] containing the list of tracks and the total track count
     *   for the album.
     */
    @GET("albums/{id}/tracks")
    suspend fun getAlbumTracks(
        @Header("Authorization") authorization: String,
        @Path("id") albumId: String,
        @Query("limit") limit: Int = 50
    ): TrackPageDto

    /**
     * Fetches a paginated list of new album releases on Spotify.
     *
     * Hits `GET https://api.spotify.com/v1/browse/new-releases`.
     *
     * Used to populate the home feed with recently released albums. Spotify returns
     * albums in reverse-chronological order by release date. Each album in the list
     * is a partial [AlbumDto] (no track listing or label); a follow-up call to
     * [getAlbum] is required to fetch complete metadata for any individual album.
     *
     * @param authorization The OAuth 2.0 bearer token in the form `"Bearer <access_token>"`.
     * @param limit Number of albums to return. Defaults to `20`; maximum is `50`.
     *   Appended to the URL as `?limit=<value>`.
     * @param offset Zero-based index of the first result to return, used for
     *   pagination. Defaults to `0` (first page). Appended as `?offset=<value>`.
     * @return A [NewReleasesDto] wrapping a paginated [com.example.wax.data.remote.dto.AlbumPageDto].
     */
    @GET("browse/new-releases")
    suspend fun getNewReleases(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): NewReleasesDto

    /**
     * Searches the Spotify catalog and returns the best-matching album.
     *
     * Hits `GET https://api.spotify.com/v1/search`.
     *
     * Spotify's search endpoint supports free-text queries with optional field
     * filters. When an artist name is known, callers should embed it in [query]
     * using the `artist:` filter syntax (e.g. `"Rumours artist:Fleetwood Mac"`)
     * to narrow results. This method constrains the search to albums only and
     * requests a single result, making it suitable for "find this album" lookups
     * rather than open-ended browsing.
     *
     * @param authorization The OAuth 2.0 bearer token in the form `"Bearer <access_token>"`.
     * @param query The search term sent as `?q=<value>`. Supports Spotify field
     *   filters such as `artist:`, `album:`, and `year:`.
     * @param type The catalog type to search. Defaults to `"album"`; other valid
     *   values include `"track"`, `"artist"`, and `"playlist"`, but only `"album"`
     *   is used here. Appended as `?type=<value>`.
     * @param limit Maximum number of results to return. Defaults to `1` since this
     *   method is used to find a single best match. Appended as `?limit=<value>`.
     * @return A [SearchResultDto] containing a paginated album list; callers
     *   typically inspect only the first item.
     */
    @GET("search")
    suspend fun searchAlbum(
        @Header("Authorization") authorization: String,
        @Query("q") query: String,
        @Query("type") type: String = "album",
        @Query("limit") limit: Int = 1
    ): SearchResultDto
}
