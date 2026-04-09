package com.example.wax.presentation.overlay

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wax.MainActivity
import com.example.wax.core.media.MediaSessionRepository
import com.example.wax.core.media.MediaSessionState
import com.example.wax.core.preferences.UserPreferencesRepository
import com.example.wax.domain.model.LockScreenTheme
import com.example.wax.presentation.common.AlbumArtLabel
import com.example.wax.presentation.common.VinylCanvas
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val SleeveBg = Color(0xFF080810)

@AndroidEntryPoint
class NowPlayingOverlayActivity : ComponentActivity() {

    @Inject lateinit var mediaSessionRepository: MediaSessionRepository
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            if (Settings.canDrawOverlays(this)) {
                val lp = window.attributes
                lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                window.attributes = lp
            }
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        super.onCreate(savedInstanceState)

        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isKeyguardLocked) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
            return
        }

        enableEdgeToEdge()

        setContent {
            val state by mediaSessionRepository.state.collectAsStateWithLifecycle()
            val theme by userPreferencesRepository.lockScreenTheme
                .collectAsStateWithLifecycle(initialValue = LockScreenTheme.FLOATING_VINYL)
            NowPlayingContent(
                state       = state,
                theme       = theme,
                onPlayPause = { mediaSessionRepository.sendPlayPause() },
                onNext      = { mediaSessionRepository.sendSkipToNext() },
                onPrevious  = { mediaSessionRepository.sendSkipToPrevious() },
                onDismiss   = { finish() }
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaSessionRepository.dismissLockScreen.collect { finish() }
            }
        }

        var wasPlaying = false
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaSessionRepository.state.collect { s ->
                    if (s.isPlaying) wasPlaying = true
                    if (!s.isPlaying && wasPlaying) finish()
                }
            }
        }
    }
}

// ── Theme dispatcher ───────────────────────────────────────────────────────────

@Composable
private fun NowPlayingContent(
    state: MediaSessionState,
    theme: LockScreenTheme,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) {
            while (true) {
                rotation.animateTo(
                    targetValue   = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                )
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }

    when (theme) {
        LockScreenTheme.SLEEVE -> SleeveThemeScreen(
            coverUrl    = state.coverUrl,
            trackTitle  = state.trackTitle ?: "",
            artistName  = state.artistName ?: "",
            rotation    = rotation.value,
            isPlaying   = state.isPlaying,
            onPlayPause = onPlayPause,
            onNext      = onNext,
            onPrevious  = onPrevious,
            onDismiss   = onDismiss
        )
        else -> SleeveThemeScreen(
            coverUrl    = state.coverUrl,
            trackTitle  = state.trackTitle ?: "",
            artistName  = state.artistName ?: "",
            rotation    = rotation.value,
            isPlaying   = state.isPlaying,
            onPlayPause = onPlayPause,
            onNext      = onNext,
            onPrevious  = onPrevious,
            onDismiss   = onDismiss
        )
    }
}

// ── Sleeve theme screen ────────────────────────────────────────────────────────

@Composable
fun SleeveThemeScreen(
    coverUrl: String,
    trackTitle: String,
    artistName: String,
    rotation: Float,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    var swipeDelta by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SleeveBg)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dy -> swipeDelta += dy },
                    onDragEnd = {
                        if (swipeDelta < -120.dp.toPx()) onDismiss()
                        swipeDelta = 0f
                    },
                    onDragCancel = { swipeDelta = 0f }
                )
            }
    ) {
        val coverHeight   = maxHeight * 0.60f
        val vinylDiameter = maxWidth  * 0.72f

        // ── Cover rectangle — bottom 60%, 85% wide, slightly tilted ──────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.85f)
                .height(coverHeight)
                .rotate(-2f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album cover",
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )
        }

        // ── Content column over the cover ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Clock
            SleeveClockWidget()

            // 2. Vinyl record — only top 55% visible (emerges from the sleeve)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(vinylDiameter)
                    .clip(TopFractionShape(0.55f))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(vinylDiameter)
                        .rotate(rotation)
                ) {
                    VinylCanvas(
                        dominantColor       = Color(0xFF1A1A1A),
                        vibrantColor        = Color(0xFF2A2A2A),
                        skinBaseColor       = Color(0xFF0D0D0D),
                        skinLabelColor      = Color(0xFF1C1C1C),
                        labelRadiusFraction = 0.35f,
                        modifier            = Modifier.fillMaxSize()
                    )
                    AlbumArtLabel(
                        coverUrl = coverUrl,
                        modifier = Modifier
                            .size(vinylDiameter * 0.35f)
                            .clip(CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(0xFF0D0D0D), CircleShape)
                    )
                }
            }

            // 3. Track info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = trackTitle,
                    color      = Color.White,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text      = artistName,
                    color     = Color.White.copy(alpha = 0.55f),
                    fontSize  = 13.sp,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )
            }

            // 4. Playback controls
            SleeveControls(
                isPlaying   = isPlaying,
                onPlayPause = onPlayPause,
                onNext      = onNext,
                onPrevious  = onPrevious
            )

            // 5. Dismiss hint
            Text(
                text          = "↑   swipe up to dismiss",
                color         = Color.White.copy(alpha = 0.25f),
                fontSize      = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight    = FontWeight.Light
            )
        }
    }
}

// ── Clip shape — keeps only the top `fraction` of any composable ──────────────

private class TopFractionShape(private val fraction: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Rectangle(
        Rect(left = 0f, top = 0f, right = size.width, bottom = size.height * fraction)
    )
}

// ── Clock ──────────────────────────────────────────────────────────────────────

@Composable
private fun SleeveClockWidget() {
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            time = LocalTime.now()
        }
    }
    Text(
        text          = time.format(DateTimeFormatter.ofPattern("HH:mm")),
        color         = Color.White,
        fontSize      = 48.sp,
        fontWeight    = FontWeight.Light,
        letterSpacing = (-2).sp,
        modifier      = Modifier.padding(top = 16.dp)
    )
}

// ── Controls ───────────────────────────────────────────────────────────────────

@Composable
private fun SleeveControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector        = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint               = Color.White,
                modifier           = Modifier.size(34.dp)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(Color.White, CircleShape)
                .clickable(onClick = onPlayPause)
        ) {
            Icon(
                imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint               = SleeveBg,
                modifier           = Modifier.size(26.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(
                imageVector        = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint               = Color.White,
                modifier           = Modifier.size(34.dp)
            )
        }
    }
}
