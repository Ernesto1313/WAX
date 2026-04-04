package com.example.wax.data.repository

import com.example.wax.data.local.AlbumHistoryDao
import com.example.wax.data.local.AlbumHistoryEntity
import com.example.wax.domain.model.Album
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumHistoryRepository @Inject constructor(
    private val dao: AlbumHistoryDao
) {
    fun getAllAlbums(): Flow<List<AlbumHistoryEntity>> = dao.getAllAlbums()

    suspend fun saveAlbum(album: Album) = dao.insertAlbum(album.toEntity())

    suspend fun deleteAlbum(id: String) = dao.deleteAlbum(id)
}

// ── Mapping ───────────────────────────────────────────────────────────────────

fun Album.toEntity() = AlbumHistoryEntity(
    id = id,
    title = name,
    artist = artistNames.joinToString(", "),
    coverUrl = coverUrl,
    spotifyUrl = spotifyUrl,
    year = releaseDate.take(4),
    label = label
)

fun AlbumHistoryEntity.toAlbum() = Album(
    id = id,
    name = title,
    artistNames = listOf(artist),
    artistSpotifyUrls = emptyList(),
    coverUrl = coverUrl,
    releaseDate = year,
    totalTracks = 0,
    tracks = emptyList(),
    spotifyUrl = spotifyUrl,
    label = label
)
