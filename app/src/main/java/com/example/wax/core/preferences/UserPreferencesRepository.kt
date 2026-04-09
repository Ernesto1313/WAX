package com.example.wax.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.wax.domain.model.LockScreenTheme
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
        private val KEY_WEEKLY_NOTIF_ENABLED =
            booleanPreferencesKey("weekly_notif_enabled")
        private val KEY_ONBOARDING_COMPLETED =
            booleanPreferencesKey("onboarding_completed")
        private val KEY_LOCK_SCREEN_THEME =
            stringPreferencesKey("lock_screen_theme")
    }

    // ── Notification listener dismissed ──────────────────────────────────────

    val isNotifListenerDismissed: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_NOTIF_LISTENER_DISMISSED] ?: false }

    suspend fun readIsNotifListenerDismissed(): Boolean =
        isNotifListenerDismissed.first()

    suspend fun setNotifListenerDismissed() {
        context.userPrefsDataStore.edit { it[KEY_NOTIF_LISTENER_DISMISSED] = true }
    }

    // ── Weekly notification ───────────────────────────────────────────────────

    val weeklyNotifEnabled: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_WEEKLY_NOTIF_ENABLED] ?: true }

    suspend fun setWeeklyNotifEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_WEEKLY_NOTIF_ENABLED] = enabled }
    }

    // ── Onboarding ────────────────────────────────────────────────────────────

    val onboardingCompleted: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }

    suspend fun readOnboardingCompleted(): Boolean = onboardingCompleted.first()

    suspend fun setOnboardingCompleted() {
        context.userPrefsDataStore.edit { it[KEY_ONBOARDING_COMPLETED] = true }
    }

    // ── Lock screen theme ─────────────────────────────────────────────────────

    val lockScreenTheme: Flow<LockScreenTheme> = context.userPrefsDataStore.data
        .map { prefs -> LockScreenTheme.fromKey(prefs[KEY_LOCK_SCREEN_THEME] ?: LockScreenTheme.FLOATING_VINYL.key) }

    suspend fun readLockScreenTheme(): LockScreenTheme = lockScreenTheme.first()

    suspend fun setLockScreenTheme(theme: LockScreenTheme) {
        context.userPrefsDataStore.edit { it[KEY_LOCK_SCREEN_THEME] = theme.key }
    }
}
