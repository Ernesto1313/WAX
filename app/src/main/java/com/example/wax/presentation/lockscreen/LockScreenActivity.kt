package com.example.wax.presentation.lockscreen

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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val LockBg    = Color(0xFF080810)
private val VinylBody = Color(0xFF0D0D0D)

@AndroidEntryPoint
class LockScreenActivity : ComponentActivity() {

    @Inject lateinit var mediaSessionRepository: MediaSessionRepository
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // All window flags before super.onCreate so they take effect on first frame.
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

        // If the phone is already unlocked (e.g. user tapped the notification while
        // the screen was on), skip straight to the main app.
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
                .collectAsStateWithLifecycle(initialValue = LockScreenTheme.CLASSIC)
            LockScreenContent(
                state       = state,
                theme       = theme,
                onPlayPause = { mediaSessionRepository.sendPlayPause() },
                onNext      = { mediaSessionRepository.sendSkipToNext() },
                onPrevious  = { mediaSessionRepository.sendSkipToPrevious() },
                onDismiss   = { finish() }
            )
        }

        // Dismiss when user unlocks (signal from MediaNotificationListenerService).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaSessionRepository.dismissLockScreen.collect { finish() }
            }
        }

        // Auto-dismiss when music stops.
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

// ── Lock screen content — theme dispatcher ─────────────────────────────────────

@Composable
private fun LockScreenContent(
    state: MediaSessionState,
    theme: LockScreenTheme,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    // Vinyl rotation shared by themes that accept it externally.
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
        LockScreenTheme.WAVEFORM -> WaveformThemeScreen(
            coverUrl    = state.coverUrl,
            trackTitle  = state.trackTitle ?: "",
            artistName  = state.artistName ?: "",
            isPlaying   = state.isPlaying,
            onPlayPause = onPlayPause,
            onNext      = onNext,
            onPrevious  = onPrevious,
            onDismiss   = onDismiss
        )
        else -> FloatingVinylScreen(
            state       = state,
            onPlayPause = onPlayPause,
            onNext      = onNext,
            onPrevious  = onPrevious,
            onDismiss   = onDismiss
        )
    }
}

// ── Floating Vinyl theme (default) ─────────────────────────────────────────────

@Composable
private fun FloatingVinylScreen(
    state: MediaSessionState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    val screenAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { screenAlpha.animateTo(1f, tween(400)) }

    var swipeDelta by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LockBg)
            .alpha(screenAlpha.value)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> swipeDelta += amount },
                    onDragEnd = {
                        if (swipeDelta < -120.dp.toPx()) onDismiss()
                        swipeDelta = 0f
                    },
                    onDragCancel = { swipeDelta = 0f }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            LockClock()

            LockVinyl(
                coverUrl  = state.coverUrl,
                isPlaying = state.isPlaying,
                modifier  = Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(1f)
            )

            // Track info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = state.trackTitle ?: "",
                    color      = Color.White,
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text      = state.artistName ?: "",
                    color     = Color.White.copy(alpha = 0.6f),
                    fontSize  = 13.sp,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth()
                )
            }

            LockControls(
                isPlaying   = state.isPlaying,
                onPlayPause = onPlayPause,
                onNext      = onNext,
                onPrevious  = onPrevious
            )

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

// ── Sleeve theme ───────────────────────────────────────────────────────────────
// Vinyl slides out of the album sleeve: cover art fills the bottom 65%,
// vinyl center sits at the sleeve's top edge so half is visible, half hidden.

@Composable
private fun SleeveThemeScreen(
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
            .background(LockBg)
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
        val vinylDiameter = maxWidth * 0.72f
        val sleeveTopY    = maxHeight * 0.35f          // sleeve starts at 35% from top
        val vinylOffsetX  = (maxWidth - vinylDiameter) / 2f
        val vinylOffsetY  = sleeveTopY - vinylDiameter / 2f  // vinyl center = sleeve top

        // 1. Vinyl — drawn first so the sleeve (drawn next) covers its bottom half
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(x = vinylOffsetX, y = vinylOffsetY)
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
                    .background(VinylBody, CircleShape)
            )
        }

        // 2. Sleeve — bottom 65%, full width, rounded top corners 20dp.
        //    Natural z-order places it over the vinyl's bottom half.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.65f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
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
            // Dark gradient so bottom text is readable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.80f)
                            )
                        )
                    )
            )
            // Track info and controls pinned to the bottom of the sleeve
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                Text(
                    text      = artistName,
                    color     = Color.White.copy(alpha = 0.70f),
                    fontSize  = 13.sp,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(4.dp))
                SleeveControls(
                    isPlaying   = isPlaying,
                    onPlayPause = onPlayPause,
                    onNext      = onNext,
                    onPrevious  = onPrevious
                )
                Text(
                    text          = "↑   swipe up to dismiss",
                    color         = Color.White.copy(alpha = 0.35f),
                    fontSize      = 11.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight    = FontWeight.Light
                )
            }
        }

        // 3. Clock — drawn last so it sits on top of everything
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
        ) {
            SleeveClockWidget()
        }
    }
}

// ── Sleeve clock — time only ───────────────────────────────────────────────────

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

// ── Sleeve controls ────────────────────────────────────────────────────────────

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
                tint               = LockBg,
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

// ── Vinyl (Floating Vinyl theme) ───────────────────────────────────────────────

private const val LABEL_FRACTION = 0.38f

