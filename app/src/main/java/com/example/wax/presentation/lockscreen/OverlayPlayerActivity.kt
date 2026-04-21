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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

import javax.inject.Inject

/** Deep near-black used as the lock-screen background to blend with AMOLED displays. */
private val LockBg    = Color(0xFF080810)

/** Near-black used for the vinyl disc body and spindle dot on the lock screen. */
private val VinylBody = Color(0xFF0D0D0D)

/**
 * Fraction of the vinyl radius occupied by the centre label disc on the lock-screen player.
 * 0.38f is larger than the main-screen 0.28f so the album art is more legible at the
 * smaller lock-screen vinyl size.
 */
private const val LABEL_FRACTION = 0.38f

/**
 * Activity that renders a fullscreen turntable player over the lock screen when Spotify is
 * playing and the device is locked.
 *
 * **Window flags — why each is needed**:
 *
 * - `FLAG_SHOW_WHEN_LOCKED` (deprecated API 27+, replaced by [setShowWhenLocked]):
 *   Allows this activity's window to be drawn on top of the keyguard UI. Without it the
 *   activity would be hidden behind the lock screen on all API levels.
 *
 * - `FLAG_TURN_SCREEN_ON` (deprecated API 27+, replaced by [setTurnScreenOn]):
 *   Wakes the display if it is off when the activity is started. Without it the activity
 *   would launch silently with the screen remaining off.
 *
 * - `FLAG_KEEP_SCREEN_ON`: Prevents the display from timing out and going dark while the
 *   lock-screen player is visible. Needed so the vinyl keeps spinning without the screen
 *   going off after a few seconds of inactivity.
 *
 * **API 27+ preferred API**: On Android 8.1+ [setShowWhenLocked] and [setTurnScreenOn] are
 * the recommended replacements for the deprecated window flags. Both the modern API calls and
 * the deprecated flags are set because:
 * 1. The modern calls are more reliable on API 27+ (they go through `ActivityManager` rather
 *    than the window layer).
 * 2. The flags provide a fallback for devices running older API levels.
 * All flags are set **before** `super.onCreate()` so they take effect on the very first frame.
 *
 * **`TYPE_APPLICATION_OVERLAY`**: When the overlay permission is granted, the window type is
 * elevated to [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] on API 27+. This makes
 * the window appear above the keyguard on certain OEM launchers (Samsung One UI, MIUI) that
 * do not honour `FLAG_SHOW_WHEN_LOCKED` alone.
 *
 * **Unlock redirect (`isKeyguardLocked` check)**: If the activity is started while the phone
 * is already unlocked — for example, when the user taps a Spotify notification while the
 * screen is on — showing the lock-screen UI would be confusing. The [KeyguardManager] check
 * in `onCreate` detects this case and immediately redirects to [MainActivity] with
 * `FLAG_ACTIVITY_CLEAR_TOP` so the main turntable screen comes to the front and this
 * activity is finished without ever rendering a frame.
 *
 * **Auto-dismiss when music stops**: A coroutine collects [MediaSessionRepository.state]
 * inside `repeatOnLifecycle(STARTED)` and calls [finish] when `isPlaying` transitions from
 * `true` to `false`. The `wasPlaying` flag prevents an immediate dismiss on startup (before
 * the first playing event has been received).
 *
 * **Dismiss via [MediaSessionRepository.dismissLockScreen] SharedFlow**: When the user
 * unlocks the device, `MediaNotificationListenerService` broadcasts `ACTION_USER_PRESENT`
 * and emits on this SharedFlow. The coroutine collecting it calls [finish], cleanly removing
 * the lock-screen overlay.
 */
@AndroidEntryPoint
class LockScreenActivity : ComponentActivity() {

    /** Injected by Hilt; provides live playback state and media transport controls. */
    @Inject lateinit var mediaSessionRepository: MediaSessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Window flags — set BEFORE super.onCreate so they apply to the first frame ──

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Preferred API 27+ replacements for the deprecated window flags below
            setShowWhenLocked(true)   // draw above the keyguard
            setTurnScreenOn(true)     // wake display if it is currently off

