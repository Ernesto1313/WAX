package com.example.wax.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wax.BuildConfig
import com.example.wax.domain.model.TurntableSkin
import com.example.wax.presentation.common.VinylCanvas
import com.example.wax.presentation.common.displayName

/** Near-black background consistent with the app's vinyl aesthetic. */
private val BackgroundColor = Color(0xFF0D0D0D)

/** Elevated surface color for dialogs and chip backgrounds. */
private val SurfaceColor    = Color(0xFF1A1A1A)

/** Slightly lighter surface used as the Switch unchecked track fill. */
private val SurfaceHigh     = Color(0xFF2A2A2A)

/** Spotify brand green; used for active states, selected skins, and granted permission labels. */
private val SpotifyGreen    = Color(0xFF1DB954)

/** Muted grey for secondary labels and section headers. */
private val TextSecondary   = Color(0xFFAAAAAA)

/** Red used for destructive actions (Disconnect button) and denied permission labels. */
private val DangerRed       = Color(0xFFE05252)

/**
 * Settings screen providing controls for Spotify connection, notifications, appearance,
 * display permissions, and app information.
 *
 * **Sections**:
 * - **Spotify** — shows connection status and a "Disconnect" button when linked.
 * - **Notifications** — weekly album reminder toggle and notification listener access status.
 * - **Appearance** — turntable skin selector ([SkinSelector]).
 * - **Display** — overlay permission row for lock-screen turntable support.
 * - **About** — app version name from [BuildConfig.VERSION_NAME].
 *
 * **[LifecycleEventEffect] on [Lifecycle.Event.ON_RESUME]**: Re-checks both the notification
 * listener permission and the overlay permission every time the screen becomes visible again.
 * This is necessary because both permissions are granted in system Settings screens outside
 * the app — the status can only be verified after the user returns. Without this, the UI
 * would show a stale "Not granted" label even after the user grants access.
 *
 * **[LaunchedEffect] for [SettingsEvent]**: Collects the one-shot [SettingsViewModel.events]
 * shared flow. When [SettingsEvent.Disconnected] arrives (after tokens are cleared), it calls
 * [onDisconnected] to trigger navigation to the auth screen. This is a one-shot event rather
 * than UI state because navigation is imperative and should not be re-triggered on
 * recomposition.
 *
 * **Disconnect flow**: Tapping "Disconnect" sets a local `showDisconnectDialog` flag to show
 * the [DisconnectDialog]. On confirmation, [SettingsViewModel.disconnect] is called, which
 * clears stored OAuth tokens from secure storage and emits [SettingsEvent.Disconnected],
 * causing the nav graph to navigate back to the auth/onboarding flow.
 *
 * **Weekly notification toggle**: [Switch] state is bound to [SettingsUiState.weeklyNotifEnabled].
 * Toggling it calls [SettingsViewModel.setWeeklyNotifEnabled], which schedules or cancels a
 * WorkManager [PeriodicWorkRequest] that fires a local notification every Sunday at 9:00.
 *
 * **Notification listener access**: [SettingsUiState.hasNotificationAccess] is checked via
 * [NotificationManagerCompat.getEnabledListenerPackages] in [SettingsViewModel]. The "Manage"
 * button opens [Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS] so the user can grant access.
 *
 * **Overlay permission**: [SettingsUiState.hasOverlayPermission] is checked via
 * [Settings.canDrawOverlays] in [SettingsViewModel]. The entire Display row is clickable and
 * opens [Settings.ACTION_MANAGE_OVERLAY_PERMISSION] with the app's package URI, navigating
 * directly to the per-app overlay toggle rather than the generic permission list.
 *
 * @param onDisconnected Callback invoked after the Spotify tokens are successfully cleared;
 *                       should navigate the user back to the auth or onboarding screen.
 * @param viewModel      Hilt-injected [SettingsViewModel] managing all settings state.
 */
