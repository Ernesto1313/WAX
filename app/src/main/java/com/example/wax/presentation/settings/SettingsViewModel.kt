package com.example.wax.presentation.settings

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.wax.core.auth.TokenManager
import com.example.wax.core.preferences.UserPreferencesRepository
import com.example.wax.core.worker.WeeklyAlbumWorker
import com.example.wax.domain.model.TurntableSkin
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Immutable snapshot of the settings screen UI state.
 *
 * @property isSpotifyConnected     True when a Spotify refresh token is stored, meaning the user
 *                                  is currently logged in to Spotify.
 * @property hasNotificationAccess  True when the app's notification listener service is enabled.
 *                                  Refreshed on every [android.lifecycle.Lifecycle.Event.ON_RESUME]
 *                                  via [SettingsViewModel.refreshNotificationAccess].
 * @property hasOverlayPermission   True when the app holds the "Draw over other apps" permission.
 *                                  Refreshed similarly via [SettingsViewModel.refreshOverlayPermission].
 * @property weeklyNotifEnabled     Whether the weekly album notification is currently enabled.
 *                                  Collected reactively from [UserPreferencesRepository.weeklyNotifEnabled].
 * @property selectedSkin           The user's currently selected [TurntableSkin].
 *                                  Collected reactively from [UserPreferencesRepository.selectedSkin].
 */
data class SettingsUiState(
    val isSpotifyConnected: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val weeklyNotifEnabled: Boolean = true,
    val selectedSkin: TurntableSkin = TurntableSkin.DARK
)

/**
 * One-shot events emitted by [SettingsViewModel] that require an imperative response
 * from [com.example.wax.presentation.settings.SettingsScreen] (e.g., triggering navigation
 * after the user disconnects from Spotify).
 */
sealed class SettingsEvent {
    /**
     * Emitted after Spotify tokens are cleared by [SettingsViewModel.disconnect].
     * The screen should forward this to the nav graph callback so the app navigates
     * back to the home screen with a cleared state.
     */
    data object Disconnected : SettingsEvent()
}