            // Elevate to overlay type so OEM lock screens (One UI, MIUI) respect the flag
            if (Settings.canDrawOverlays(this)) {
                val lp = window.attributes
                lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                window.attributes = lp
            }
        }
        // Deprecated flags kept as a fallback for API < 27 and as a belt-and-suspenders
        // safety net on devices where the modern Activity API is unreliable.
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON    or  // prevent screen timeout
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED  or  // show above keyguard
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON        // wake display on launch
        )

        super.onCreate(savedInstanceState)

        // ── Unlock redirect ────────────────────────────────────────────────────
        // If the screen is already unlocked (e.g. user tapped the notification while the
        // phone was on), skip the lock-screen UI entirely and open the main app instead.
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

        // ── Dismiss on unlock — SharedFlow signal from MediaNotificationListenerService ──
        // ACTION_USER_PRESENT (fired when the keyguard is dismissed) is intercepted by the
        // service, which emits on dismissLockScreen. repeatOnLifecycle(STARTED) pauses
        // collection if the activity moves to the background and resumes when it returns,
        // avoiding missed events or redundant finish() calls.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaSessionRepository.dismissLockScreen.collect { finish() }
            }
        }

        // ── Auto-dismiss when music stops ──────────────────────────────────────
        // wasPlaying gates the dismiss so we don't immediately finish() on startup before
        // the first isPlaying=true event arrives from the media session.
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

/**
 * Fullscreen Compose UI for the lock-screen player.
 *
 * **Fade-in**: An [Animatable] fades the entire screen from 0 to 1 alpha over 400 ms via
 * [LaunchedEffect], softening the abrupt appearance of the overlay after the display wakes.
 *
 * **Swipe-up to dismiss gesture**: [detectVerticalDragGestures] accumulates the raw drag
 * delta in `swipeDelta`. Negative values indicate an upward swipe (screen coordinates: y
 * increases downward, so dragging up produces a negative delta). When `onDragEnd` fires
 * with `swipeDelta < −120.dp.toPx()`, [onDismiss] is called. The 120 dp threshold prevents
 * accidental dismissal from small incidental touches while still being reachable with a
 * natural flick gesture. `swipeDelta` is reset to 0 on both `onDragEnd` and `onDragCancel`
 * so a failed swipe doesn't carry state into the next gesture.
 *
 * **Layout structure** (top → bottom, evenly spaced via [Arrangement.SpaceEvenly]):
 * 1. [LockClock] — large time + date display.
 * 2. [LockVinyl] — spinning vinyl disc with album art label (88% of screen width, square).
 * 3. Track info block — track title + artist name, both truncated to one line.
 * 4. [LockControls] — skeuomorphic prev/play-pause/next buttons.
 * 5. "↑ swipe up to dismiss" hint label at 25% white opacity.
 *
 * @param state      Live [MediaSessionState] from [MediaSessionRepository]; drives all display.
 * @param onPlayPause Forwarded to [MediaSessionRepository.sendPlayPause].
 * @param onNext      Forwarded to [MediaSessionRepository.sendSkipToNext].
 * @param onPrevious  Forwarded to [MediaSessionRepository.sendSkipToPrevious].
 * @param onDismiss   Calls [LockScreenActivity.finish] to remove the overlay.
 */
@Composable
private fun FloatingVinylScreen(
    state: MediaSessionState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    // Fade the entire screen in over 400 ms to soften the wake-up transition
    val screenAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) { screenAlpha.animateTo(1f, tween(400)) }

    // Accumulated vertical drag delta; negative = upward swipe
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
                        // Negative delta = upward; dismiss when swipe exceeds 120 dp threshold
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

            // Track info — title bold, artist at 60% white opacity
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

            // Gesture hint — low opacity so it doesn't compete with the track info
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

