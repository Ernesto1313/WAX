package com.example.wax.core.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.wax.data.remote.dto.TokenDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// File-level delegate — DataStore requires exactly one instance per name
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wax_auth")

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        // Unix epoch ms at which the access token expires
        private val KEY_EXPIRES_AT = longPreferencesKey("expires_at")
        // Refresh 60 s before actual expiry to avoid race conditions
        private const val EXPIRY_BUFFER_MS = 60_000L
    }

    suspend fun saveTokens(dto: TokenDto) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = dto.accessToken
            dto.refreshToken?.let { prefs[KEY_REFRESH_TOKEN] = it }
            prefs[KEY_EXPIRES_AT] = System.currentTimeMillis() + dto.expiresIn * 1_000L
        }
    }

    suspend fun getAccessToken(): String? =
        context.dataStore.data.first()[KEY_ACCESS_TOKEN]

    suspend fun getRefreshToken(): String? =
        context.dataStore.data.first()[KEY_REFRESH_TOKEN]

    suspend fun isTokenValid(): Boolean {
        val expiresAt = context.dataStore.data.first()[KEY_EXPIRES_AT] ?: return false
        return System.currentTimeMillis() < expiresAt - EXPIRY_BUFFER_MS
    }

    suspend fun clearTokens() = context.dataStore.edit { it.clear() }
}
