package com.example.wax.domain.model

/**
 * Domain model representing a Spotify album.
 *
 * ## Why a separate domain model instead of using the DTO directly?
 *
 * In clean architecture the domain layer must be completely independent of
 * external frameworks, APIs, and libraries. `AlbumDto` (in the `data` layer)
 * is shaped by Spotify's JSON contract: its field names are driven by
 * `@SerializedName` annotations, some fields are nullable because the API may
 * or may not include them, and the object is tightly coupled to Gson.
 *
 * `Album` exists so that:
 * - The UI and use-case layers depend on a stable, app-owned contract. If
 *   Spotify renames `release_date` to `released_on`, only `AlbumDto` and the
 *   mapper in `SpotifyRepository` change — nothing in the UI breaks.
 * - Fields that are optional at the API level (`label`, `genres`) are expressed
 *   with non-null defaults here, so callers never need null checks.
 * - The model carries only the fields the app actually uses, keeping ViewModels
 *   and composables free of DTO boilerplate.
 *
 * @property id Spotify's unique album identifier (22-character Base62 string).
 *   Stable across all API versions and used as a cache key.
 * @property name Human-readable album title as it appears on Spotify
 *   (e.g. `"Abbey Road"`).
 * @property artistNames Ordered list of display names for all artists credited
 *   on the album (e.g. `["The Beatles"]`). Parallel to [artistSpotifyUrls] —
 *   index `i` in this list corresponds to index `i` in [artistSpotifyUrls].
 * @property artistSpotifyUrls Ordered list of `https://open.spotify.com/artist/...`
 *   URLs for each credited artist. Parallel to [artistNames].
 * @property coverUrl HTTPS URL of the album's largest available cover art image
 *   (typically 640×640 px). Empty string if Spotify returned no images, which
 *   is uncommon but possible for older catalog entries.
 * @property releaseDate The album's release date as a string. Spotify's precision
 *   varies: `"YYYY"`, `"YYYY-MM"`, or `"YYYY-MM-DD"` are all possible.
 * @property totalTracks Total number of tracks on the album, including any tracks
 *   not present in [tracks] if the album exceeds one page.
 * @property tracks The list of tracks available for this album. May be empty if
 *   the album was fetched from a listing endpoint that does not embed track data;
 *   use `SpotifyRepository.getAlbumTracks` to populate separately in that case.
 * @property spotifyUrl Canonical `https://open.spotify.com/album/...` URL that
 *   opens the album in the Spotify app or web player.
 * @property label Name of the record label that released the album
 *   (e.g. `"Island Records"`). Defaults to empty string when Spotify does not
 *   provide the label, rather than `null`, to simplify UI display logic.
 * @property genres List of genre strings associated with the album
 *   (e.g. `["rock", "indie"]`). Often empty — Spotify tends to associate genres
 *   with artists rather than individual albums. Defaults to empty list.
 */
data class Album(
    val id: String,
    val name: String,
    val artistNames: List<String>,
    val artistSpotifyUrls: List<String>,
    val coverUrl: String,
    val releaseDate: String,
    val totalTracks: Int,
    val tracks: List<Track>,
    val spotifyUrl: String,
    val label: String = "",
    val genres: List<String> = emptyList()
)

/**
 * Domain model representing a single track on a Spotify album.
 *
 * ## Why a separate domain model?
 *
 * Like [Album], `Track` is a clean-architecture domain object that mirrors the
 * shape of `TrackDto` but without any dependency on Gson, Retrofit, or Spotify's
 * JSON naming conventions. The UI layer imports only `com.example.wax.domain.model`
 * and is never exposed to `com.example.wax.data.remote.dto`.
 *
 * ## Why is `previewUrl` nullable?
 *
 * Spotify does not guarantee a 30-second preview for every track. Previews may
 * be absent due to licensing restrictions, regional market rules, or simply
 * because the rights holder has not provided one. `previewUrl` is therefore
 * `String?` rather than `String` to force callers to handle the missing-preview
 * case explicitly rather than crashing on a null dereference.
 *
 * @property id Spotify's unique track identifier. Used as a stable key in lists
 *   and for deep-linking to the track.
 * @property name Display title of the track (e.g. `"Come Together"`).
 * @property trackNumber The track's 1-based position on the album disc.
 *   Used to display the track listing in the correct order.
 * @property durationMs Track length in **milliseconds**. The UI layer is
 *   responsible for formatting this into `mm:ss` for display. Using milliseconds
 *   (rather than seconds) matches Spotify's native unit and avoids lossy integer
 *   division when the duration is not a round number of seconds.
 * @property artistNames List of display names for the artists credited on this
 *   specific track. May differ from the album-level artists for compilations,
 *   split releases, or tracks with featured artists.
 * @property previewUrl A URL to a 30-second MP3 audio preview hosted by Spotify,
 *   or `null` if no preview is available for this track in the user's market.
 */
data class Track(
    val id: String,
    val name: String,
    val trackNumber: Int,
    val durationMs: Int,
    val artistNames: List<String>,
    val previewUrl: String?
)
