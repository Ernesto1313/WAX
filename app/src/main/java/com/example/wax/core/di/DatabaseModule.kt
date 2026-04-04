package com.example.wax.core.di

import android.content.Context
import androidx.room.Room
import com.example.wax.data.local.AlbumHistoryDao
import com.example.wax.data.local.AlbumHistoryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAlbumHistoryDatabase(@ApplicationContext context: Context): AlbumHistoryDatabase =
        Room.databaseBuilder(
            context,
            AlbumHistoryDatabase::class.java,
            "wax_album_history.db"
        ).build()

    @Provides
    fun provideAlbumHistoryDao(db: AlbumHistoryDatabase): AlbumHistoryDao = db.albumHistoryDao()
}
