package com.example.wax.data.remote

import com.example.wax.data.remote.dto.AlbumDto
import com.example.wax.data.remote.dto.NewReleasesDto
import com.example.wax.data.remote.dto.TrackPageDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyApiService {

    @GET("albums/{id}")
    suspend fun getAlbum(
        @Header("Authorization") authorization: String,
        @Path("id") albumId: String
    ): AlbumDto

    @GET("albums/{id}/tracks")
    suspend fun getAlbumTracks(
        @Header("Authorization") authorization: String,
        @Path("id") albumId: String,
        @Query("limit") limit: Int = 50
    ): TrackPageDto

    @GET("browse/new-releases")
    suspend fun getNewReleases(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): NewReleasesDto
}
