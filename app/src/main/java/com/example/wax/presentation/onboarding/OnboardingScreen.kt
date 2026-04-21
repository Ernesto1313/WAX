package com.example.wax.presentation.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wax.R

/** Near-black background consistent with the app's vinyl aesthetic. */
private val BackgroundColor = Color(0xFF0D0D0D)

/** Elevated surface color used as the permission card background. */
private val SurfaceColor    = Color(0xFF1A1A1A)

/** Spotify green used for the Continue button, granted-state icon tint, and checkmark. */
private val AccentGreen     = Color(0xFF1DB954)

/** Muted grey for secondary descriptive text and the icon background when not granted. */
private val TextSecondary   = Color(0xFFAAAAAA)

/**
 * One-time onboarding screen that requests the two permissions Wax needs for its advanced
 * features before the user reaches the main turntable screen.
 *
 * **Why it shows only once**: When the user taps "Continue", [OnboardingViewModel.completeOnboarding]
 * writes a boolean flag to DataStore via [UserPreferencesRepository.setOnboardingCompleted].
 * On every subsequent cold start, [MainViewModel] reads this flag from DataStore and the nav
 * graph skips the onboarding destination, sending the user directly to the main screen.
 *
 * **[LifecycleEventEffect] on [Lifecycle.Event.ON_RESUME]**: Both permissions are granted
 * in system Settings screens outside the app. [OnboardingViewModel.refreshPermissions] is
 * called every time the screen returns to the foreground so the [PermissionCard] status
 * indicators update automatically when the user grants a permission and then presses Back
 * to return to the onboarding screen, without requiring a manual refresh gesture.
 *
 * **Permissions explained**:
 * - **Notification listener access** ([Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS]):
 *   Required for `MediaNotificationListenerService` to receive Spotify playback notifications
 *   and populate [MediaSessionRepository] with the current track, artist, album, and
 *   playback state. Without this, the turntable vinyl doesn't know what's playing and can
 *   only show the weekly curated album with no live track highlighting.
 * - **Overlay / Draw over other apps** ([Settings.ACTION_MANAGE_OVERLAY_PERMISSION]):
 *   Required for [LockScreenActivity] to appear above the keyguard on certain OEM firmware
 *   (Samsung One UI, MIUI). Without this permission the lock-screen turntable feature is
 *   unavailable; the main turntable and track highlighting still work normally.
 *
 * **Continue button always enabled**: Both permissions are optional — Wax's core weekly-album
 * feature works without them. Blocking the Continue button until all permissions are granted
 * would be a dark pattern that frustrates users who don't want these features. The "You can
 * change these later in Settings" footer reinforces that the choice is revisable.
 *
 * @param onContinue Called after [OnboardingViewModel.completeOnboarding]; navigates to the
 *                   main turntable screen.
 * @param viewModel  Hilt-injected [OnboardingViewModel] tracking permission state.
 */
@Composable
fun OnboardingScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Refresh permission status each time the screen resumes so cards update automatically
    // after the user returns from a system Settings screen having granted access.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))

        // Vinyl icon — decorative, tint = Unspecified preserves the drawable's original colors
        Icon(
            painter = painterResource(R.drawable.ic_splash_vinyl),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(56.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Set up Wax",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Grant these permissions to get the full experience",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(40.dp))

        /**
         * Notification listener permission card.
         * Needed so MediaNotificationListenerService can read Spotify playback events and
         * drive live track highlighting and the lock-screen player. Without it, the app
         * shows only the weekly curated album with no real-time track awareness.
         */
        PermissionCard(
            icon        = Icons.Outlined.Notifications,
            title       = "Notification Access",
            description = "Lets Wax detect what's playing in Spotify and highlight the current track",
            isGranted   = uiState.hasNotifAccess,
            onGrant     = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        )

        Spacer(Modifier.height(16.dp))

        /**
         * Overlay / draw-over-apps permission card.
         * Required on certain OEM firmware for LockScreenActivity to render above the
         * keyguard. Without it the lock-screen turntable feature is silently unavailable,
         * but the rest of the app functions normally.
         */
        PermissionCard(
            icon        = Icons.Outlined.Lock,
            title       = "Lock Screen",
            description = "Shows the turntable on your lock screen while music is playing",
            isGranted   = uiState.hasOverlayPermission,
            onGrant     = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        // Package URI navigates to the per-app overlay toggle directly
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        )

        // Push the Continue button to the bottom of the screen
        Spacer(Modifier.weight(1f))

        // Always enabled — permissions are optional, blocking would be a dark UX pattern
        Button(
            onClick = {
                viewModel.completeOnboarding()  // write DataStore flag so this screen is skipped on next launch
                onContinue()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
        ) {
            Text(
                text = "Continue",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "You can change these later in Settings",
            color = TextSecondary,
            fontSize = 12.sp
        )

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * A card composable that displays one permission's status and provides a "Grant" button
 * when the permission has not yet been granted.
 *
 * **Visual states**:
 * - **Granted**: The icon background switches from a faint white tint to a green tint
 *   ([AccentGreen] at 15% opacity); the icon itself turns [AccentGreen]; a "✓" checkmark
 *   appears beside the title; the "Grant" button is hidden.
 * - **Not granted**: Neutral grey icon background (6% white), [TextSecondary] icon tint,
 *   and a [TextButton] labelled "Grant" appears below the description.
 *
 * **Status checking**: [isGranted] is driven by [OnboardingUiState] which is populated by
 * [OnboardingViewModel.refreshPermissions]. That method calls
 * [NotificationManagerCompat.getEnabledListenerPackages] for notification access and
 * [Settings.canDrawOverlays] for overlay permission — both are synchronous checks that
 * return the current system state without requiring a callback.
 *
 * **[onGrant]**: Fires an [Intent] to the appropriate system settings page. The result of
 * the user's action in Settings is observed via [LifecycleEventEffect(ON_RESUME)] in
 * [OnboardingScreen], which calls [OnboardingViewModel.refreshPermissions] on return.
 *
 * @param icon        The vector icon displayed in the circular badge on the left.
 * @param title       The permission name shown as the card's bold headline.
 * @param description Explanation of what the permission enables in Wax.
 * @param isGranted   Whether the permission is currently active on this device.
 * @param onGrant     Launches the system settings page for this permission.
 */
@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceColor)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Circular icon badge — tinted green when granted, neutral when not
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isGranted) AccentGreen.copy(alpha = 0.15f)
                    else Color.White.copy(alpha = 0.06f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) AccentGreen else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Checkmark replaces the Grant button once permission is active
                if (isGranted) {
                    Text(
                        text = "✓",
                        color = AccentGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = description,
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            // Grant button only shown when the permission is not yet active
            if (!isGranted) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = onGrant,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "Grant",
                        color = AccentGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