@Composable
fun SettingsScreen(
    onDisconnected: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Local flag drives DisconnectDialog visibility; kept in composition rather than ViewModel
    // because it is pure UI state that does not need to survive process death.
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Re-check both runtime permissions every time the screen resumes so labels update
    // automatically after the user returns from the system Settings screen.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationAccess()
        viewModel.refreshOverlayPermission()
    }

    // Collect one-shot navigation events from the ViewModel; navigate on Disconnected
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Disconnected -> onDisconnected()
            }
        }
    }

    if (showDisconnectDialog) {
        DisconnectDialog(
            onConfirm = { showDisconnectDialog = false; viewModel.disconnect() },
            onDismiss = { showDisconnectDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
    ) {
        // ── Page title ────────────────────────────────────────────────────────
        Text(
            text = "Settings",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(8.dp))

        // ── Spotify ───────────────────────────────────────────────────────────
        // Shows connection status. When connected, a Disconnect button is shown.
        // Tapping Disconnect shows a confirmation dialog before clearing tokens.
        SectionHeader("Spotify")

        SettingsRow {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uiState.isSpotifyConnected) "Connected" else "Not connected",
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = if (uiState.isSpotifyConnected) "Your Spotify account is linked"
                    else "Sign in to load your weekly album",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            if (uiState.isSpotifyConnected) {
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = { showDisconnectDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Disconnect", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(8.dp))

        // ── Notifications ─────────────────────────────────────────────────────
        // Two rows:
        // 1. Weekly reminder toggle — schedules/cancels a WorkManager PeriodicWorkRequest.
        // 2. Notification listener access status — required for media session detection.
        SectionHeader("Notifications")

        SettingsRow {
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekly album reminder", color = Color.White, fontSize = 15.sp)
                Text(
                    // WorkManager fires this task every Sunday at 9:00
                    "Every Sunday at 9:00",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            // Toggling the Switch calls ViewModel which schedules or cancels the WorkManager job
            Switch(
                checked = uiState.weeklyNotifEnabled,
                onCheckedChange = { viewModel.setWeeklyNotifEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor   = Color.White,
                    checkedTrackColor   = SpotifyGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = SurfaceHigh
                )
            )
        }

        // Notification listener access — checked via NotificationManagerCompat.getEnabledListenerPackages
        SettingsRow {
            Column(modifier = Modifier.weight(1f)) {
                val accessLabel = if (uiState.hasNotificationAccess) "Granted" else "Not granted"
                val accessColor = if (uiState.hasNotificationAccess) SpotifyGreen else DangerRed
                Text("Notification listener access", color = Color.White, fontSize = 15.sp)
                Text(accessLabel, color = accessColor, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
            // Opens the system notification listener settings page for the user to toggle access
            TextButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            ) {
                Text("Manage", color = SpotifyGreen, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(8.dp))

        // ── Appearance ────────────────────────────────────────────────────────
        // TurntableSkin selector: three vinyl preview cards that persist the selection
        // to DataStore via SettingsViewModel.setSelectedSkin.
        SectionHeader("Appearance")

        SkinSelector(
            selectedSkin = uiState.selectedSkin,
            onSkinSelected = { viewModel.setSelectedSkin(it) }
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(8.dp))

        // ── Display ───────────────────────────────────────────────────────────
        // Overlay permission — required by some OEM launchers (Samsung One UI, MIUI) for
        // the lock-screen turntable feature. Checked via Settings.canDrawOverlays(context).
        // The entire row is clickable and opens ACTION_MANAGE_OVERLAY_PERMISSION with the
        // app's package URI so the user lands directly on the per-app toggle.
        SectionHeader("Display")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            // Package URI required to navigate to the per-app overlay toggle
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Display over lock screen", color = Color.White, fontSize = 15.sp)
                Text(
                    "Required to show the turntable on Samsung One UI and MIUI",
                    color    = TextSecondary,
                    fontSize = 13.sp
                )
                // Status label changes color and message based on Settings.canDrawOverlays result
                if (uiState.hasOverlayPermission) {
                    Text("Granted", color = SpotifyGreen, fontSize = 12.sp)
                    Text(
                        "To disable, tap to open settings",
                        color    = TextSecondary,
                        fontSize = 11.sp
                    )
                } else {
                    Text("Not granted", color = DangerRed, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(8.dp))

        // ── About ─────────────────────────────────────────────────────────────
        SectionHeader("About")

        SettingsRow {
            Text("Version", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            // VERSION_NAME comes from BuildConfig, injected at compile time via build.gradle
            Text(BuildConfig.VERSION_NAME, color = TextSecondary, fontSize = 15.sp)
        }

        SettingsRow {
            Text(
                text = "Wax · Made with ♥",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Skin selector ──────────────────────────────────────────────────────────────

/**
 * Horizontal row of [SkinCard] items, one per [TurntableSkin] variant.
 *
 * Iterates [TurntableSkin.entries] (the full ordered enum list) and renders a card for each.
 * Each card is given equal horizontal weight via `Modifier.weight(1f)` so all three always
 * fill the row evenly regardless of the display width.
 *
 * When the user taps a card, [onSkinSelected] is called with the selected skin, which triggers
 * [SettingsViewModel.setSelectedSkin]. The ViewModel writes the new value to DataStore via
 * [UserPreferencesRepository], and [MainViewModel] — which collects the same DataStore flow —
 * updates [MainUiState.selectedSkin], causing the turntable on the main screen to immediately
 * re-render with the new skin colors.
 *
 * @param selectedSkin   The currently active [TurntableSkin] from [SettingsUiState].
 * @param onSkinSelected Callback invoked with the newly chosen skin when a card is tapped.
 */
@Composable
private fun SkinSelector(
    selectedSkin: TurntableSkin,
    onSkinSelected: (TurntableSkin) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TurntableSkin.entries.forEach { skin ->
            SkinCard(
                skin = skin,
                isSelected = skin == selectedSkin,
                onClick = { onSkinSelected(skin) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * A tappable card showing a miniature [VinylCanvas] preview of a single [TurntableSkin].
 *
 * **Selection indicator**: A [SpotifyGreen] border (2 dp) is drawn around the circular
 * preview when [isSelected] is true; otherwise a nearly transparent white border keeps the
 * visual boundary without drawing attention. The label below the preview mirrors this —
 * green + SemiBold when selected, grey + Normal otherwise.
 *
 * **[VinylCanvas] preview**: Rendered at 72×72 dp with `labelRadiusFraction = 0.30f` (slightly
 * larger label disc than the main 0.28f to remain legible at this reduced size). The canvas
 * is clipped to [CircleShape] and padded by 3 dp inside the border so the vinyl does not
 * overlap the selection ring.
 *
 * Tapping the card calls [onClick], which propagates up to [SkinSelector] and ultimately
 * to [SettingsViewModel.setSelectedSkin] for DataStore persistence.
 *
 * @param skin       The [TurntableSkin] this card represents.
 * @param isSelected Whether this skin is the currently active selection.
 * @param onClick    Invoked when the user taps this card.
 * @param modifier   Applied to the root [Column]; caller sets `Modifier.weight(1f)`.
 */
@Composable
private fun SkinCard(
    skin: TurntableSkin,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) SpotifyGreen else Color.White.copy(alpha = 0.12f)

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .border(2.dp, borderColor, CircleShape)
                .padding(3.dp)   // gap between selection ring and vinyl canvas
                .clip(CircleShape)
        ) {
            VinylCanvas(
                skin                = skin,
                labelRadiusFraction = 0.30f,  // slightly larger label for readability at 72 dp
                modifier            = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = skin.displayName(),
            color = if (isSelected) SpotifyGreen else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/**
 * Renders an all-caps section label with wide letter spacing, used to group related settings
 * rows (e.g. "SPOTIFY", "NOTIFICATIONS", "APPEARANCE").
 *
 * The uppercase + 1.2 sp letter spacing style matches the Material Design convention for
 * settings category headers without requiring a dedicated headline text style.
 *
 * @param title The section name to display; automatically uppercased by the composable.
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

/**
 * A horizontally-arranged container with consistent horizontal padding (20 dp) and vertical
 * padding (12 dp), used to wrap the content of every settings row.
 *
 * Extracted to ensure all rows share identical insets and alignment without repeating the
 * same [Row] + [Modifier] boilerplate at every call site.
 *
 * @param content The composable content of the row, typically a [Text] + control pair.
 */
@Composable
private fun SettingsRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/**
 * Confirmation dialog shown before clearing Spotify OAuth tokens.
 *
 * The two-step confirmation (tap "Disconnect" → dialog → confirm) prevents accidental
 * disconnection, which would require the user to go through the full OAuth PKCE flow again.
 *
 * Confirming calls [SettingsViewModel.disconnect], which:
 * 1. Clears the access and refresh tokens from secure storage via [TokenManager.clearTokens].
 * 2. Emits [SettingsEvent.Disconnected], causing [SettingsScreen] to call [onDisconnected]
 *    and navigate the user back to the auth screen.
 *
 * @param onConfirm Invoked when the user taps the red "Disconnect" button.
 * @param onDismiss Invoked when the user taps "Cancel" or dismisses the dialog.
 */
@Composable
private fun DisconnectDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        titleContentColor = Color.White,
        textContentColor = TextSecondary,
        title = { Text("Disconnect Spotify?", fontWeight = FontWeight.Bold) },
        text = { Text("You'll need to sign in again to load albums.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Text("Disconnect", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
