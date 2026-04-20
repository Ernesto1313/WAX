package com.example.wax.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Data Transfer Object representing a Spotify album as returned by the Web API.
 *
 * ## Why `@SerializedName`?
 *
 * Spotify's API follows JSON naming conventions and uses **snake_case** for
 * multi-word field names (e.g. `release_date`, `total_tracks`). Kotlin idiom,
 * on the other hand, uses **camelCase** (e.g. `releaseDate`, `totalTracks`).
 * Gson's default field-mapping strategy performs a case-sensitive, exact-match
 * lookup: without `@SerializedName`, a Kotlin property named `releaseDate` would
 * never match the JSON key `release_date`, leaving the field `null` or at its
 * default value after deserialization.
 *
 * Adding `@SerializedName("release_date")` to `releaseDate` explicitly tells Gson
 * which JSON key to read from (and write to), bridging the naming-convention gap
 * without requiring the Kotlin property names to mirror the JSON keys verbatim.
 *
 * Single-word fields like `id` and `name` do not technically need `@SerializedName`,
 * but they are annotated for consistency and to make the JSON contract explicit.
 *
 * ## Nested DTOs
 *
 * Several fields are themselves DTO objects or lists of DTOs:
 * - [artists] — list of [ArtistDto]; each artist has its own id, name, and Spotify URL.
 * - [images] — list of [ImageDto]; Spotify returns multiple resolutions (640×640,
 *   300×300, 64×64); the first element is typically the largest.
 * - [tracks] — an optional [TrackPageDto] embedded in the album object. Spotify
 *   includes the first page of tracks (up to 50) inline; the field is `null` when
 *   the album is returned by list endpoints (e.g. new-releases) that omit track data.
 * - [externalUrls] — an [ExternalUrlsDto] containing the album's canonical
 *   Spotify deep-link URL.
 *
 * @property id Spotify's unique identifier for this album (22-character Base62 string).
 * @property name Human-readable album title as it appears on Spotify.
 * @property releaseDate The album's release date. Format varies by precision:
 *   `"YYYY"`, `"YYYY-MM"`, or `"YYYY-MM-DD"` depending on how much Spotify knows.
 * @property totalTracks Total number of tracks on the album, including tracks not
 *   present in the [tracks] page if the album exceeds 50 tracks.
 * @property artists Ordered list of artists credited on the album.
 * @property images Cover art images at multiple resolutions, ordered largest first.
 * @property tracks First page of track listings embedded in the album object, or
 *   `null` when the album appears in a listing that does not include track data.
 * @property externalUrls Object containing the album's public Spotify URL.
 * @property label Name of the record label, or `null` if not provided by Spotify.
 * @property genres List of genre strings associated with the album. Often empty
 *   (`[]`) for individual albums; Spotify associates genres primarily with artists.
 */
data class AlbumDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("release_date") val releaseDate: String,
    @SerializedName("total_tracks") val totalTracks: Int,
    @SerializedName("artists") val artists: List<ArtistDto>,
    @SerializedName("images") val images: List<ImageDto>,
    @SerializedName("tracks") val tracks: TrackPageDto?,
    @SerializedName("external_urls") val externalUrls: ExternalUrlsDto,
    @SerializedName("label") val label: String? = null,
    @SerializedName("genres") val genres: List<String>? = null
)

/**
 * Data Transfer Object representing a single Spotify artist as embedded inside
 * an [AlbumDto] or [TrackDto].
 *
 * This is a **simplified** artist object; the full artist object returned by
 * `GET /artists/{id}` contains additional fields (follower count, genres, images)
 * that are not present here.
 *
 * @property id Spotify's unique artist identifier.
 * @property name The artist's display name (e.g. `"The Beatles"`).
 * @property externalUrls An [ExternalUrlsDto] containing the artist's Spotify profile URL.
 */
data class ArtistDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("external_urls") val externalUrls: ExternalUrlsDto
)

/**
 * Data Transfer Object representing a single cover-art image at a specific resolution.
 *
 * Spotify provides the same image at multiple resolutions (typically 640×640,
 * 300×300, and 64×64 pixels). The list in [AlbumDto.images] is ordered from
 * largest to smallest, so `images.firstOrNull()` reliably returns the highest-
 * resolution version.
 *
 * @property url Absolute HTTPS URL of the image on Spotify's CDN.
 * @property width Pixel width of the image, or `null` if Spotify did not specify it.
 * @property height Pixel height of the image, or `null` if Spotify did not specify it.
 */
