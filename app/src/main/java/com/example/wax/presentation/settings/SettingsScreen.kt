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

private val BackgroundColor = Color(0xFF0D0D0D)
private val SurfaceColor    = Color(0xFF1A1A1A)
private val SurfaceHigh     = Color(0xFF2A2A2A)
private val SpotifyGreen    = Color(0xFF1DB954)
private val TextSecondary   = Color(0xFFAAAAAA)
private val DangerRed       = Color(0xFFE05252)

@Composable
fun SettingsScreen(
    onDisconnected: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDisconnectDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationAccess()
        viewModel.refreshOverlayPermission()
    }

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
        SectionHeader("Notifications")

        SettingsRow {
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekly album reminder", color = Color.White, fontSize = 15.sp)
                Text(
                    "Every Sunday at 9:00",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(12.dp))
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

        SettingsRow {
            Column(modifier = Modifier.weight(1f)) {
                val accessLabel = if (uiState.hasNotificationAccess) "Granted" else "Not granted"
                val accessColor = if (uiState.hasNotificationAccess) SpotifyGreen else DangerRed
                Text("Notification listener access", color = Color.White, fontSize = 15.sp)
                Text(accessLabel, color = accessColor, fontSize = 13.sp)
            }
            Spacer(Modifier.width(12.dp))
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
        SectionHeader("Appearance")

        SkinSelector(
            selectedSkin = uiState.selectedSkin,
            onSkinSelected = { viewModel.setSelectedSkin(it) }
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(8.dp))

        // ── Display ───────────────────────────────────────────────────────────
        SectionHeader("Display")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
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
                .padding(3.dp)
                .clip(CircleShape)
        ) {
            VinylCanvas(
                skin                = skin,
                labelRadiusFraction = 0.30f,
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
