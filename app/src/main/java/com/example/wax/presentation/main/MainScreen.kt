package com.example.wax.presentation.main

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wax.domain.model.Track
import com.example.wax.domain.model.TurntableSkin
import com.example.wax.presentation.common.TurntableSection

private val SpotifyGreen    = Color(0xFF1DB954)
private val BackgroundColor = Color(0xFF0D0D0D)
private val SheetColor      = Color(0xFF161616)
private val SurfaceColor    = Color(0xFF1A1A1A)
private val TextSecondary   = Color(0xFFAAAAAA)

// ── Main screen ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToDetail: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-check notification listener status every time the screen resumes
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkNotificationListenerStatus()
    }

    if (uiState.showNotificationPrompt) {
        NotificationPermissionDialog(
            onGrantAccess = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
            onNotNow = { viewModel.dismissNotificationPrompt(permanent = false) },
            onDontAskAgain = { viewModel.dismissNotificationPrompt(permanent = true) }
        )
    }

    // contentWindowInsets = 0 because the outer NavGraph Scaffold already handles
    // system bar and bottom nav insets via Modifier.padding(innerPadding) on NavHost.
    Scaffold(
        containerColor = BackgroundColor,
        contentWindowInsets = WindowInsets(0)
    ) { scaffoldPadding ->

        BottomSheetScaffold(
            scaffoldState = rememberBottomSheetScaffoldState(),
            sheetContent = {
                TrackListSheet(
                    tracks = uiState.album?.tracks ?: emptyList(),
                    currentTrackId = uiState.currentTrackId,
                    isPlaying = uiState.isPlaying,
                    isTracksLoading = uiState.isTracksLoading,
                    onTrackClick = { track ->
                        viewModel.onTrackSelected(track.id)
                        val trackUri = Uri.parse("spotify:track:${track.id}")
                        val intent = Intent(Intent.ACTION_VIEW, trackUri)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://open.spotify.com/track/${track.id}"))
                            )
                        }
                    }
                )
            },
            sheetPeekHeight = 120.dp,
            sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            sheetMaxWidth = Dp.Unspecified,
            containerColor = BackgroundColor,
            sheetContainerColor = SheetColor,
            sheetTonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) { sheetPadding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(sheetPadding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TurntableSection(
                    coverUrl = uiState.coverUrl,
                    vinylDominantColor = uiState.vinylDominantColor,
                    vinylVibrantColor = uiState.vinylVibrantColor,
                    isPlaying = uiState.isPlaying,
                    isSessionActive = uiState.isSessionActive,
                    turntableSkin = uiState.turntableSkin,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                PlaybackControls(
                    isPlaying = uiState.isPlaying,
                    onPrevious = { viewModel.onPrevious() },
                    onPlayPause = { viewModel.onPlayPause() },
                    onNext = { viewModel.onNext() }
                )

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.08f)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onNavigateToDetail)
                ) {
                    AlbumSourceBadge(isNowPlaying = uiState.isNowPlaying)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.albumTitle,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${uiState.artistName} · ${uiState.year}",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        val albumId = uiState.album?.id ?: return@Button
                        val spotifyAppUri = Uri.parse("spotify:album:$albumId")
                        val spotifyWebUrl = uiState.spotifyUrl.ifEmpty {
                            "https://open.spotify.com/album/$albumId"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, spotifyAppUri)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spotifyWebUrl)))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(52.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Open in Spotify",
                        color = Color.Black,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(130.dp))
            }
        }
    }
}

// ── Album source badge ─────────────────────────────────────────────────────────

@Composable
private fun AlbumSourceBadge(isNowPlaying: Boolean) {
    val label = if (isNowPlaying) "● Now Playing" else "Weekly Pick"
    val textColor = if (isNowPlaying) SpotifyGreen else TextSecondary
    val bgColor = if (isNowPlaying) SpotifyGreen.copy(alpha = 0.12f)
                  else Color.White.copy(alpha = 0.06f)

    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ── Notification permission dialog ─────────────────────────────────────────────

@Composable
private fun NotificationPermissionDialog(
    onGrantAccess: () -> Unit,
    onNotNow: () -> Unit,
    onDontAskAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        containerColor = SurfaceColor,
        titleContentColor = Color.White,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Enable Notification Access",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = "Wax needs Notification Access to detect what's playing in Spotify " +
                        "and highlight the current track automatically.\n\n" +
                        "Tap \"Grant Access\", find Wax in the list, and toggle it on.",
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onGrantAccess,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                shape = RoundedCornerShape(50)
            ) {
                Text("Grant Access", color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onNotNow) {
                    Text("Not Now", color = TextSecondary)
                }
                TextButton(onClick = onDontAskAgain) {
                    Text("Don't ask again", color = TextSecondary.copy(alpha = 0.6f))
                }
            }
        }
    )
}

// ── Track list sheet ───────────────────────────────────────────────────────────

@Composable
private fun TrackListSheet(
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    isTracksLoading: Boolean,
    onTrackClick: (Track) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Tracklist", color = Color.White, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(6.dp))
                if (!isTracksLoading) {
                    Text(text = "${tracks.size}", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }

        // Show a spinner while tracks are loading instead of an empty list
        if (isTracksLoading && tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = SpotifyGreen,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                }
            }
        } else {
            items(tracks, key = { it.id }) { track ->
                SheetTrackRow(
                    track = track,
                    isCurrentlyPlaying = isPlaying && track.id == currentTrackId,
                    onClick = { onTrackClick(track) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun SheetTrackRow(
    track: Track,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit
) {
    val nameColor = if (isCurrentlyPlaying) SpotifyGreen else Color.White
    val rowBg = if (isCurrentlyPlaying) Color.White.copy(alpha = 0.04f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (isCurrentlyPlaying) {
                SheetEqualizerBars()
            } else {
                Text(
                    text = track.trackNumber.toString().padStart(2, '0'),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                color = nameColor,
                fontSize = 14.sp,
                fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val featured = track.artistNames.drop(1)
            if (featured.isNotEmpty()) {
                Text(
                    text = featured.joinToString(", "),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(text = formatDuration(track.durationMs), color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun SheetEqualizerBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq_sheet")
    val h1 by transition.animateFloat(
        initialValue = 0.30f, targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "s1"
    )
    val h2 by transition.animateFloat(
        initialValue = 1.00f, targetValue = 0.40f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "s2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.50f, targetValue = 0.90f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "s3"
    )

    Row(
        modifier = modifier.size(width = 14.dp, height = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(h1, h2, h3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .background(SpotifyGreen, RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
            )
        }
    }
}

private fun formatDuration(durationMs: Int): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

// ── Playback controls ──────────────────────────────────────────────────────────

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(44.dp)
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

