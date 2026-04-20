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

/**
 * File-level Kotlin property delegate that creates and wires a single
 * [DataStore]<[Preferences]> instance to [Context] under the name "wax_auth".
 *
 * The [preferencesDataStore] delegate enforces a **one-instance-per-name** rule:
 * calling it multiple times with the same name would throw at runtime. By declaring
 * it at the file level (outside any class), there is only ever one delegate object
 * in the process, regardless of how many times `context.dataStore` is accessed.
 *
 * DataStore stores data asynchronously in a file on the internal storage and
 * exposes it as a [kotlinx.coroutines.flow.Flow], making it safe to read and
 * write from coroutines without blocking the main thread.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wax_auth")

/**
 * Persists and retrieves Spotify OAuth 2.0 tokens using Jetpack [DataStore].
 *
 * Responsibilities:
 * - Store the access token, refresh token, and computed expiry timestamp
 *   atomically after a successful token exchange or refresh.
 * - Expose the stored tokens for use by the network layer when building
 *   authenticated API requests.
 * - Determine whether the current access token is still valid, applying a
 *   [EXPIRY_BUFFER_MS] safety margin to avoid sending an expired token.
 * - Wipe all stored token data on logout or when the refresh token is rejected.
 *
 * Why DataStore instead of SharedPreferences?
 * DataStore is coroutine- and Flow-friendly, performs all I/O off the main thread
 * by default, and provides transactional write semantics — multiple keys are
 * updated atomically inside a single [DataStore.edit] lambda, which prevents
 * partial writes if the process is killed mid-update.
 *
 * This class is provided as a [Singleton] by Hilt so the same DataStore
 * instance (and therefore the same underlying file) is shared across the app.
 *
 * @param context Application context used to access the [DataStore] delegate
 *   defined at the file level.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /**
         * DataStore key for the Spotify access token string.
         * Access tokens are short-lived (typically 1 hour) bearer tokens included
         * in the `Authorization: Bearer <token>` header of every API request.
         */
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")

        /**
         * DataStore key for the Spotify refresh token string.
         * Refresh tokens are long-lived and are used to obtain a new access token
         * once the current one expires, without requiring the user to log in again.
         * Spotify may rotate the refresh token on each use; if a new one is returned
         * it replaces the stored value.
         */
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")

        /**
         * DataStore key for the Unix epoch timestamp (in milliseconds) at which
         * the current access token expires. Calculated at save time as:
         *   `System.currentTimeMillis() + expiresIn * 1_000`
         * where `expiresIn` is the number of seconds reported by Spotify's token endpoint.
         */
        private val KEY_EXPIRES_AT = longPreferencesKey("expires_at")

        /**
         * Safety buffer subtracted from the stored expiry timestamp when checking
         * token validity. Treating the token as expired 60 seconds before its actual
         * expiry prevents the following race condition:
         *   - [isTokenValid] returns `true` at T=0.
         *   - The network request is queued but delayed.
         *   - The token actually expires at T=30 s.
         *   - The request arrives at Spotify at T=45 s with an expired token → 401.
         *
         * With a 60-second buffer the token is proactively refreshed before any
         * request could be rejected due to timing.
         */
        private const val EXPIRY_BUFFER_MS = 60_000L
    }

    /**
     * Atomically persists the access token, optional refresh token, and
     * computed expiry timestamp from a successful token API response.
     *
     * The expiry timestamp is derived by adding [TokenDto.expiresIn] seconds
     * (converted to milliseconds) to the current wall-clock time. All three
     * writes occur inside a single [DataStore.edit] transaction, so no other
     * coroutine can observe a state where only some keys have been updated.
     *
     * The refresh token is written only when the response includes one; Spotify
     * does not always return a new refresh token on every access-token refresh,
     * so an absent value should not overwrite the existing stored token.
     *
     * @param dto The [TokenDto] received from Spotify's token endpoint containing
     *   the new access token, an optional refresh token, and the token lifetime
     *   in seconds.
     */
    suspend fun saveTokens(dto: TokenDto) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = dto.accessToken

            // Only update the refresh token if the response provided a new one;
            // Spotify does not rotate the refresh token on every call
            dto.refreshToken?.let { prefs[KEY_REFRESH_TOKEN] = it }

            // Convert expiresIn (seconds) to an absolute epoch-millisecond timestamp
            prefs[KEY_EXPIRES_AT] = System.currentTimeMillis() + dto.expiresIn * 1_000L
        }
    }

    /**
     * Returns the stored Spotify access token, or `null` if no token has been
     * saved yet (i.e., the user has not completed the authorization flow).
     *
     * This is a suspend function because [DataStore.data] is a [kotlinx.coroutines.flow.Flow];
     * [kotlinx.coroutines.flow.first] suspends until the first emission is available,
     * then cancels the flow collection — effectively a one-shot read.
     *
     * @return The current access token string, or `null` if absent.
     */
    suspend fun getAccessToken(): String? =
        context.dataStore.data.first()[KEY_ACCESS_TOKEN]

    /**
     * Returns the stored Spotify refresh token, or `null` if no refresh token
     * has been saved (e.g., after a fresh install before the first login).
     *
     * The refresh token is used by the repository layer to silently obtain a new
     * access token when [isTokenValid] returns `false`.
     *
     * @return The current refresh token string, or `null` if absent.
     */
    suspend fun getRefreshToken(): String? =
        context.dataStore.data.first()[KEY_REFRESH_TOKEN]

    /**
     * Returns `true` if the stored access token is still valid and safe to use
     * for API requests; `false` if it has expired or is about to expire.
     *
     * Validity is determined by comparing the current wall-clock time against
     * the stored expiry timestamp minus [EXPIRY_BUFFER_MS] (60 seconds):
     *
     * ```
     *   valid = now < (expiresAt - 60_000)
     * ```
     *
     * The 60-second buffer ensures that a token considered valid here will
     * still be accepted by Spotify for the duration of a typical network request.
     *
     * Returns `false` immediately if no expiry timestamp is stored, which
     * covers the case where [saveTokens] has never been called.
     *
     * @return `true` if the access token is present and will not expire within
     *   the next 60 seconds; `false` otherwise.
     */
    suspend fun isTokenValid(): Boolean {
        val expiresAt = context.dataStore.data.first()[KEY_EXPIRES_AT]
            ?: return false // No expiry stored means no token has been saved yet

        // Subtract the 60-second buffer so the token is proactively refreshed
        // before it actually expires, avoiding 401 errors on in-flight requests
        return System.currentTimeMillis() < expiresAt - EXPIRY_BUFFER_MS
    }

    /**
     * Removes all stored authentication data — access token, refresh token, and
     * expiry timestamp — from DataStore in a single atomic operation.
     *
     * This should be called on explicit user logout or when the refresh token is
     * rejected by Spotify (e.g., the user revoked access), forcing a full
     * re-authorization via [SpotifyAuthManager.getAuthIntent].
     */
    suspend fun clearTokens() = context.dataStore.edit { it.clear() }
}
