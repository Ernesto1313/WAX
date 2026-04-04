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

private val BackgroundColor = Color(0xFF0D0D0D)
private val SurfaceColor    = Color(0xFF1A1A1A)
private val TextSecondary   = Color(0xFFAAAAAA)

@Composable
fun HistoryScreen(
    onAlbumClick: (Album) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
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
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(albums, key = { it.id }) { entity ->
                    AlbumHistoryRow(
                        entity = entity,
                        onClick = { onAlbumClick(entity.toAlbum()) }
                    )
                }
            }
        }
    }
}

// ── Album row ──────────────────────────────────────────────────────────────────

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
                .background(SurfaceColor)
        )

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
