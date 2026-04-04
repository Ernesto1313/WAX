package com.example.wax.data.repository

import com.example.wax.BuildConfig
import com.example.wax.data.remote.SpotifyApiService
import com.example.wax.data.remote.SpotifyTokenService
import com.example.wax.data.remote.dto.AlbumDto
import com.example.wax.data.remote.dto.TokenDto
import com.example.wax.data.remote.dto.TrackDto
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
}
