package com.example.wax.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "album_history")
data class AlbumHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val spotifyUrl: String,
    val year: String,
    val label: String,
    val savedAt: Long = System.currentTimeMillis()
)
