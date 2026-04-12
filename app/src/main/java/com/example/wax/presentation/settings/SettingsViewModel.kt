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

data class SettingsUiState(
    val isSpotifyConnected: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val weeklyNotifEnabled: Boolean = true,
    val selectedSkin: TurntableSkin = TurntableSkin.DARK
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

    private fun checkSpotifyConnection() {
        viewModelScope.launch {
            val connected = tokenManager.getRefreshToken() != null
            _uiState.update { it.copy(isSpotifyConnected = connected) }
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

    fun setWeeklyNotifEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setWeeklyNotifEnabled(enabled)
            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                val now = Calendar.getInstance()
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

    fun setSelectedSkin(skin: TurntableSkin) {
        viewModelScope.launch { userPreferencesRepository.setSelectedSkin(skin) }
    }

    fun disconnect() {
        viewModelScope.launch {
            tokenManager.clearTokens()
            _events.emit(SettingsEvent.Disconnected)
        }
    }
}
