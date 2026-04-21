package com.example.wax.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the `album_history` table.
 *
 * The [@Dao] annotation marks this interface as a Room DAO. Room generates a concrete
 * implementation class at compile time that translates each annotated method into the
 * appropriate SQLite statement. The generated class is injected wherever [AlbumHistoryDao]
 * is required via Hilt's [com.example.wax.core.di.DatabaseModule].
 */
@Dao
interface AlbumHistoryDao {

    /**
     * Inserts [album] into the `album_history` table.
     *
     * **[OnConflictStrategy.REPLACE] behaviour:**
     * If a row with the same primary key ([AlbumHistoryEntity.id]) already exists, Room
     * deletes the old row and inserts the new one in its place. This effectively updates
     * the record and resets [AlbumHistoryEntity.savedAt] to the current time, so the album
     * bubbles to the top of the history list the next time it is played. Using REPLACE avoids
     * a separate "upsert" query and keeps the DAO interface simple.
     *
     * Marked `suspend` so Room executes the write on a background thread managed by its
     * internal coroutine dispatcher; the caller never blocks the main thread.
     *
     * @param album The [AlbumHistoryEntity] to insert or replace.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumHistoryEntity)

    /**
     * Returns all albums in the history, sorted from most-recently saved to oldest.
     *
     * **Why the return type is [Flow] instead of a `suspend` function:**
     * Returning a [Flow] makes this query *reactive*: Room automatically re-runs the query
     * and emits a new list every time the `album_history` table is modified (insert, delete,
     * or replace). Collectors — such as the HistoryViewModel — receive updates without
     * polling, which keeps the UI in sync with the database at zero extra cost. A `suspend`
     * function would return a one-shot snapshot and miss subsequent changes.
     *
     * @return A cold [Flow] that emits the full, time-ordered history list on each change.
     */
    @Query("SELECT * FROM album_history ORDER BY savedAt DESC")
    fun getAllAlbums(): Flow<List<AlbumHistoryEntity>>

    /**
     * Deletes the history entry with the given [id].
     *
     * Marked `suspend` for the same reason as [insertAlbum] — Room performs the delete
     * on a background thread and the [Flow] returned by [getAllAlbums] will automatically
     * emit an updated list once the deletion is committed.
     *
     * @param id The Spotify album ID of the entry to remove.
     */
    @Query("DELETE FROM album_history WHERE id = :id")
    suspend fun deleteAlbum(id: String)
}
