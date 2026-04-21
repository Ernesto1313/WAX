package com.example.wax.presentation.onboarding

import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wax.core.preferences.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable snapshot of the two permission states shown on [OnboardingScreen].
 *
 * Both fields start as `false` and are updated synchronously by [OnboardingViewModel.refreshPermissions].
 *
 * @property hasNotifAccess      True when the app's notification listener service is enabled
 *                               (checked via [NotificationManagerCompat.getEnabledListenerPackages]).
 * @property hasOverlayPermission True when the app holds the "Draw over other apps" permission
 *                                (checked via [Settings.canDrawOverlays]).
 */
data class OnboardingUiState(
    val hasNotifAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false
)

/**
 * ViewModel for [OnboardingScreen].
 *
 * Responsible for:
 * - Checking both runtime permissions synchronously and exposing results via [uiState].
 * - Writing the onboarding-completed flag to DataStore when the user taps "Continue".
 *
 * @param userPreferencesRepository DataStore-backed repository used to persist the
 *                                  one-time onboarding completion flag.
 * @param context                   Application context; used for permission checks that
 *                                  require a [Context] ([NotificationManagerCompat],
 *                                  [Settings.canDrawOverlays]).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Backing mutable state; all updates go through [_uiState.update]. */
    private val _uiState = MutableStateFlow(OnboardingUiState())

    /** Public read-only state consumed by [OnboardingScreen]. */
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /**
     * Performs an initial permission check as soon as the ViewModel is created so the
     * cards display accurate status from the very first composition, before the first
     * [Lifecycle.Event.ON_RESUME] fires.
     */
    init {
        refreshPermissions()
    }

    /**
     * Synchronously reads the current state of both permissions and updates [uiState].
     *
     * Called once from [init] and again on every [Lifecycle.Event.ON_RESUME] in
     * [OnboardingScreen] so the UI reflects changes made in system Settings without
     * requiring the user to restart the app.
     *
     * **Notification listener check**: [NotificationManagerCompat.getEnabledListenerPackages]
     * returns a [Set] of package names for all apps that currently hold the notification
     * listener permission. Checking [Context.packageName] membership is the only reliable
     * way to verify this permission — [ContextCompat.checkSelfPermission] does not apply
     * to notification listener access because it is not a manifest permission.
     *
     * **Overlay check**: [Settings.canDrawOverlays] returns `true` when the app has been
     * granted the `SYSTEM_ALERT_WINDOW` permission via the system Settings UI. This is
     * also a special permission that cannot be checked with the standard permission APIs.
     *
     * Both checks are synchronous and inexpensive; no coroutine is needed.
     */
    fun refreshPermissions() {
        val hasNotif = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
        val hasOverlay = Settings.canDrawOverlays(context)
        _uiState.update { it.copy(hasNotifAccess = hasNotif, hasOverlayPermission = hasOverlay) }
    }

    /**
     * Writes the onboarding-completed flag to DataStore, causing the nav graph to skip
     * [OnboardingScreen] on all future cold starts.
     *
     * Called immediately before [OnboardingScreen] invokes its `onContinue` callback so
     * the flag is guaranteed to be persisted before navigation occurs. The write is
     * launched in [viewModelScope] on the default IO dispatcher (DataStore handles this
     * internally); the navigation proceeds immediately without waiting for the write to
     * complete, which is safe because the flag only matters on the next app launch.
     */
    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted()
        }
    }
}