/**
 * Spinning vinyl disc composable for the lock-screen player.
 *
 * Uses the same [Animatable]-based rotation strategy as the main-screen `TurntableSection`:
 * the rotation value accumulates across full rotations and is snapped back to `value % 360f`
 * to prevent float overflow, but never reset to `0f` — so the disc stops at its current
 * angle when [isPlaying] becomes false rather than snapping back.
 *
 * The Canvas inside the rotating [Box] draws a simplified lock-screen vinyl with four layers:
 * 1. **Solid [VinylBody] disc** — the base plate.
 * 2. **22 groove rings** — concentric circles at 6% white opacity, 0.5 dp stroke, evenly
 *    distributed between the label edge and the outer rim.
 * 3. **Directional highlight** — radial gradient from 8% white in the upper-left to
 *    transparent, simulating a light source.
 * 4. **Edge vignette** — transparent from 0%–95% of radius, then fades to 55% black at the
 *    rim to add physical depth.
 * 5. **Label disc** — solid [VinylBody] circle at [LABEL_FRACTION] radius; album art is
 *    drawn on top via [AsyncImage], clipped to [CircleShape].
 * 6. **Label ring** — a 1.5 dp white stroke at 15% opacity along the label circumference,
 *    simulating the pressed-label edge shadow.
 * 7. **Spindle dot** — 4 dp [VinylBody] circle at the exact centre.
 *
 * @param coverUrl  Remote or cached URL of the album artwork; nothing is drawn if empty.
 * @param isPlaying Controls whether the rotation animation is running.
 * @param modifier  Applied to the [BoxWithConstraints] root.
 */
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
                // Keep float bounded; stops at current angle on pause (no snap-to-zero)
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

                // Layer 1: solid body
                drawCircle(color = VinylBody, radius = r, center = center)

                // Layer 2: groove rings — 22 evenly-spaced concentric circles
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

                // Layer 3: directional highlight — upper-left radial gradient simulating light
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(cx - r * 0.35f, cy - r * 0.40f),
                        radius = r * 0.70f
                    ),
                    radius = r,
                    center = center
                )

                // Layer 4: edge vignette — transparent until 95% radius, then dark rim
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

                // Layer 5: label disc base (album art drawn on top)
                drawCircle(color = VinylBody, radius = labelRadius, center = center)
            }

            // Album art — clipped to circle, same diameter as the label disc
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

            // Layer 6: label ring — thin white stroke simulating the pressed-label edge
            Canvas(modifier = Modifier.size(labelSizeDp)) {
                drawCircle(
                    color  = Color.White.copy(alpha = 0.15f),
                    radius = size.minDimension / 2f,
                    style  = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Layer 7: spindle dot at the exact centre of the disc
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(VinylBody, CircleShape)
            )
        }
    }
}

// ── Skeuomorphic 3D button ─────────────────────────────────────────────────────

/**
 * A physically-styled button drawn entirely on [Canvas] to simulate a raised 3D surface
 * with directional lighting.
 *
 * **Drawing layers** (back to front):
 * 1. **Dark shadow** (bottom-right offset +3 dp): a dark rounded rectangle shifted down and
 *    to the right represents the shadow cast by a light source above-left. Its alpha is
 *    0.6f at rest and drops to 0.2f when pressed, reducing the depth illusion to simulate
 *    the button sinking into the surface.
 * 2. **Light shadow** (top-left offset −3 dp): a white rounded rectangle shifted up and to
 *    the left represents the highlight/bevel illuminated by the same light source. Its alpha
 *    is 0.25f at rest and drops to 0.15f when pressed, shrinking the highlight to reinforce
 *    the pressed impression.
 * 3. **Button face**: a solid rounded rectangle at `0xFF111118` (resting) or `0xFF0D0D14`
 *    (pressed). The pressed color is slightly darker to complete the sunken effect.
 *
 * **Pressed state inversion**: Both shadow alphas and the face color change simultaneously
 * when [isPressed] is true (observed via [MutableInteractionSource.collectIsPressedAsState]).
 * Together they make the button appear to recede into the surface when held.
 *
 * **Scale animation**: [animateFloatAsState] smoothly scales the entire button to 0.96f
 * while pressed, providing a tactile "click" feel without mechanical feedback.
 *
 * The icon [content] is centered inside the button using `Box(Modifier.align(Center))`.
 *
 * @param onClick Content lambda executed when the button is released.
 * @param size    Diameter of the square button; defaults to 56 dp (play/pause uses 68 dp).
 * @param content The icon composable to render at the button centre.
 */
