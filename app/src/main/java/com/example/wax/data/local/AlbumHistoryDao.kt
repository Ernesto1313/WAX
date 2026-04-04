package com.example.wax.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumHistoryEntity)

    @Query("SELECT * FROM album_history ORDER BY savedAt DESC")
    fun getAllAlbums(): Flow<List<AlbumHistoryEntity>>

    @Query("DELETE FROM album_history WHERE id = :id")
    suspend fun deleteAlbum(id: String)
}
