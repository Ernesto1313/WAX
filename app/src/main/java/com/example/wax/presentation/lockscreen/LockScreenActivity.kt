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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wax.MainActivity
import com.example.wax.core.media.MediaSessionRepository
import com.example.wax.core.media.MediaSessionState
import com.example.wax.domain.model.TurntableSkin
import com.example.wax.presentation.common.TurntableSection
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val BackgroundColor = Color(0xFF08080F)
private val TextPrimary     = Color.White
private val TextSecondary   = Color.White.copy(alpha = 0.55f)
private val ControlOverlay  = Color.White.copy(alpha = 0.10f)

@AndroidEntryPoint
class LockScreenActivity : ComponentActivity() {

    @Inject lateinit var mediaSessionRepository: MediaSessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // All window configuration must happen before super.onCreate() so it takes effect
        // on the first frame. On Samsung One UI the combination of TYPE_APPLICATION_OVERLAY
        // + FLAG_SHOW_WHEN_LOCKED is what actually punches through the keyguard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // TYPE_APPLICATION_OVERLAY lets the window sit above the keyguard on OEMs
            // (Samsung One UI, MIUI, etc.) that ignore the standard FLAG_SHOW_WHEN_LOCKED alone.
            // Only applied when SYSTEM_ALERT_WINDOW has been granted by the user.
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

        // If the keyguard is not locked (user tapped notification while already unlocked),
        // skip straight to the main app and close this activity.
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
            LockScreenContent(
                state     = state,
                onPlayPause = { mediaSessionRepository.sendPlayPause() },
                onNext      = { mediaSessionRepository.sendSkipToNext() },
                onPrevious  = { mediaSessionRepository.sendSkipToPrevious() },
                onDismiss   = { finish() }
            )
        }

        // Dismiss when user unlocks (ACTION_USER_PRESENT fired by service via the repo signal)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaSessionRepository.dismissLockScreen.collect { finish() }
            }
        }

        // Also auto-dismiss when music stops playing.
        // Guard with wasPlaying so a brief pause arriving before the first playing
        // update does not instantly close the activity on launch.
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

// ── Full-screen lock UI ────────────────────────────────────────────────────────

@Composable
private fun LockScreenContent(
    state: MediaSessionState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit
) {
    var swipeDelta by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            // Swipe-up gesture: swipe ≥ 120 dp upward to dismiss
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(Modifier.height(20.dp))

            LockClock()

            Spacer(Modifier.height(16.dp))

            // Vinyl — fills available space between clock and track info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                TurntableSection(
                    coverUrl           = state.coverUrl,
                    vinylDominantColor = Color(state.vinylDominantColor),
                    vinylVibrantColor  = Color(state.vinylVibrantColor),
                    isPlaying          = state.isPlaying,
                    isSessionActive    = true,
                    turntableSkin      = TurntableSkin.DARK,
                    modifier           = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Track info
            Text(
                text       = state.trackTitle ?: "",
                color      = TextPrimary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text      = state.artistName ?: "",
                color     = TextSecondary,
                fontSize  = 15.sp,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            // Playback controls
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector        = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint               = TextPrimary,
                        modifier           = Modifier.size(40.dp)
                    )
                }

                // Central play/pause button — slightly larger with a tinted backdrop
                IconButton(
                    onClick  = onPlayPause,
                    modifier = Modifier
                        .size(72.dp)
                        .background(ControlOverlay, CircleShape)
                ) {
                    Icon(
                        imageVector        = if (state.isPlaying) Icons.Rounded.Pause
                                            else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint               = TextPrimary,
                        modifier           = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = onNext) {
                    Icon(
                        imageVector        = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint               = TextPrimary,
                        modifier           = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // Dismiss hint
            Text(
                text          = "↑   swipe up to dismiss",
                color         = TextSecondary.copy(alpha = 0.45f),
                fontSize      = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight    = FontWeight.Light
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── Clock widget ───────────────────────────────────────────────────────────────

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
            color         = TextPrimary,
            fontSize      = 68.sp,
            fontWeight    = FontWeight.Thin,
            letterSpacing = (-2).sp
        )
        Text(
            text      = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            color     = TextSecondary,
            fontSize  = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
