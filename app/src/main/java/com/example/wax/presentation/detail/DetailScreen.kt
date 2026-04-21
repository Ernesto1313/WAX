package com.example.wax.presentation.detail

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wax.domain.model.Album
import com.example.wax.domain.model.Track
import com.example.wax.presentation.main.MainViewModel

/** Spotify brand green; used for the artist name link and the "Open in Spotify" button. */
private val SpotifyGreen = Color(0xFF1DB954)

/** Near-black background consistent with the app's vinyl aesthetic. */
private val BackgroundColor = Color(0xFF0D0D0D)

/** Elevated surface color used for genre chips and dialog backgrounds. */
private val SurfaceColor = Color(0xFF1A1A1A)

/** Muted grey for secondary text: metadata labels, timestamps, featured artists. */
private val TextSecondary = Color(0xFFAAAAAA)

/**
 * Full-screen detail view for the currently loaded album.
 *
 * **Screen states** (evaluated in order inside the Scaffold lambda):
 * 1. **Loading** — [MainUiState.isLoading] is true: shows a centred [CircularProgressIndicator].
 * 2. **No album** — [MainUiState.album] is null (load failed or cleared): shows the error
 *    message and a "← Go back" link so the user is never stuck on a blank screen.
 * 3. **Content** — album is available: renders the full [LazyColumn] layout.
 *
 * **Why [LazyColumn] instead of `Column + verticalScroll`**: LazyColumn composes and lays out
 * only the items currently visible on screen. For an album with 15–20 tracks plus a large cover
 * image, this avoids composing every [TrackRow] upfront. `Column + verticalScroll` would require
 * the full height to be measured at once and all children to be composed eagerly, which is wasteful
 * and can cause jank on lower-end devices. LazyColumn also supports stable `key` parameters so
 * the framework can skip re-composing unchanged rows when [MainUiState.currentTrackId] changes.
 *
 * **LazyColumn structure** (items in order):
 * 1. Top bar (back button)
 * 2. Album cover image ([aspectRatio(1f)] square)
 * 3. [AlbumInfoSection] — title, artist, metadata, "Open in Spotify" button
 * 4. "Tracklist" section header
 * 5. One [TrackRow] per track (via [itemsIndexed])
 * 6. "About" section header
 * 7. [AboutSection] — genre chips, label, track count, release date
 * 8. Bottom spacer
 *
 * The screen shares [MainViewModel] with [MainScreen] so it reads the same [MainUiState]
 * including [MainUiState.currentTrackId] for track highlight without duplicating state.
 *
 * @param onNavigateBack Callback invoked when the back button or the "← Go back" link is tapped.
 * @param viewModel      Hilt-injected [MainViewModel]; shares state with the main turntable screen.
 */
@Composable
fun DetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val album = uiState.album

    Scaffold(containerColor = BackgroundColor) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SpotifyGreen)
                }
                return@Scaffold
            }
            album == null -> {
                // Album cleared or load failed — show error and an escape route
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "Album data unavailable",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "← Go back",
                            color = SpotifyGreen,
                            fontSize = 15.sp,
                            modifier = Modifier.clickable { onNavigateBack() }
                        )
                    }
                }
                return@Scaffold
            }
        }

        /**
         * LazyColumn composes only the visible items at a time, avoiding the cost of laying
         * out the entire screen (cover image + ~20 track rows + about section) upfront.
         * contentPadding = innerPadding applies the Scaffold's system-bar insets to the
         * list's scroll area so the first item is not obscured by the status bar.
         */
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding
        ) {
            // ── Top bar ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }

            // ── Album cover ──────────────────────────────────────────────────
            /**
             * The cover image is sized to the full available width via [fillMaxWidth] then
             * constrained to a 1:1 square via [aspectRatio(1f)]. Most album artwork is square,
             * and this constraint keeps the layout predictable regardless of the image's actual
             * pixel dimensions. [ContentScale.Crop] fills the rounded rectangle completely,
             * cropping any excess rather than letterboxing.
             */
            item {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(album.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)           // enforce square regardless of artwork dimensions
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            // ── Album info ───────────────────────────────────────────────────
            item {
                AlbumInfoSection(
                    album = album,
                    onOpenInSpotify = {
                val spotifyAppUri = Uri.parse("spotify:album:${album.id}")
                val spotifyWebUrl = album.spotifyUrl.ifEmpty {
                    "https://open.spotify.com/album/${album.id}"
                }
                val intent = Intent(Intent.ACTION_VIEW, spotifyAppUri)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    // Spotify app not installed — fall back to the web player
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spotifyWebUrl)))
                }
            },
                    onOpenArtist = {
                        val artistUrl = album.artistSpotifyUrls.firstOrNull() ?: return@AlbumInfoSection
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(artistUrl)))
                    }
                )
            }

            // ── Tracklist header ─────────────────────────────────────────────
            item { SectionHeader("Tracklist") }

            // ── Tracks ───────────────────────────────────────────────────────
            // itemsIndexed is used to have access to the index if needed in future;
            // isCurrentlyPlaying requires BOTH the correct ID AND active playback.
            itemsIndexed(album.tracks) { _, track ->
                TrackRow(
                    track = track,
                    isCurrentlyPlaying = uiState.isPlaying && track.id == uiState.currentTrackId
                )
            }

            // ── About header ─────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("About")
            }

            // ── About section ────────────────────────────────────────────────
            item { AboutSection(album) }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ── Album info section ─────────────────────────────────────────────────────────

