package com.example.wax.domain.usecase

import com.example.wax.data.repository.SpotifyRepository
import com.example.wax.domain.model.Album
import com.example.wax.domain.model.Track
import javax.inject.Inject

class GetWeeklyAlbumUseCase @Inject constructor(
    private val repository: SpotifyRepository
) {
    companion object {
        // "The New Abnormal" by The Strokes
        private const val CURATED_ALBUM_ID = "2xkZV2Hl1Omi8rk2D7t5lN"
    }

    suspend operator fun invoke(accessToken: String): Album {
        val dto = repository.getAlbum(accessToken, CURATED_ALBUM_ID)

        return Album(
            id = dto.id,
            name = dto.name,
            artistNames = dto.artists.map { it.name },
            artistSpotifyUrls = dto.artists.map { it.externalUrls.spotify },
            coverUrl = dto.images.firstOrNull()?.url ?: "",
            releaseDate = dto.releaseDate,
            totalTracks = dto.totalTracks,
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
            label = dto.label ?: "",
            genres = dto.genres ?: emptyList()
        )
    }
}
