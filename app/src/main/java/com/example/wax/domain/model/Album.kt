package com.example.wax.domain.model

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

data class Track(
    val id: String,
    val name: String,
    val trackNumber: Int,
    val durationMs: Int,
    val artistNames: List<String>,
    val previewUrl: String?
)
