package com.example.wax.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AlbumHistoryEntity::class], version = 1, exportSchema = true)
abstract class AlbumHistoryDatabase : RoomDatabase() {
    abstract fun albumHistoryDao(): AlbumHistoryDao
}
