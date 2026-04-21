package com.example.wax.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wax.data.local.AlbumHistoryEntity
import com.example.wax.data.repository.toAlbum
import com.example.wax.domain.model.Album

/** Near-black background consistent with the app's vinyl aesthetic. */
private val BackgroundColor = Color(0xFF0D0D0D)

/** Elevated surface color used as the album thumbnail placeholder background. */
private val SurfaceColor    = Color(0xFF1A1A1A)

/** Muted grey for secondary text: artist names and year badges. */
private val TextSecondary   = Color(0xFFAAAAAA)

/**
 * Screen that displays the user's album listening history stored in the local Room database.
 *
 * **Flow collection**: [HistoryViewModel.albums] is a `Flow<List<AlbumHistoryEntity>>` backed
 * by a Room DAO query. [collectAsStateWithLifecycle] subscribes to this flow in a
 * lifecycle-aware manner — the collection is automatically paused when the screen is stopped
 * and resumed when it returns to the started state, preventing unnecessary work while the
 * screen is in the background. Room emits a new list automatically whenever the database
 * changes (e.g. a new album is saved by [MainViewModel]), so the history list always reflects
 * the current state of the database without requiring a manual refresh.
 *
 * **[AlbumHistoryEntity] mapping**: [AlbumHistoryEntity] is a flat Room entity storing only
 * the fields needed for the list row: `id`, `title`, `artist`, `year`, `coverUrl`, and
 * serialised track/genre JSON. [toAlbum] (an extension function on the entity) converts the
 * entity back into a full [Album] domain object, deserialising the stored JSON, before
 * passing it to [onAlbumClick]. This keeps the navigation callback type-safe and ensures
 * [DetailScreen] receives a fully-populated [Album] without a second network call.
 *
 * **Navigation to DetailScreen**: When [onAlbumClick] fires, the nav graph calls
 * [MainViewModel.selectAlbum] with the reconstructed [Album], which writes it into
 * [MainUiState.album]. [DetailScreen] then reads from the same shared [MainViewModel]
 * state, so no serialisation through navigation arguments is required for the full
 * album object.
 *
 * **Empty state**: When [albums] is empty the list is replaced by a centred message
 * prompting the user to start listening. The [LazyColumn] is only instantiated when
 * there is at least one entry to avoid unnecessary composition.
 *
 * @param onAlbumClick Callback invoked when the user taps a row; receives the fully
 *                     reconstructed [Album] domain object converted from the Room entity.
 * @param viewModel    Hilt-injected [HistoryViewModel] that exposes the Room-backed flow.
 */
@Composable
fun HistoryScreen(
    onAlbumClick: (Album) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    // collectAsStateWithLifecycle suspends collection when the screen is not started,
    // saving resources while the user is on a different tab.
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Text(
            text = "History",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        if (albums.isEmpty()) {
            // ── Empty state ──────────────────────────────────────────────────
            // Shown on first launch before the user has played any albums via Wax
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No albums yet",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Albums you listen to will appear here",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // ── Album list ───────────────────────────────────────────────────
            // key = entity.id lets LazyColumn animate insertions/deletions and skip
            // re-composing rows whose data hasn't changed.
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(albums, key = { it.id }) { entity ->
                    AlbumHistoryRow(
                        entity = entity,
                        // toAlbum() deserialises the JSON fields stored in the Room entity
                        // back into a full Album domain object before navigating
                        onClick = { onAlbumClick(entity.toAlbum()) }
                    )
                }
            }
        }
    }
}

// ── Album row ──────────────────────────────────────────────────────────────────

/**
 * A single row in the history list representing one [AlbumHistoryEntity] stored in Room.
 *
 * **[AlbumHistoryEntity] → display mapping**:
 * - `entity.coverUrl` → 64×64 dp thumbnail image (with [SurfaceColor] placeholder background
 *   shown while the image loads from network or cache).
 * - `entity.title` → primary bold label, truncated to one line with ellipsis.
 * - `entity.artist` → secondary muted label, truncated to one line.
 * - `entity.year` → small badge on the right, styled with a [RoundedCornerShape] background.
 *
 * The thumbnail is clipped to `RoundedCornerShape(8.dp)` to match the album art style in
 * [DetailScreen]. [ContentScale.Crop] fills the square frame without letterboxing.
 *
 * Tapping anywhere on the row invokes [onClick], which calls [toAlbum] to reconstruct the
 * full [Album] domain object and passes it to the nav graph for [DetailScreen].
 *
 * @param entity  The Room entity containing the persisted album data for this row.
 * @param onClick Invoked when the user taps the row; triggers navigation to [DetailScreen].
 */
@Composable
private fun AlbumHistoryRow(
    entity: AlbumHistoryEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Thumbnail: SurfaceColor background is visible while Coil loads the image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(entity.coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Cover of ${entity.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceColor)  // placeholder while image loads
        )

        // Title and artist take all remaining horizontal space via weight(1f)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entity.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = entity.artist,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Year badge — always 4 characters wide, right-aligned
        Text(
            text = entity.year,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier
                .background(SurfaceColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
