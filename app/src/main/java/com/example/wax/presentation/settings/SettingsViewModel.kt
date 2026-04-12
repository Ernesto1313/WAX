package com.example.wax.presentation.settings

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wax.core.auth.TokenManager
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
import javax.inject.Inject

data class SettingsUiState(
    val isSpotifyConnected: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false
)

sealed class SettingsEvent {
    data object Disconnected : SettingsEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
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

    fun disconnect() {
        viewModelScope.launch {
            tokenManager.clearTokens()
            _events.emit(SettingsEvent.Disconnected)
        }
    }
}
