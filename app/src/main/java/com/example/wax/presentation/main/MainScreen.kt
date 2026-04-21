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
import androidx.compose.material3.SheetValue
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


/** Spotify brand green; used for active/highlighted UI elements and the "Open in Spotify" button. */
private val SpotifyGreen    = Color(0xFF1DB954)

/** Near-black background that matches the physical vinyl aesthetic of the app. */
private val BackgroundColor = Color(0xFF0D0D0D)

/** Slightly lighter dark surface used as the bottom sheet background to lift it visually. */
private val SheetColor      = Color(0xFF161616)

/** Card/dialog surface color, one step lighter than [SheetColor]. */
private val SurfaceColor    = Color(0xFF1A1A1A)

/** Muted grey used for secondary labels (artist, year, track numbers, timestamps). */
private val TextSecondary   = Color(0xFFAAAAAA)

// ── Main screen ────────────────────────────────────────────────────────────────

/**
 * Root composable for the main turntable screen.
 *
 * Layout structure (top → bottom):
 * - [TurntableSection] — takes all remaining vertical space via `weight(1f)` so the vinyl
 *   platter scales gracefully across screen sizes without hard-coded heights.
 * - [PlaybackControls] — previous / play-pause / next buttons delegating to [MainViewModel].
 * - Album metadata block — title, artist, year; tapping navigates to the detail screen.
 * - "Open in Spotify" button — deep links to the Spotify app, falling back to the web URL.
 *
 * [BottomSheetScaffold] hosts the [TrackListSheet]. `sheetPeekHeight = 28.dp` is intentionally
 * small: it shows only the drag handle pill so users know the sheet is swipeable, without
 * obscuring the playback controls or the turntable when collapsed.
 *
 * A [LifecycleEventEffect] re-checks the notification listener permission on every `ON_RESUME`
 * so that the [NotificationPermissionDialog] dismisses automatically if the user grants access
 * from the system Settings screen and then returns to the app.
 *
 * System-bar insets are intentionally suppressed (`contentWindowInsets = WindowInsets(0)`)
 * because the outer NavGraph Scaffold already applies `innerPadding` to the NavHost container.
 *
 * @param onNavigateToDetail Callback invoked when the user taps the album metadata block.
 * @param viewModel          Hilt-injected [MainViewModel]; defaults to the hiltViewModel() instance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToDetail: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-check notification listener status every time the screen resumes so the dialog
    // auto-dismisses if the user granted access from the system Settings and returned.
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

        val scaffoldState = rememberBottomSheetScaffoldState()
        val isSheetExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded

        /**
         * BottomSheetScaffold is used instead of a plain ModalBottomSheet because it keeps
         * the sheet permanently anchored to the bottom of the screen (peekable), rather than
         * overlaying the content as a modal. This allows the tracklist to coexist with the
         * turntable rather than covering it entirely on open.
         *
         * sheetPeekHeight = 28.dp shows only the drag handle pill — just enough to signal
         * the sheet's presence without stealing vertical space from the turntable or controls.
         */
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetContent = {
                TrackListSheet(
                    tracks = uiState.album?.tracks ?: emptyList(),
                    currentTrackId = uiState.currentTrackId,
                    isPlaying = uiState.isPlaying,
                    isTracksLoading = uiState.isTracksLoading,
                    isExpanded = isSheetExpanded,
                    onTrackClick = { track ->
                        // Update the highlighted row immediately, then open Spotify
                        viewModel.onTrackSelected(track.id)
                        val trackUri = Uri.parse("spotify:track:${track.id}")
                        val intent = Intent(Intent.ACTION_VIEW, trackUri)
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            // Spotify app not installed — fall back to the web player
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://open.spotify.com/track/${track.id}"))
                            )
                        }
                    }
                )
            },
            sheetPeekHeight = 28.dp,
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
                /**
                 * weight(1f) makes TurntableSection consume all vertical space not claimed by
                 * siblings (controls, metadata, button). This lets the vinyl platter scale to
                 * any screen height without hard-coded dp values, preserving proportions on both
                 * compact and large displays.
                 *
                 * Internally, TurntableSection uses an Animatable<Float> for vinyl rotation.
                 * The rotation value is never reset to 0 when playback pauses — the animation
                 * simply stops at its current angle. Resetting to 0 would cause a visible
                 * snap-back jump; retaining the angle makes resume feel natural and physical.
                 *
                 * TurntableSection draws two Canvas layers:
                 *  - VinylCanvas: concentric circles for the record body, groove rings,
                 *    a highlight arc to simulate light reflection, and a centre label disc.
                 *  - TonearmCanvas: a pivot circle and a straight arm line rotated to rest
                 *    position (lifted) or playing position (lowered onto the groove).
                 */
                TurntableSection(
                    coverUrl = uiState.coverUrl,
                    isPlaying = uiState.isPlaying,
                    isSessionActive = uiState.isSessionActive,
                    turntableSkin = uiState.selectedSkin,
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

/**
 * Small pill-shaped label that indicates whether the displayed album comes from a live
 * Spotify session ("● Now Playing") or from the weekly curation algorithm ("Weekly Pick").
 *
 * The dot prefix and [SpotifyGreen] color make the live state visually distinct and convey
 * activity without needing an icon or animation.
 *
 * @param isNowPlaying True when [MainUiState.isNowPlaying] is set, indicating the album
 *                     was loaded from an active Spotify media session rather than the curator.
 */
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

/**
 * Modal dialog explaining why Wax needs the notification listener permission and offering
 * three response options: grant access, dismiss for this session, or permanently dismiss.
 *
 * The dialog is shown when [MainUiState.showNotificationPrompt] is true, which happens when
 * the permission has not been granted AND the user has not chosen "Don't ask again".
 *
 * @param onGrantAccess     Navigates to the system notification listener settings screen.
 * @param onNotNow          Hides the dialog for this session; it will reappear on next launch.
 * @param onDontAskAgain    Persistently dismisses the dialog by writing a DataStore flag.
 */
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

/**
 * Scrollable tracklist rendered inside the [BottomSheetScaffold]'s sheet content slot.
 *
 * Structure (top → bottom in the LazyColumn):
 * 1. **Drag handle pill** — always visible regardless of sheet state; signals swipeability.
 * 2. **Header** ("Tracklist" + count) — shown only when [isExpanded] is true to avoid
 *    cluttering the collapsed peek state.
 * 3. **Content** — either a [CircularProgressIndicator] while [isTracksLoading] with no
 *    tracks yet, or the list of [SheetTrackRow] items keyed by track ID for stable diffing.
 *
 * Highlighting logic: a row is considered "currently playing" when BOTH conditions hold:
 * - `track.id == currentTrackId` (matched by [MainViewModel]'s media session collector)
 * - `isPlaying == true` (playback is not paused)
 * This prevents the highlight from persisting while music is paused.
 *
 * @param tracks           The full ordered tracklist for the displayed album.
 * @param currentTrackId   Spotify ID of the track currently playing; null when nothing is matched.
 * @param isPlaying        Whether Spotify is actively playing (not paused).
 * @param isTracksLoading  True while tracks are being fetched; shows a spinner if [tracks] is empty.
 * @param isExpanded       True when the sheet is in the [SheetValue.Expanded] state.
 * @param onTrackClick     Callback invoked when the user taps a track row.
 */
@Composable
private fun TrackListSheet(
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    isTracksLoading: Boolean,
    isExpanded: Boolean,
    onTrackClick: (Track) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
    ) {
        // Drag handle — always visible so the user always knows the sheet is swipeable
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

        // Header — only when expanded to avoid cluttering the collapsed peek state
        if (isExpanded) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tracklist",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (!isTracksLoading) {
                        Text(text = "${tracks.size}", color = TextSecondary, fontSize = 13.sp)
                    }
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
            // key = track.id enables LazyColumn to efficiently diff and animate item changes
            items(tracks, key = { it.id }) { track ->
                SheetTrackRow(
                    track = track,
                    // Both conditions must hold: correct ID AND actively playing (not paused)
                    isCurrentlyPlaying = isPlaying && track.id == currentTrackId,
                    onClick = { onTrackClick(track) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

/**
 * A single row in [TrackListSheet] representing one track on the album.
 *
 * Highlighting: when [isCurrentlyPlaying] is true the row applies a subtle tinted background,
 * renders the track name in [SpotifyGreen] with [FontWeight.SemiBold], and replaces the
 * track-number label with an animated [SheetEqualizerBars] indicator. This gives immediate
 * visual feedback without requiring a separate "now playing" screen.
 *
 * Featured artists (all artists after index 0) are displayed as a secondary line below the
 * track name; the primary artist is omitted here because it is already shown in the album header.
 *
 * @param track              The domain [Track] to display.
 * @param isCurrentlyPlaying True when this track is the active playing track.
 * @param onClick            Callback invoked when the row is tapped.
 */
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
                // Replace the static track number with animated equalizer bars to
                // signal live playback activity at a glance.
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
            // Show featured artists (index > 0); the primary artist is in the album header
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

/**
 * Three animated vertical bars mimicking a classic audio equalizer indicator.
 *
 * Each bar oscillates independently between a min and max height fraction using
 * [infiniteRepeatable] tweens with different durations (600 ms, 400 ms, 500 ms) so they
 * never move in unison — this produces a natural, organic look rather than a mechanical pulse.
 *
 * Rendered at 14×14 dp so it fits inside the 28 dp track-number column without clipping.
 *
 * @param modifier Optional modifier applied to the root [Row].
 */
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

/**
 * Converts a track duration in milliseconds to a human-readable "m:ss" string.
 *
 * @param durationMs Track length in milliseconds as returned by the Spotify API.
 * @return Formatted string, e.g. `"3:45"` or `"12:04"`.
 */
private fun formatDuration(durationMs: Int): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

// ── Playback controls ──────────────────────────────────────────────────────────

/**
 * Row of media transport controls (previous, play/pause, next).
 *
 * Each button delegates directly to the corresponding [MainViewModel] method via the lambda
 * parameters — the composable itself holds no state. The play/pause icon swaps between
 * [Icons.Rounded.Pause] and [Icons.Rounded.PlayArrow] based on [isPlaying], keeping the
 * button in sync with the actual Spotify playback state from [MainUiState].
 *
 * The centre play/pause button is rendered at 44 dp (vs 32 dp for skip buttons) to create
 * a natural visual hierarchy matching standard media player conventions.
 *
 * @param isPlaying   Whether Spotify is currently playing; controls the icon displayed.
 * @param onPrevious  Invoked when the user taps the skip-previous button.
 * @param onPlayPause Invoked when the user taps the play/pause button.
 * @param onNext      Invoked when the user taps the skip-next button.
 */
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
