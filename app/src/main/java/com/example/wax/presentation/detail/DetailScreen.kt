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

private val SpotifyGreen = Color(0xFF1DB954)
private val BackgroundColor = Color(0xFF0D0D0D)
private val SurfaceColor = Color(0xFF1A1A1A)
private val TextSecondary = Color(0xFFAAAAAA)

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
                        .aspectRatio(1f)
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

        Text(
            text = album.artistNames.joinToString(", "),
            color = SpotifyGreen,
            fontSize = 16.sp,
            modifier = Modifier.clickable(onClick = onOpenArtist)
        )

        Spacer(Modifier.height(6.dp))

        val year = album.releaseDate.take(4)
        val totalDuration = formatTotalDuration(album.tracks)
        val labelText = album.label.ifEmpty { "—" }
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
        // Track number or animated equalizer
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (isCurrentlyPlaying) {
                EqualizerBars()
            } else {
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
            // Featured artists: everyone beyond the primary album artist
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
            // Lyrics link
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

@Composable
private fun AboutSection(album: Album) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (album.genres.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(album.genres) { genre ->
                    Text(
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

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(text = value, color = Color.White, fontSize = 15.sp)
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────

private fun lyricsSearchUrl(trackName: String, artistName: String): String =
    "https://www.google.com/search?q=" +
    "$trackName $artistName lyrics".trim().replace(" ", "+")

private fun formatDuration(durationMs: Int): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "$min:${sec.toString().padStart(2, '0')}"
}

private fun formatTotalDuration(tracks: List<Track>): String {
    val totalMs = tracks.sumOf { it.durationMs.toLong() }
    val totalMin = totalMs / 60_000
    val hours = totalMin / 60
    val mins = totalMin % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${totalMin}m"
}
