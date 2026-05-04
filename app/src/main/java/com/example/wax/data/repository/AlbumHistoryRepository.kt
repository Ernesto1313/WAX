package com.example.wax.data.repository

import com.example.wax.data.local.AlbumHistoryDao
import com.example.wax.data.local.AlbumHistoryEntity
import com.example.wax.domain.model.Album
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that mediates access to the local album listening history stored in Room.
 *
 * Acts as the single source of truth for history data in the app: ViewModels and other callers
 * depend on this class rather than directly on [AlbumHistoryDao], keeping the data layer
 * encapsulated behind a stable API. The repository also owns the [Album] ↔ [AlbumHistoryEntity]
 * mapping so domain models never leak into the local storage layer.
 *
 * @param dao Room DAO injected by Hilt; provides the underlying SQLite operations.
 */
@Singleton
class AlbumHistoryRepository @Inject constructor(
    private val dao: AlbumHistoryDao
) {
    /**
     * Returns a [Flow] that emits the full history list, ordered from most recently saved
     * to oldest, and re-emits whenever the `album_history` table changes.
     *
     * Delegates directly to [AlbumHistoryDao.getAllAlbums]; the reactive [Flow] return type
     * means the [com.example.wax.presentation.history.HistoryViewModel] does not need to
     * manually refresh after inserts or deletes.
     *
     * @return A cold [Flow] of [AlbumHistoryEntity] lists.
     */
    fun getAllAlbums(): Flow<List<AlbumHistoryEntity>> = dao.getAllAlbums()

    /**
     * Converts [album] to an [AlbumHistoryEntity] and inserts (or replaces) it in the database.
     *
     * Uses [Album.toEntity] to translate the domain model into the storage format. The underlying
     * DAO uses [androidx.room.OnConflictStrategy.REPLACE], so if an album with the same
     * [Album.id] already exists in the history, it is updated and its [AlbumHistoryEntity.savedAt]
     * timestamp is refreshed, bubbling it to the top of the list.
     *
     * @param album The domain [Album] to persist; its [Album.id] is used as the primary key.
     */
    suspend fun saveAlbum(album: Album) = dao.insertAlbum(album.toEntity())

    /**
     * Deletes the history entry with the given [id].
     *
     * @param id The Spotify album ID of the entry to remove from history.
     */
    suspend fun deleteAlbum(id: String) = dao.deleteAlbum(id)
}

// ── Mapping ───────────────────────────────────────────────────────────────────

/**
 * Converts a domain [Album] to its Room storage representation [AlbumHistoryEntity].
 *
 * Key transformations:
 * - [Album.artistNames] is joined to a single comma-separated string because the history table
 *   stores the artist as a single column; the full artist list is not needed in the history view.
 * - [Album.releaseDate] is truncated to the first 4 characters (the year) because Spotify
 *   returns the date in varying precision (`"YYYY"`, `"YYYY-MM"`, `"YYYY-MM-DD"`), and the
 *   history card only displays the year.
 * - [AlbumHistoryEntity.savedAt] defaults to [System.currentTimeMillis] at construction time,
 *   recording when this entity was created.
 *
 * @receiver The domain [Album] to convert.
 * @return A [AlbumHistoryEntity] suitable for insertion into the Room database.
 */
fun Album.toEntity() = AlbumHistoryEntity(
    id = id,
    title = name,
    artist = artistNames.joinToString(", "),
    coverUrl = coverUrl,
    spotifyUrl = spotifyUrl,
    year = releaseDate.take(4),
    label = label
)

/**
 * Converts a [AlbumHistoryEntity] back to a domain [Album].
 *
 * Used by [com.example.wax.presentation.history.HistoryScreen] when the user taps a history
 * card to open the album in the detail screen. Several fields are set to their zero/empty
 * defaults because the history entity stores only a subset of the full album data:
 * - [Album.artistNames] is reconstructed as a single-element list from the stored string; the
 *   original multi-artist breakdown is lost at save time (see [Album.toEntity]).
 * - [Album.artistSpotifyUrls], [Album.tracks], and [Album.totalTracks] default to empty values
 *   because the history only needs enough data to show the card and open the detail screen.
 * - [Album.genres] is not stored in history and defaults to an empty list.
 *
 * @receiver The [AlbumHistoryEntity] to convert.
 * @return A domain [Album] with partial data sufficient for display and navigation.
 */
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