/**
 * ViewModel for the settings screen.
 *
 * Responsibilities:
 * - Checking and exposing the Spotify connection status (refresh token presence).
 * - Reflecting the current notification listener and overlay permission states.
 * - Toggling and scheduling the weekly album notification via WorkManager.
 * - Persisting the selected turntable skin to DataStore.
 * - Clearing Spotify tokens and signalling a forced logout via [SettingsEvent.Disconnected].
 *
 * @param userPreferencesRepository DataStore-backed repository for user preferences.
 * @param tokenManager              Reads and clears OAuth tokens from secure storage.
 * @param context                   Application context for permission checks and WorkManager access.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Backing mutable state; all updates go through [_uiState.update] for thread safety. */
    private val _uiState = MutableStateFlow(SettingsUiState())

    /** Public read-only state consumed by [SettingsScreen]. */
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Backing one-shot event flow; replayed to new collectors only if capacity allows. */
    private val _events = MutableSharedFlow<SettingsEvent>()

    /** Public shared flow of [SettingsEvent]; collected by [SettingsScreen] for navigation. */
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    /**
     * On initialization:
     * 1. Checks the Spotify connection status.
     * 2. Reads the initial overlay permission state.
     * 3. Starts collecting the weekly notification toggle preference.
     * 4. Starts collecting the selected turntable skin preference.
     *
     * Both collection coroutines stay active for the ViewModel lifetime so that changes
     * made from other screens (e.g., the skin picker in Settings) are reflected here immediately.
     */
    init {
        checkSpotifyConnection()
        refreshOverlayPermission()
        viewModelScope.launch {
            userPreferencesRepository.weeklyNotifEnabled.collect { enabled ->
                _uiState.update { it.copy(weeklyNotifEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.selectedSkin.collect { skin ->
                _uiState.update { it.copy(selectedSkin = skin) }
            }
        }
    }

    // ── Init collectors ───────────────────────────────────────────────────────

    /**
     * Checks whether a Spotify refresh token is stored in [TokenManager] and updates
     * [SettingsUiState.isSpotifyConnected] accordingly.
     *
     * A non-null refresh token is the canonical indicator that the user is logged in:
     * the access token may have expired and been cleared, but as long as the refresh token
     * exists, the app can silently renew the session. A null refresh token means the user
     * has never logged in or has explicitly disconnected.
     */
    private fun checkSpotifyConnection() {
        viewModelScope.launch {
            val connected = tokenManager.getRefreshToken() != null
            _uiState.update { it.copy(isSpotifyConnected = connected) }
        }
    }

    // ── Public actions ────────────────────────────────────────────────────────

    /**
     * Reads the current notification listener permission state and updates [SettingsUiState.hasNotificationAccess].
     *
     * Called on [android.lifecycle.Lifecycle.Event.ON_RESUME] so the badge updates live if the
     * user enables the permission in system Settings and returns to the app without restarting.
     */
    fun refreshNotificationAccess() {
        val hasAccess = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
        _uiState.update { it.copy(hasNotificationAccess = hasAccess) }
    }

    /**
     * Reads the current overlay permission state and updates [SettingsUiState.hasOverlayPermission].
     *
     * Called on [android.lifecycle.Lifecycle.Event.ON_RESUME] so the badge reflects the result
     * of the user navigating to the overlay permission screen and granting or denying access.
     */
    fun refreshOverlayPermission() {
        _uiState.update { it.copy(hasOverlayPermission = Settings.canDrawOverlays(context)) }
    }

    /**
     * Enables or disables the weekly album notification.
     *
     * When [enabled] is `true`: persists the preference and enqueues (or replaces) a
     * [WeeklyAlbumWorker] periodic task scheduled for the coming Sunday at 09:00.
     * [ExistingPeriodicWorkPolicy.REPLACE] is used here (vs KEEP in [com.example.wax.MainActivity])
     * because the user is explicitly re-enabling the feature and should get a fresh schedule.
     *
     * When [enabled] is `false`: persists the preference and cancels the WorkManager task by
     * its unique work name so no further notifications are fired until the user re-enables it.
     *
     * @param enabled `true` to opt in to the weekly notification; `false` to opt out.
     */
    fun setWeeklyNotifEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setWeeklyNotifEnabled(enabled)
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                val now = Calendar.getInstance()
                // Calculate the delay to the next Sunday at 09:00
                val target = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (!after(now)) add(Calendar.WEEK_OF_YEAR, 1)
                }
                val delayMs = target.timeInMillis - now.timeInMillis
                val request = PeriodicWorkRequestBuilder<WeeklyAlbumWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    "weekly_album_notification",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request
                )
            } else {
                workManager.cancelUniqueWork("weekly_album_notification")
            }
        }
    }

    /**
     * Persists the user's chosen [TurntableSkin] to DataStore.
     *
     * The [userPreferencesRepository.selectedSkin] Flow collector in [init] will emit the
     * new value and update [SettingsUiState.selectedSkin] reactively, so the UI does not
     * need to be updated manually.
     *
     * @param skin The [TurntableSkin] to store as the active skin.
     */
    fun setSelectedSkin(skin: TurntableSkin) {
        viewModelScope.launch { userPreferencesRepository.setSelectedSkin(skin) }
    }

    /**
     * Clears all stored Spotify OAuth tokens and emits [SettingsEvent.Disconnected] to trigger
     * navigation back to the home screen, which will in turn re-initiate the auth flow.
     *
     * After this call [TokenManager.getRefreshToken] returns `null`, causing
     * [com.example.wax.presentation.main.MainViewModel.checkAuthAndLoad] to emit
     * [com.example.wax.presentation.main.AuthEvent.LaunchAuth] on the next check.
     */
    fun disconnect() {
        viewModelScope.launch {
            tokenManager.clearTokens()
            _events.emit(SettingsEvent.Disconnected)
        }
    }
}
