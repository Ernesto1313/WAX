package com.example.wax.core.di

import com.example.wax.core.auth.SpotifyAuthManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthEntryPoint {
    fun spotifyAuthManager(): SpotifyAuthManager
}
