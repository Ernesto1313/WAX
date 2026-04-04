package com.example.wax.data.remote.dto

import com.google.gson.annotations.SerializedName

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

data class ArtistDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("external_urls") val externalUrls: ExternalUrlsDto
)

data class ImageDto(
    @SerializedName("url") val url: String,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

data class TrackPageDto(
    @SerializedName("items") val items: List<TrackDto>,
    @SerializedName("total") val total: Int
)

data class TrackDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("duration_ms") val durationMs: Int,
    @SerializedName("track_number") val trackNumber: Int,
    @SerializedName("artists") val artists: List<ArtistDto>,
    @SerializedName("preview_url") val previewUrl: String?
)

data class ExternalUrlsDto(
    @SerializedName("spotify") val spotify: String
)

data class NewReleasesDto(
    @SerializedName("albums") val albums: AlbumPageDto
)

data class AlbumPageDto(
    @SerializedName("items") val items: List<AlbumDto>,
    @SerializedName("total") val total: Int
)
