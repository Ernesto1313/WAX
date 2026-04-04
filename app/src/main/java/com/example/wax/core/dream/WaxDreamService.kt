package com.example.wax.core.dream

import android.service.dreams.DreamService
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.wax.core.media.MediaSessionRepository
import com.example.wax.data.local.AlbumHistoryEntity
import com.example.wax.data.repository.AlbumHistoryRepository
import com.example.wax.domain.model.TurntableSkin
import com.example.wax.presentation.common.TurntableSection
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Android Dream (screen saver) shown while the device is charging or idle.
 *
 * Displays a gently spinning vinyl record with the album art currently loaded
 * in MediaSessionRepository. Falls back to the most recently saved album from
 * history when no session is active.
 *
 * The service registers as a LifecycleOwner + SavedStateRegistryOwner so that
 * Compose and StateFlow collection work correctly outside of an Activity context.
 */
@AndroidEntryPoint
class WaxDreamService : DreamService(), LifecycleOwner, SavedStateRegistryOwner {

    @Inject lateinit var mediaSessionRepository: MediaSessionRepository
    @Inject lateinit var albumHistoryRepository: AlbumHistoryRepository

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen  = true

        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val composeView = ComposeView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewTreeLifecycleOwner(this@WaxDreamService)
            setViewTreeSavedStateRegistryOwner(this@WaxDreamService)
            setContent {
                DreamScreen(
                    mediaSessionRepository = mediaSessionRepository,
                    albumHistoryRepository = albumHistoryRepository
                )
            }
        }

        setContentView(composeView)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDetachedFromWindow() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.INITIALIZED)) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        super.onDetachedFromWindow()
    }
}

// ── Dream screen ──────────────────────────────────────────────────────────────

@Composable
private fun DreamScreen(
    mediaSessionRepository: MediaSessionRepository,
    albumHistoryRepository: AlbumHistoryRepository
) {
    val mediaState    by mediaSessionRepository.state.collectAsState()
    val isSessionActive by mediaSessionRepository.isSessionActive.collectAsState()
    val history       by albumHistoryRepository.getAllAlbums().collectAsState(initial = emptyList<AlbumHistoryEntity>())

    // coverUrl from active session; fall back to most recent saved album
    val coverUrl = mediaState.coverUrl.ifEmpty { history.firstOrNull()?.coverUrl ?: "" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        TurntableSection(
            coverUrl           = coverUrl,
            vinylDominantColor = Color(0xFF1A1A1A),
            vinylVibrantColor  = Color(0xFF2A2A2A),
            isPlaying          = mediaState.isPlaying,
            isSessionActive    = isSessionActive,
            turntableSkin      = TurntableSkin.DARK,
            modifier           = Modifier
                .fillMaxWidth(0.80f)
                .aspectRatio(1f)
        )

        if (!mediaState.trackTitle.isNullOrEmpty()) {
            TrackInfo(
                title  = mediaState.trackTitle!!,
                artist = mediaState.artistName,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
                    .padding(horizontal = 40.dp)
            )
        }
    }
}

@Composable
private fun TrackInfo(
    title: String,
    artist: String?,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!artist.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = artist,
                color = Color.White.copy(alpha = 0.50f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
