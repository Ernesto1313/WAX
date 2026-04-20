package com.example.wax.domain.usecase

import com.example.wax.data.repository.SpotifyRepository
import com.example.wax.domain.model.Album
import com.example.wax.domain.model.Track
import javax.inject.Inject

/**
 * Use case that returns the curated "album of the week" for the Wax app.
 *
 * ## Use case pattern in clean architecture
 *
 * A use case (also called an "interactor") encapsulates a **single business
 * operation** that the app can perform. It sits in the domain layer — the only
 * layer with no framework dependencies — and orchestrates one or more repository
 * calls to fulfil that operation.
 *
 * Benefits of this pattern:
 * - **Single responsibility** — each use case does exactly one thing, making it
 *   easy to test in isolation with a fake repository.
 * - **Decoupling** — ViewModels depend on the use case interface, not on the
 *   repository directly. If the data source changes (e.g. switching from a
 *   hardcoded album ID to a server-driven one), only this class changes.
 * - **Reusability** — multiple ViewModels or screens can share the same use case
 *   without duplicating the business logic.
 *
 * ## `operator fun invoke()` convention
 *
 * Defining the main entry point as `operator fun invoke(...)` lets callers treat
 * the use case object as if it were a function:
 * ```kotlin
 * val album = getWeeklyAlbumUseCase(accessToken)
 * // instead of:
 * val album = getWeeklyAlbumUseCase.execute(accessToken)
 * ```
 * This is idiomatic Kotlin for single-method classes and keeps call sites concise.
 *
 * @param repository The [SpotifyRepository] used to fetch album data. Injected by
 *   Hilt, which provides the singleton instance.
 */
class GetWeeklyAlbumUseCase @Inject constructor(
    private val repository: SpotifyRepository
) {
    companion object {
        /**
         * Spotify album ID for the curated "album of the week".
         *
         * Currently hardcoded to **"The New Abnormal"** by The Strokes
         * (`2xkZV2Hl1Omi8rk2D7t5lN`). In a future iteration this could be replaced
         * by a server-driven value (e.g. fetched from a remote config or backend)
         * so the weekly pick can be updated without a new app release.
         *
         * A previous design seeded album selection using the ISO week number
         * (obtained via `Calendar.WEEK_OF_YEAR`) to deterministically pick a
         * different album each week from a local list — the week number was used
         * as a modular index (`weekNumber % albumList.size`) so every user always
         * sees the same album for a given calendar week without any server call.
         * That approach has been simplified to a single curated ID for now.
         */
        private const val CURATED_ALBUM_ID = "2xkZV2Hl1Omi8rk2D7t5lN"
    }

    /**
     * Fetches and returns the weekly album as a domain [Album] model.
     *
     * Calls [SpotifyRepository.getAlbum] with the hardcoded [CURATED_ALBUM_ID],
     * then manually maps the returned `AlbumDto` to an [Album] domain object.
     * This mapping mirrors the one in `SpotifyRepository.toDomain()` but is
     * performed inline here because [repository.getAlbum] returns a raw DTO
     * rather than a domain model.
     *
     * ## Mapping details
     *
     * - `artistNames` and `artistSpotifyUrls` are extracted from the `artists`
     *   list as parallel flat lists.
     * - `coverUrl` uses the first (largest) image; falls back to `""` if Spotify
     *   returns no images.
     * - `tracks` maps each embedded `TrackDto` to a [Track] domain object.
     *   If the album's embedded track page is `null` (which can happen when the
     *   API returns a stripped-down album object), an **empty list** is returned
     *   rather than throwing — the caller receives a valid [Album] with no tracks
     *   rather than a crash, and can choose to fetch tracks separately if needed.
     * - `label` and `genres` default to `""` / `emptyList()` when absent in the
     *   response, keeping the domain model non-nullable for simpler UI code.
     *
     * @param accessToken A valid Spotify OAuth 2.0 access token (without the
     *   `"Bearer "` prefix; the repository adds that prefix internally).
     * @return The fully populated [Album] domain model for the weekly curated album.
     * @throws retrofit2.HttpException if the Spotify API returns an error response
     *   (e.g. 401 Unauthorized if the token has expired).
     */
    suspend operator fun invoke(accessToken: String): Album {
        val dto = repository.getAlbum(accessToken, CURATED_ALBUM_ID)

        return Album(
            id = dto.id,
            name = dto.name,
            // Flatten the artists list into parallel name and URL lists
            artistNames = dto.artists.map { it.name },
            artistSpotifyUrls = dto.artists.map { it.externalUrls.spotify },
            // First image is the highest resolution; fall back to empty string if absent
            coverUrl = dto.images.firstOrNull()?.url ?: "",
            releaseDate = dto.releaseDate,
            totalTracks = dto.totalTracks,
            // Map each embedded TrackDto to a Track domain model;
            // use emptyList() if the API omitted the track page entirely
            tracks = dto.tracks?.items?.map { track ->
                Track(
                    id = track.id,
                    name = track.name,
                    trackNumber = track.trackNumber,
                    durationMs = track.durationMs,
                    artistNames = track.artists.map { it.name },
                    previewUrl = track.previewUrl
                )
            } ?: emptyList(),
            spotifyUrl = dto.externalUrls.spotify,
            // Default null optional fields to non-null domain values
            label = dto.label ?: "",
            genres = dto.genres ?: emptyList()
        )
    }
}
