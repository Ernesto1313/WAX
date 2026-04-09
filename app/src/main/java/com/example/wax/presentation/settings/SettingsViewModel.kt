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
import com.example.wax.core.work.WeeklyAlbumWorker
import com.example.wax.domain.model.LockScreenTheme
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val isSpotifyConnected: Boolean = false,
    val weeklyNotifEnabled: Boolean = true,
    val hasNotificationAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val lockScreenTheme: LockScreenTheme = LockScreenTheme.FLOATING_VINYL
)

sealed class SettingsEvent {
    data object Disconnected : SettingsEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val tokenManager: TokenManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        collectWeeklyNotif()
        collectLockScreenTheme()
        checkSpotifyConnection()
        refreshOverlayPermission()
    }

    // ── Init collectors ───────────────────────────────────────────────────────

    private fun collectWeeklyNotif() {
        viewModelScope.launch {
            userPreferencesRepository.weeklyNotifEnabled.collect { enabled ->
                _uiState.update { it.copy(weeklyNotifEnabled = enabled) }
            }
        }
    }

    private fun checkSpotifyConnection() {
        viewModelScope.launch {
            val connected = tokenManager.getRefreshToken() != null
            _uiState.update { it.copy(isSpotifyConnected = connected) }
        }
    }

    private fun collectLockScreenTheme() {
        viewModelScope.launch {
            userPreferencesRepository.lockScreenTheme.collect { theme ->
                _uiState.update { it.copy(lockScreenTheme = theme) }
            }
        }
    }

    // ── Public actions ────────────────────────────────────────────────────────

    /** Called on ON_RESUME so the notification access badge updates live. */
    fun refreshNotificationAccess() {
        val hasAccess = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
        _uiState.update { it.copy(hasNotificationAccess = hasAccess) }
    }

    /** Called on ON_RESUME so the overlay permission badge updates after the user returns from Settings. */
    fun refreshOverlayPermission() {
        _uiState.update { it.copy(hasOverlayPermission = Settings.canDrawOverlays(context)) }
    }

    fun setLockScreenTheme(theme: LockScreenTheme) {
        viewModelScope.launch { userPreferencesRepository.setLockScreenTheme(theme) }
    }

    fun setWeeklyNotifEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setWeeklyNotifEnabled(enabled)
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<WeeklyAlbumWorker>(7, TimeUnit.DAYS)
                    .setInitialDelay(7, TimeUnit.DAYS)
                    .build()
                wm.enqueueUniquePeriodicWork(
                    WeeklyAlbumWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request
                )
            } else {
                wm.cancelUniqueWork(WeeklyAlbumWorker.WORK_NAME)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            tokenManager.clearTokens()
            _events.emit(SettingsEvent.Disconnected)
        }
    }
}
