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

@Singleton
class SpotifyRepository @Inject constructor(
    private val apiService: SpotifyApiService,
    private val tokenService: SpotifyTokenService
) {
    suspend fun exchangeCodeForToken(code: String, codeVerifier: String): TokenDto {
        return tokenService.getAccessToken(
            code = code,
            redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID,
            codeVerifier = codeVerifier
        )
    }

    suspend fun refreshToken(refreshToken: String): TokenDto {
        return tokenService.refreshAccessToken(
            refreshToken = refreshToken,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID
        )
    }

    suspend fun getAlbum(accessToken: String, albumId: String): AlbumDto {
        return apiService.getAlbum(
            authorization = "Bearer $accessToken",
            albumId = albumId
        )
    }

    suspend fun getAlbumTracks(accessToken: String, albumId: String): List<TrackDto> {
        return apiService.getAlbumTracks(
            authorization = "Bearer $accessToken",
            albumId = albumId
        ).items
    }

    suspend fun getNewReleases(accessToken: String, limit: Int = 20): List<AlbumDto> {
        return apiService.getNewReleases(
            authorization = "Bearer $accessToken",
            limit = limit
        ).albums.items
    }

    /**
     * Searches for an album by name + optional artist name.
     * Returns the first result's full AlbumDto, or null if nothing is found.
     */
    suspend fun searchAndGetAlbum(accessToken: String, albumName: String, artistName: String?): Album? {
        val query = if (!artistName.isNullOrEmpty()) "$albumName artist:$artistName" else albumName
        val searchHit = apiService.searchAlbum(
            authorization = "Bearer $accessToken",
            query = query
        ).albums.items.firstOrNull() ?: return null

        // The search result only has a subset of fields — fetch the full album for tracks/label/etc.
        val fullDto = apiService.getAlbum(
            authorization = "Bearer $accessToken",
            albumId = searchHit.id
        )
        return fullDto.toDomain()
    }
}

// ── Mapper ────────────────────────────────────────────────────────────────────

fun AlbumDto.toDomain(): Album = Album(
    id            = id,
    name          = name,
    artistNames   = artists.map { it.name },
    artistSpotifyUrls = artists.map { it.externalUrls.spotify },
    coverUrl      = images.firstOrNull()?.url ?: "",
    releaseDate   = releaseDate,
    totalTracks   = totalTracks,
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
    label      = label ?: "",
    genres     = genres ?: emptyList()
)
