package com.example.wax.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.wax.domain.model.TurntableSkin
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Separate store from "wax_auth" (TokenManager) — one DataStore per name per process
private val Context.userPrefsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "wax_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_NOTIF_LISTENER_DISMISSED =
            booleanPreferencesKey("notif_listener_dismissed")
        private val KEY_TURNTABLE_SKIN =
            stringPreferencesKey("turntable_skin")
        private val KEY_WEEKLY_NOTIF_ENABLED =
            booleanPreferencesKey("weekly_notif_enabled")
    }

    // ── Notification listener dismissed ──────────────────────────────────────

    val isNotifListenerDismissed: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_NOTIF_LISTENER_DISMISSED] ?: false }

    suspend fun readIsNotifListenerDismissed(): Boolean =
        isNotifListenerDismissed.first()

    suspend fun setNotifListenerDismissed() {
        context.userPrefsDataStore.edit { it[KEY_NOTIF_LISTENER_DISMISSED] = true }
    }

    // ── Turntable skin ────────────────────────────────────────────────────────

    val turntableSkin: Flow<TurntableSkin> = context.userPrefsDataStore.data
        .map { prefs -> TurntableSkin.fromKey(prefs[KEY_TURNTABLE_SKIN] ?: TurntableSkin.DARK.key) }

    suspend fun setTurntableSkin(skin: TurntableSkin) {
        context.userPrefsDataStore.edit { it[KEY_TURNTABLE_SKIN] = skin.key }
    }

    // ── Weekly notification ───────────────────────────────────────────────────

    val weeklyNotifEnabled: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_WEEKLY_NOTIF_ENABLED] ?: true }

    suspend fun setWeeklyNotifEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_WEEKLY_NOTIF_ENABLED] = enabled }
    }
}