@Composable
private fun LockVinyl(
    coverUrl: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                rotation.animateTo(
                    targetValue   = rotation.value + 360f,
                    animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
                )
                rotation.snapTo(rotation.value % 360f)
            }
        }
        // When paused the LaunchedEffect is restarted with isPlaying=false and
        // simply does nothing, so the vinyl stops at its current angle.
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier         = modifier
    ) {
        val vinylSize   = maxWidth
        val labelSizeDp = vinylSize * LABEL_FRACTION

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(vinylSize)
                .rotate(rotation.value)
        ) {
            // ── Vinyl canvas ──────────────────────────────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx     = size.width / 2f
                val cy     = size.height / 2f
                val r      = size.minDimension / 2f
                val center = Offset(cx, cy)

                val labelRadius = r * LABEL_FRACTION
                val grooveStart = labelRadius + 2.dp.toPx()
                val grooveEnd   = r - 3.dp.toPx()

                // 1. Body
                drawCircle(color = VinylBody, radius = r, center = center)

                // 2. Groove rings
                val grooveCount = 22
                repeat(grooveCount) { i ->
                    val t       = i.toFloat() / (grooveCount - 1)
                    val grooveR = grooveStart + (grooveEnd - grooveStart) * t
                    drawCircle(
                        color  = Color.White.copy(alpha = 0.06f),
                        radius = grooveR,
                        center = center,
                        style  = Stroke(width = 0.5.dp.toPx())
                    )
                }

                // 3. Directional highlight
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(cx - r * 0.35f, cy - r * 0.40f),
                        radius = r * 0.70f
                    ),
                    radius = r,
                    center = center
                )

                // 4. Edge darkening
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.95f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.55f)
                        ),
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )

                // 5. Label background
                drawCircle(color = VinylBody, radius = labelRadius, center = center)
            }

            // ── Album art ─────────────────────────────────────────────────────
            if (coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Album art",
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(labelSizeDp)
                        .clip(CircleShape)
                )
            }

            // ── Label border ring ─────────────────────────────────────────────
            Canvas(modifier = Modifier.size(labelSizeDp)) {
                drawCircle(
                    color  = Color.White.copy(alpha = 0.15f),
                    radius = size.minDimension / 2f,
                    style  = Stroke(width = 1.5.dp.toPx())
                )
            }

            // ── Center spindle ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(VinylBody, CircleShape)
            )
        }
    }
}

// ── Controls (Floating Vinyl theme) ───────────────────────────────────────────

@Composable
private fun LockControls(
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
                imageVector        = if (isPlaying) Icons.Rounded.Pause
                                     else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint               = LockBg,
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

// ── Clock (Floating Vinyl theme) ───────────────────────────────────────────────

@Composable
private fun LockClock() {
    var time by remember { mutableStateOf(LocalTime.now()) }
    val date = remember { LocalDate.now() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            time = LocalTime.now()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text          = time.format(DateTimeFormatter.ofPattern("HH:mm")),
            color         = Color.White,
            fontSize      = 52.sp,
            fontWeight    = FontWeight.Light,
            letterSpacing = (-2).sp
        )
        Text(
            text       = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            color      = Color.White.copy(alpha = 0.50f),
            fontSize   = 13.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

// ── Waveform theme ─────────────────────────────────────────────────────────────

private const val WAVE_BAR_COUNT = 40

@Composable
private fun WaveformThemeScreen(
    coverUrl: String,
    trackTitle: String,
    artistName: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    var swipeDelta by remember { mutableFloatStateOf(0f) }

    // Single infiniteTransition drives a continuous sine-wave clock — no snapTo resets
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val time by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    // Smooth 0↔1 transition when play state changes
    val isPlayingFactor by animateFloatAsState(
        targetValue   = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        label         = "isPlayingFactor"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(LockBg)
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
        val artSize = maxWidth * 0.60f

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

            // 2. Waveforms flanking the album circle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Top waveform — bars hang downward (mirrored)
                WaveformBars(
                    time            = time,
                    isPlayingFactor = isPlayingFactor,
                    flipped         = true,
                    modifier        = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(4.dp))

                // Album art circle with thin border
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(artSize)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album art",
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .fillMaxSize()
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clip(CircleShape)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Bottom waveform — bars grow upward (normal)
                WaveformBars(
                    time            = time,
                    isPlayingFactor = isPlayingFactor,
                    flipped         = false,
                    modifier        = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 20.dp)
                )
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

            // 4. Controls
            WaveformControls(
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

// ── Waveform bar canvas ────────────────────────────────────────────────────────

@Composable
private fun WaveformBars(
    time: Float,
    isPlayingFactor: Float,
    flipped: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barWidthPx = 3.dp.toPx()
        val gapPx      = 4.dp.toPx()
        val stepPx     = barWidthPx + gapPx
        val totalW     = WAVE_BAR_COUNT * stepPx - gapPx
        val startX     = (size.width - totalW) / 2f
        val maxH       = size.height

        repeat(WAVE_BAR_COUNT) { i ->
            val barH   = abs(sin(time + i.toFloat() * 0.3f)) * maxH * isPlayingFactor
            val x      = startX + i * stepPx + barWidthPx / 2f
            val startY = if (flipped) 0f     else size.height - barH
            val endY   = if (flipped) barH   else size.height

            drawLine(
                color       = Color.White.copy(alpha = 0.6f),
                start       = Offset(x, startY),
                end         = Offset(x, endY),
                strokeWidth = barWidthPx,
                cap         = StrokeCap.Round
            )
        }
    }
}

// ── Waveform controls ──────────────────────────────────────────────────────────

@Composable
private fun WaveformControls(
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
                .size(48.dp)
                .background(Color.White, CircleShape)
                .clickable(onClick = onPlayPause)
        ) {
            Icon(
                imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint               = LockBg,
                modifier           = Modifier.size(24.dp)
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