/**
 * Displays the album title, artist name, condensed metadata line, and the
 * "Open in Spotify" deep-link button.
 *
 * The artist name is rendered in [SpotifyGreen] and is clickable; tapping it opens the
 * first artist's Spotify profile URL (from [Album.artistSpotifyUrls]) via [Intent.ACTION_VIEW].
 *
 * The metadata line composes `year · label · totalTracks tracks · totalDuration` in a single
 * [Text] to keep the layout compact. The label defaults to "—" when [Album.label] is empty
 * (some Spotify releases omit the label field).
 *
 * @param album           The [Album] domain object to display.
 * @param onOpenInSpotify Invoked when the "Open in Spotify" button is clicked.
 * @param onOpenArtist    Invoked when the artist name text is clicked.
 */
@Composable
private fun AlbumInfoSection(
    album: Album,
    onOpenInSpotify: () -> Unit,
    onOpenArtist: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 4.dp)
    ) {
        Text(
            text = album.name,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        // Artist name is a tappable link that opens the Spotify artist page
        Text(
            text = album.artistNames.joinToString(", "),
            color = SpotifyGreen,
            fontSize = 16.sp,
            modifier = Modifier.clickable(onClick = onOpenArtist)
        )

        Spacer(Modifier.height(6.dp))

        val year = album.releaseDate.take(4)
        val totalDuration = formatTotalDuration(album.tracks)
        val labelText = album.label.ifEmpty { "—" }  // Spotify omits label on some releases
        Text(
            text = "$year · $labelText · ${album.totalTracks} tracks · $totalDuration",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onOpenInSpotify,
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            shape = RoundedCornerShape(50),
            modifier = Modifier.height(48.dp),
            contentPadding = PaddingValues(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Open in Spotify",
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

// ── Section header ─────────────────────────────────────────────────────────────

/**
 * Bold white section title followed by a subtle [HorizontalDivider].
 *
 * Used to separate "Tracklist" and "About" content blocks within the [LazyColumn].
 * The divider at 8% white opacity matches the app-wide divider style without being
 * visually dominant.
 *
 * @param title The section heading text to display.
 */
@Composable
private fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = Color.White.copy(alpha = 0.08f)
        )
    }
}

// ── Track row ──────────────────────────────────────────────────────────────────

/**
 * A single row in the tracklist representing one track on the album.
 *
 * **Highlight logic**: [isCurrentlyPlaying] is true only when BOTH the track's ID matches
 * [MainUiState.currentTrackId] AND [MainUiState.isPlaying] is true. This prevents the
 * highlight lingering while Spotify is paused on that track. When highlighted:
 * - The row background gains a 4% white tint.
 * - The track name turns [SpotifyGreen] and becomes [FontWeight.SemiBold].
 * - The zero-padded track number is replaced by an animated [EqualizerBars] indicator.
 *
 * **Zero-padded track numbers**: `padStart(2, '0')` formats single-digit numbers as "01",
 * "02", etc., so all track numbers occupy the same width and the column stays vertically
 * aligned across the full tracklist.
 *
 * **Featured artists**: `track.artistNames.drop(1)` skips the primary (index 0) artist —
 * already shown in [AlbumInfoSection] — and displays only collaborators as a secondary line.
 * If the list has only one artist, `drop(1)` returns an empty list and nothing is rendered.
 *
 * **Lyrics link**: Tapping "View Lyrics" fires [Intent.ACTION_VIEW] with a Google search URL
 * built by [lyricsSearchUrl]. Using a search URL rather than a lyrics-specific service keeps
 * the implementation stable and doesn't require an additional API key. The browser or any
 * installed app that handles HTTP links will open the result.
 *
 * @param track              The domain [Track] to display.
 * @param isCurrentlyPlaying True when this track is the active playing track.
 */
@Composable
private fun TrackRow(track: Track, isCurrentlyPlaying: Boolean) {
    val context   = LocalContext.current
    val nameColor = if (isCurrentlyPlaying) SpotifyGreen else Color.White
    val rowBg     = if (isCurrentlyPlaying) Color.White.copy(alpha = 0.04f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Fixed 28 dp width: shows zero-padded number normally, animated bars when playing
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (isCurrentlyPlaying) {
                EqualizerBars()
            } else {
                // padStart(2, '0') keeps single-digit numbers right-aligned: "01", "02" …
                Text(
                    text = track.trackNumber.toString().padStart(2, '0'),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                color = nameColor,
                fontSize = 15.sp,
                fontWeight = if (isCurrentlyPlaying) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Featured artists: everyone beyond the primary album artist (index 0)
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
            // Lyrics link — fires ACTION_VIEW with a Google search URL for the track + artist
            Text(
                text = "View Lyrics",
                color = TextSecondary.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.clickable {
                    val artist = track.artistNames.firstOrNull() ?: ""
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(lyricsSearchUrl(track.name, artist)))
                    )
                }
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = formatDuration(track.durationMs),
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

// ── Animated equalizer bars ────────────────────────────────────────────────────

/**
 * Three animated vertical bars that replace the static track number when a track is
 * actively playing. Each bar oscillates between a min and max height fraction using
 * [infiniteRepeatable] tweens with different durations (600 ms, 400 ms, 500 ms) so the
 * bars never move in perfect unison, producing a natural equalizer appearance.
 *
 * Rendered at 14×14 dp to fit inside the 28 dp track-number column without clipping.
 *
 * @param modifier Optional modifier applied to the root [Row].
 */
@Composable
private fun EqualizerBars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    val h1 by transition.animateFloat(
        initialValue = 0.30f, targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val h2 by transition.animateFloat(
        initialValue = 1.00f, targetValue = 0.40f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.50f, targetValue = 0.90f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
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

// ── About section ──────────────────────────────────────────────────────────────

/**
 * Displays supplementary album metadata below the tracklist: genre chips, record label,
 * total track count, and full release date.
 *
 * **Genre chips — [LazyRow]**: Genres are rendered as horizontally scrollable pill-shaped
 * chips using [LazyRow] instead of a wrapped [Row]. Album genre lists from Spotify are
 * typically short (1–5 items), but [LazyRow] is preferred because it avoids measuring all
 * chip widths up-front and allows horizontal overflow without clipping on narrow screens.
 * Each genre string has its first character uppercased for consistent capitalisation since
 * Spotify returns genres in lowercase (e.g. "indie rock" → "Indie rock").
 *
 * Sections with empty data ([Album.genres], [Album.label]) are suppressed entirely rather
 * than showing empty or placeholder rows, keeping the UI clean for albums with incomplete
 * metadata.
 *
 * @param album The [Album] domain object whose metadata is displayed.
 */
@Composable
private fun AboutSection(album: Album) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Genre chips — LazyRow allows horizontal scrolling if chip list overflows the width
        if (album.genres.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(album.genres) { genre ->
                    Text(
                        // Spotify returns genres in lowercase; capitalise the first letter
                        text = genre.replaceFirstChar { it.uppercase() },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(SurfaceColor, RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Label is omitted entirely when empty (some Spotify releases have no label data)
        if (album.label.isNotEmpty()) {
            LabeledValue(label = "Label", value = album.label)
        }

        LabeledValue(label = "Total tracks", value = "${album.totalTracks} tracks")

        LabeledValue(
            label = "Release date",
            value = album.releaseDate
        )
    }
}

/**
 * A two-line block showing a small secondary [label] above a larger primary [value].
 *
 * Used inside [AboutSection] for consistent styling of metadata fields (Label, Total tracks,
 * Release date). The visual hierarchy — small grey label on top, larger white value below —
 * follows the convention of form-field labels in iOS / Material Design settings screens.
 *
 * @param label The descriptor for the field (e.g. "Label", "Release date").
 * @param value The data value to display below the label.
 */
@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(text = value, color = Color.White, fontSize = 15.sp)
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/**
 * Builds a Google search URL for the given [trackName] and [artistName] that will return
 * lyrics results at the top of the search page.
 *
 * The query string is formatted as `"<trackName> <artistName> lyrics"` with spaces
 * replaced by `+` to produce a valid URL query parameter. Using a Google search URL keeps
 * the implementation dependency-free and surfaces lyrics from multiple providers
 * (Genius, AZLyrics, etc.) without requiring a lyrics API key.
 *
 * @param trackName  The title of the track.
 * @param artistName The primary artist of the track.
 * @return A fully-formed Google search URL string.
 */
private fun lyricsSearchUrl(trackName: String, artistName: String): String =
    "https://www.google.com/search?q=" +
    "$trackName $artistName lyrics".trim().replace(" ", "+")

/**
 * Converts a track duration in milliseconds to a `"m:ss"` display string.
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

/**
 * Calculates and formats the total playback duration of all tracks in the list.
 *
 * Uses [Long] arithmetic to avoid integer overflow for albums with many long tracks
 * (e.g. a 20-track album where each track is ~5 min exceeds [Int.MAX_VALUE] milliseconds
 * only at extreme edge cases, but [Long] is used defensively).
 *
 * Returns `"Xh Ym"` for albums over 60 minutes, or `"Ym"` for shorter ones.
 *
 * @param tracks The list of [Track] objects whose [Track.durationMs] values are summed.
 * @return Human-readable total duration string, e.g. `"47m"` or `"1h 12m"`.
 */
private fun formatTotalDuration(tracks: List<Track>): String {
    val totalMs = tracks.sumOf { it.durationMs.toLong() }
    val totalMin = totalMs / 60_000
    val hours = totalMin / 60
    val mins = totalMin % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${totalMin}m"
}