@Composable
private fun SkeuomorphicButton(
    onClick: () -> Unit,
    size: Dp = 56.dp,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // Smoothly scale down to 0.96 on press to convey physical depression
    val scale by animateFloatAsState(if (isPressed) 0.96f else 1f, label = "skeu_scale")

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clickable(interactionSource, null) { onClick() }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = 12.dp.toPx()
            // Dark shadow: bottom-right offset; alpha halved when pressed to simulate sinking
            drawRoundRect(
                color        = Color.Black.copy(if (isPressed) 0.2f else 0.6f),
                topLeft      = Offset(3.dp.toPx(), 3.dp.toPx()),
                size         = this.size,
                cornerRadius = CornerRadius(r),
            )
            // Light shadow: top-left offset; alpha reduced when pressed to shrink highlight
            drawRoundRect(
                color        = Color.White.copy(if (isPressed) 0.15f else 0.25f),
                topLeft      = Offset((-3).dp.toPx(), (-3).dp.toPx()),
                size         = this.size,
                cornerRadius = CornerRadius(r),
            )
            // Button face: slightly darker when pressed to complete the sunken illusion
            drawRoundRect(
                color        = Color(if (isPressed) 0xFF0d0d14 else 0xFF111118),
                size         = this.size,
                cornerRadius = CornerRadius(r),
            )
        }
        Box(Modifier.align(Alignment.Center)) { content() }
    }
}

// ── Playback controls ──────────────────────────────────────────────────────────

/**
 * Horizontal row of three [SkeuomorphicButton] controls: previous, play/pause, and next.
 *
 * The play/pause button is rendered at 68 dp (vs 56 dp for skip buttons) to establish a
 * visual hierarchy that matches the standard media-player convention of a larger centre
 * action. Each button is paired with a small all-caps label below it at 40% white opacity.
 *
 * All callbacks are forwarded directly to [LockScreenActivity]'s injected
 * [MediaSessionRepository] transport commands without additional logic.
 *
 * @param isPlaying   Controls which icon is shown on the centre button (Pause vs PlayArrow).
 * @param onPlayPause Forwarded to [MediaSessionRepository.sendPlayPause].
 * @param onNext      Forwarded to [MediaSessionRepository.sendSkipToNext].
 * @param onPrevious  Forwarded to [MediaSessionRepository.sendSkipToPrevious].
 */
@Composable
private fun LockControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Previous
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SkeuomorphicButton(onClick = onPrevious, size = 56.dp) {
                Icon(
                    imageVector        = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint               = Color.White.copy(alpha = 0.85f),
                    modifier           = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text          = "PREV",
                color         = Color.White.copy(alpha = 0.4f),
                fontSize      = 9.sp,
                letterSpacing = 1.sp
            )
        }

        // Play / Pause — larger button (68 dp) for visual hierarchy
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SkeuomorphicButton(onClick = onPlayPause, size = 68.dp) {
                Icon(
                    imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint               = Color.White.copy(alpha = 0.85f),
                    modifier           = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text          = if (isPlaying) "PAUSE" else "PLAY",
                color         = Color.White.copy(alpha = 0.4f),
                fontSize      = 9.sp,
                letterSpacing = 1.sp
            )
        }

        // Next
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SkeuomorphicButton(onClick = onNext, size = 56.dp) {
                Icon(
                    imageVector        = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint               = Color.White.copy(alpha = 0.85f),
                    modifier           = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text          = "NEXT",
                color         = Color.White.copy(alpha = 0.4f),
                fontSize      = 9.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

// ── Clock ──────────────────────────────────────────────────────────────────────

/**
 * A live clock composable showing the current time (HH:mm) and today's date.
 *
 * [LocalTime.now] is polled every 1 second inside a `while(true)` loop in [LaunchedEffect].
 * Using a polling loop rather than a [kotlinx.coroutines.flow.Flow] or
 * `BroadcastReceiver` keeps the implementation simple — the lock screen is a
 * short-lived Activity and the 1-second delay is precise enough for a clock display.
 *
 * [LocalDate.now] is captured once at composition time via `remember` because the date
 * cannot change while the screen is visible (if it did, the activity would have been
 * dismissed at midnight by normal Android lifecycle events).
 *
 * The time is displayed at 52 sp with [FontWeight.Light] and −2 sp letter spacing to
 * produce the clean, wide-kerned style common on lock-screen UIs.
 */
@Composable
private fun LockClock() {
    var time by remember { mutableStateOf(LocalTime.now()) }
    // Date won't change while the lock screen is visible; captured once
    val date = remember { LocalDate.now() }

    // Poll every second to update the displayed time
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