data class ImageDto(
    @SerializedName("url") val url: String,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

/**
 * Data Transfer Object representing a paginated list of tracks as returned by
 * `GET /albums/{id}/tracks` or embedded inside an [AlbumDto].
 *
 * Spotify paginates track lists; a single page contains up to 50 items. When
 * this DTO is embedded in [AlbumDto.tracks], it represents the first page only.
 * The [total] field reflects the true album track count, which may exceed [items].
 *
 * @property items The track objects present on this page of results.
 * @property total The total number of tracks on the album across all pages.
 */
data class TrackPageDto(
    @SerializedName("items") val items: List<TrackDto>,
    @SerializedName("total") val total: Int
)

/**
 * Data Transfer Object representing a single track as returned inside a
 * [TrackPageDto] or by `GET /albums/{id}/tracks`.
 *
 * This is a **simplified** track object; it does not include full audio analysis
 * or popularity data. The `duration_ms` and `track_number` fields use
 * `@SerializedName` to map from Spotify's snake_case JSON keys to camelCase
 * Kotlin properties.
 *
 * @property id Spotify's unique track identifier.
 * @property name Display title of the track (e.g. `"Come Together"`).
 * @property durationMs Track length in **milliseconds**. Divide by `1_000` for seconds
 *   or by `60_000` for minutes. Maps from the JSON key `duration_ms`.
 * @property trackNumber The track's position on the album disc (1-based).
 *   Maps from the JSON key `track_number`.
 * @property artists Ordered list of artists credited on this specific track.
 *   May differ from the album-level artists for compilations or featured artists.
 * @property previewUrl A URL to a 30-second MP3 preview of the track, or `null`
 *   if Spotify does not provide a preview for this track in the user's market.
 */
data class TrackDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("duration_ms") val durationMs: Int,
    @SerializedName("track_number") val trackNumber: Int,
    @SerializedName("artists") val artists: List<ArtistDto>,
    @SerializedName("preview_url") val previewUrl: String?
)

/**
 * Data Transfer Object representing Spotify's `external_urls` object, which
 * contains platform-specific deep-link URLs for an album, artist, or track.
 *
 * Only the `spotify` key is mapped here; the raw object may contain URLs for
 * other platforms, but Spotify is the only one relevant to this app.
 *
 * @property spotify The canonical `https://open.spotify.com/...` URL that opens
 *   the item directly in the Spotify app or web player.
 */
data class ExternalUrlsDto(
    @SerializedName("spotify") val spotify: String
)

/**
 * Data Transfer Object wrapping the response from `GET /browse/new-releases`.
 *
 * Spotify envelopes the album list inside an `"albums"` key at the top level of
 * the response. This wrapper DTO exists solely to match that envelope structure
 * so Gson can deserialize the response without a custom type adapter.
 *
 * @property albums The paginated list of new-release albums.
 */
data class NewReleasesDto(
    @SerializedName("albums") val albums: AlbumPageDto
)

/**
 * Data Transfer Object wrapping the response from `GET /search` when
 * `type=album` is specified.
 *
 * Like [NewReleasesDto], this wrapper mirrors Spotify's JSON envelope:
 * the search result nests album results under an `"albums"` key.
 *
 * @property albums The paginated list of albums matching the search query.
 */
data class SearchResultDto(
    @SerializedName("albums") val albums: AlbumPageDto
)

/**
 * Data Transfer Object representing a paginated list of [AlbumDto] objects.
 *
 * Used as the inner payload for both [NewReleasesDto] and [SearchResultDto].
 * Spotify's full pagination object also includes `href`, `next`, `previous`,
 * `limit`, and `offset` fields; only [items] and [total] are mapped here
 * since the app does not currently implement cursor-based pagination.
 *
 * @property items The album objects on this page of results.
 * @property total The total number of albums available across all pages.
 */
data class AlbumPageDto(
    @SerializedName("items") val items: List<AlbumDto>,
    @SerializedName("total") val total: Int
)
