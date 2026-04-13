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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import javax.inject.Inject

private val LockBg    = Color(0xFF080810)
private val VinylBody = Color(0xFF0D0D0D)

private const val LABEL_FRACTION = 0.38f

@AndroidEntryPoint
class LockScreenActivity : ComponentActivity() {

    @Inject lateinit var mediaSessionRepository: MediaSessionRepository

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
            FloatingVinylScreen(
                state       = state,
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

// ── Floating Vinyl theme ───────────────────────────────────────────────────────

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

// ── Vinyl disc ─────────────────────────────────────────────────────────────────

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
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx     = size.width / 2f
                val cy     = size.height / 2f
                val r      = size.minDimension / 2f
                val center = Offset(cx, cy)

                val labelRadius = r * LABEL_FRACTION
                val grooveStart = labelRadius + 2.dp.toPx()
                val grooveEnd   = r - 3.dp.toPx()

                drawCircle(color = VinylBody, radius = r, center = center)

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

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(cx - r * 0.35f, cy - r * 0.40f),
                        radius = r * 0.70f
                    ),
                    radius = r,
                    center = center
                )

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

                drawCircle(color = VinylBody, radius = labelRadius, center = center)
            }

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

            Canvas(modifier = Modifier.size(labelSizeDp)) {
                drawCircle(
                    color  = Color.White.copy(alpha = 0.15f),
                    radius = size.minDimension / 2f,
                    style  = Stroke(width = 1.5.dp.toPx())
                )
            }

            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(VinylBody, CircleShape)
            )
        }
    }
}

// ── Analog playback controls ───────────────────────────────────────────────────

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
        // Previous — left-pointing triangle
        TurntableDiscButton(modifier = Modifier.size(44.dp), onClick = onPrevious) {
            val s  = size.minDimension * 0.28f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val path = Path().apply {
                moveTo(cx + s * 0.55f, cy - s * 0.65f)
                lineTo(cx - s * 0.55f, cy)
                lineTo(cx + s * 0.55f, cy + s * 0.65f)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.85f))
        }

        // Play / Pause — slightly larger disc
        TurntableDiscButton(modifier = Modifier.size(56.dp), onClick = onPlayPause) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            if (isPlaying) {
                val barW = size.minDimension * 0.10f
                val barH = size.minDimension * 0.36f
                val gap  = size.minDimension * 0.08f
                drawRect(Color.White.copy(alpha = 0.85f), Offset(cx - gap - barW, cy - barH / 2f), Size(barW, barH))
                drawRect(Color.White.copy(alpha = 0.85f), Offset(cx + gap, cy - barH / 2f), Size(barW, barH))
            } else {
                val s = size.minDimension * 0.28f
                val path = Path().apply {
                    moveTo(cx - s * 0.45f, cy - s * 0.65f)
                    lineTo(cx + s * 0.65f, cy)
                    lineTo(cx - s * 0.45f, cy + s * 0.65f)
                    close()
                }
                drawPath(path, Color.White.copy(alpha = 0.85f))
            }
        }

        // Next — right-pointing triangle
        TurntableDiscButton(modifier = Modifier.size(44.dp), onClick = onNext) {
            val s  = size.minDimension * 0.28f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val path = Path().apply {
                moveTo(cx - s * 0.55f, cy - s * 0.65f)
                lineTo(cx + s * 0.55f, cy)
                lineTo(cx - s * 0.55f, cy + s * 0.65f)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.85f))
        }
    }
}

/**
 * Physical turntable-style button drawn with Canvas.
 * Concentric circles create a tactile raised-disc look:
 * outer ring slightly lighter than background, inner face darker (pressed inward),
 * thin white border, and a shadow arc at the bottom for depth.
 */
@Composable
private fun TurntableDiscButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    drawIcon: DrawScope.() -> Unit
) {
    Canvas(modifier = modifier.clickable(onClick = onClick)) {
        val r      = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val innerR = r * 0.72f

        // Outer disc — slightly lighter than background, raised-button feel
        drawCircle(color = Color(0xFF1C1C28), radius = r, center = center)

        // Shadow arc at bottom — darker crescent suggests depth
        drawArc(
            color      = Color.Black.copy(alpha = 0.55f),
            startAngle = 40f,
            sweepAngle = 100f,
            useCenter  = false,
            topLeft    = Offset(center.x - r, center.y - r),
            size       = Size(r * 2f, r * 2f),
            style      = Stroke(width = r * 0.20f, cap = StrokeCap.Round)
        )

        // Inner disc — darker, the button face pressed slightly inward
        drawCircle(
            brush  = Brush.radialGradient(
                listOf(Color(0xFF18181E), Color(0xFF0D0D12)),
                center = center,
                radius = innerR
            ),
            radius = innerR,
            center = center
        )

        // Thin white border ring
        drawCircle(
            color  = Color.White.copy(alpha = 0.20f),
            radius = r - 0.5.dp.toPx(),
            center = center,
            style  = Stroke(width = 1.dp.toPx())
        )

        // Draw the icon (triangle or pause bars)
        drawIcon()
    }
}

// ── Clock ──────────────────────────────────────────────────────────────────────

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
