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

/**
 * Jetpack DataStore instance for user-facing preferences, stored under the name `wax_prefs`.
 *
 * **Why DataStore instead of SharedPreferences:**
 * - **Type safety:** Keys are typed ([booleanPreferencesKey], [stringPreferencesKey]) so
 *   a type mismatch is a compile error rather than a runtime crash.
 * - **Coroutines-first:** Reads expose [kotlinx.coroutines.flow.Flow] and writes are
 *   `suspend` functions, making them safe to call from coroutines without blocking the
 *   main thread. SharedPreferences `apply()` is fire-and-forget and can cause data loss
 *   if the process is killed before the background write completes.
 * - **Consistency:** DataStore uses atomic file writes backed by a coroutine-safe mutex,
 *   eliminating the read-modify-write race conditions that SharedPreferences suffers from.
 *
 * **Why a separate DataStore from `wax_auth`:**
 * Each [preferencesDataStore] delegate creates a single file on disk identified by `name`.
 * The Jetpack DataStore documentation explicitly states that only **one** DataStore per
 * file name should exist per process. [com.example.wax.core.auth.TokenManager] already
 * owns a store named `wax_auth` for OAuth tokens. Mixing user preferences into that store
 * would couple two unrelated concerns; using a separate `wax_prefs` store keeps them
 * independent and avoids accidental key collisions.
 */
// Separate store from "wax_auth" (TokenManager) — one DataStore per name per process
private val Context.userPrefsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "wax_prefs")

/**
 * Repository that reads and writes all user-facing application preferences via
 * Jetpack DataStore (Preferences).
 *
 * **Flow-based reads vs one-shot suspend reads:**
 * - **[Flow]-returning properties** (e.g., [isNotifListenerDismissed], [selectedSkin])
 *   are used by the UI layer (ViewModels) to reactively observe preference changes and
 *   recompose when a value is updated. The Flow stays active for the lifetime of the
 *   collector and emits a new value whenever [DataStore.edit] writes to that key.
 * - **`suspend` one-shot reads** (e.g., [isWeeklyNotifEnabled], [readOnboardingCompleted])
 *   are used outside the UI — in Workers or initialization code — where a single current
 *   value is needed and ongoing observation is unnecessary. Internally they call
 *   [Flow.first], which collects exactly one emission and then cancels the Flow.
 *
 * All write functions are `suspend` and safe to call from any coroutine dispatcher.
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    /** Application context injected by Hilt; used to access the DataStore delegate. */
    @ApplicationContext private val context: Context
) {
    companion object {
        /**
         * Whether the user has permanently dismissed the notification-listener permission
         * prompt. When `true`, the prompt banner is never shown again even if the
         * notification listener is still not enabled.
         */
        private val KEY_NOTIF_LISTENER_DISMISSED =
            booleanPreferencesKey("notif_listener_dismissed")

        /**
         * Whether the weekly album notification is enabled. Defaults to `true` so new
         * users receive the first weekly pick without any explicit opt-in.
         */
        private val KEY_WEEKLY_NOTIF_ENABLED =
            booleanPreferencesKey("weekly_notif_enabled")

        /**
         * Whether the user has completed the onboarding flow at least once. Used by
         * [com.example.wax.presentation.WaxNavGraph] to decide the start destination:
         * `"home"` when `true`, `"onboarding"` when `false`.
         */
        private val KEY_ONBOARDING_COMPLETED =
            booleanPreferencesKey("onboarding_completed")

        /**
         * The string key of the currently selected [TurntableSkin]. Stored as a string
         * so that adding new skin variants in the future doesn't require a database
         * migration — the key is resolved by [TurntableSkin.fromKey] at read time.
         */
        private val KEY_SELECTED_SKIN =
            stringPreferencesKey("selected_skin")
    }

    // ── Notification listener dismissed ──────────────────────────────────────

    /**
     * Reactive stream that emits `true` once the notification-listener prompt has been
     * permanently dismissed by the user. Collected by the ViewModel to hide the banner.
     * Defaults to `false` (prompt is not dismissed) for new installs.
     */
    val isNotifListenerDismissed: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_NOTIF_LISTENER_DISMISSED] ?: false }

    /**
     * One-shot read of [isNotifListenerDismissed]. Collects the first emission and
     * returns immediately; intended for use in Workers or non-UI initialization code.
     */
    suspend fun readIsNotifListenerDismissed(): Boolean =
        isNotifListenerDismissed.first()

    /** Permanently marks the notification-listener prompt as dismissed. */
    suspend fun setNotifListenerDismissed() {
        context.userPrefsDataStore.edit { it[KEY_NOTIF_LISTENER_DISMISSED] = true }
    }

    // ── Weekly notification ───────────────────────────────────────────────────

    /**
     * Reactive stream that emits the current weekly-notification enabled state.
     * Collected by the Settings screen to keep the toggle in sync with stored state.
     * Defaults to `true` so first-run users are opted in automatically.
     */
    val weeklyNotifEnabled: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_WEEKLY_NOTIF_ENABLED] ?: true }

    /**
     * One-shot read used by [com.example.wax.MainActivity] before scheduling the
     * [com.example.wax.core.worker.WeeklyAlbumWorker] to avoid enqueuing work for
     * users who have explicitly opted out.
     */
    suspend fun isWeeklyNotifEnabled(): Boolean = weeklyNotifEnabled.first()

    /**
     * Enables or disables the weekly album notification.
     *
     * @param enabled `true` to opt in; `false` to opt out.
     */
    suspend fun setWeeklyNotifEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[KEY_WEEKLY_NOTIF_ENABLED] = enabled }
    }

    // ── Onboarding ────────────────────────────────────────────────────────────

    /**
     * Reactive stream that emits whether onboarding has been completed.
     * Observed by [com.example.wax.presentation.main.MainViewModel] to determine the
     * initial navigation destination. Emits `false` on a fresh install.
     */
    val onboardingCompleted: Flow<Boolean> = context.userPrefsDataStore.data
        .map { prefs -> prefs[KEY_ONBOARDING_COMPLETED] ?: false }

    /**
     * One-shot read of [onboardingCompleted]. Used in contexts where a single boolean
     * value is needed without setting up a long-lived collector.
     */
    suspend fun readOnboardingCompleted(): Boolean = onboardingCompleted.first()

    /** Marks onboarding as complete. Called at the end of the onboarding flow. */
    suspend fun setOnboardingCompleted() {
        context.userPrefsDataStore.edit { it[KEY_ONBOARDING_COMPLETED] = true }
    }

    // ── Turntable skin ────────────────────────────────────────────────────────

    /**
     * Reactive stream that emits the currently selected [TurntableSkin].
     * The stored string key is resolved by [TurntableSkin.fromKey]; if the key is
     * unrecognized (e.g., after a downgrade), [TurntableSkin.DARK] is used as the default.
     * Collected by the Settings and turntable screens to apply the correct skin assets.
     */
    val selectedSkin: Flow<TurntableSkin> = context.userPrefsDataStore.data
        .map { prefs -> TurntableSkin.fromKey(prefs[KEY_SELECTED_SKIN] ?: TurntableSkin.DARK.key) }

    /**
     * Persists the user's chosen [TurntableSkin].
     *
     * @param skin The [TurntableSkin] to store; its [TurntableSkin.key] string is written.
     */
    suspend fun setSelectedSkin(skin: TurntableSkin) {
        context.userPrefsDataStore.edit { it[KEY_SELECTED_SKIN] = skin.key }
    }
}
