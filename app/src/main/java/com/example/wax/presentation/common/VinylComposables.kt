package com.example.wax.presentation.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wax.domain.model.TurntableSkin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SpindleColor = Color(0xFF0D0D0D)

// ── Skin helpers ───────────────────────────────────────────────────────────────

internal fun TurntableSkin.baseColor()  = Color(0xFF0D0D0D)
internal fun TurntableSkin.labelColor() = Color(0xFF1C1C1C)

// ── Turntable section ─────────────────────────────────────────────────────────

@Composable
internal fun TurntableSection(
    coverUrl: String,
    isPlaying: Boolean,
    isSessionActive: Boolean,
    turntableSkin: TurntableSkin,
    labelRadiusFraction: Float = 0.28f,
    modifier: Modifier = Modifier
) {
    val shouldSpin = !isSessionActive || isPlaying
    val durationMs = if (isSessionActive) 3000 else 8000

    val rotation = remember { Animatable(0f) }
    LaunchedEffect(shouldSpin, durationMs) {
        if (shouldSpin) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
                )
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val vinylSize = maxWidth * 0.85f

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(vinylSize + 72.dp)
                .align(Alignment.Center)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(vinylSize)
                    .rotate(rotation.value)
            ) {
                VinylCanvas(
                    skinBaseColor        = turntableSkin.baseColor(),
                    skinLabelColor       = turntableSkin.labelColor(),
                    labelRadiusFraction  = labelRadiusFraction,
                    modifier             = Modifier.fillMaxSize()
                )
                AlbumArtLabel(
                    coverUrl = coverUrl,
                    modifier = Modifier
                        .size(vinylSize * labelRadiusFraction)
                        .clip(CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(SpindleColor, CircleShape)
                )
            }
            TonearmCanvas(modifier = Modifier.fillMaxSize())
        }
    }
}

// ── Vinyl Canvas ───────────────────────────────────────────────────────────────

@Composable
internal fun VinylCanvas(
    skinBaseColor: Color,
    skinLabelColor: Color,
    labelRadiusFraction: Float = 0.28f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r  = size.minDimension / 2f
        val center      = Offset(cx, cy)
        val labelRadius = r * labelRadiusFraction
        val grooveStart = labelRadius + 2.dp.toPx()
        val grooveEnd   = r - 3.dp.toPx()

        // ── 1. BASE BODY ──────────────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1A1A1A), Color(0xFF000000)),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )

        // ── 2. GROOVE RINGS ───────────────────────────────────────────────────
        // 55 concentric rings; stroke alternates 1.2 / 0.7 dp every 3 rings,
        // alpha oscillates between 0.06 and 0.12 via a seeded sin wave.
        val grooveCount = 55
        repeat(grooveCount) { i ->
            val t       = i.toFloat() / (grooveCount - 1)
            val grooveR = grooveStart + (grooveEnd - grooveStart) * t
            val strokePx = if (i % 3 == 0) 1.2.dp.toPx() else 0.7.dp.toPx()
            val alpha   = 0.06f + (sin(i * 0.7) * 0.5 + 0.5).toFloat() * 0.06f
            drawCircle(
                color  = Color.White.copy(alpha = alpha),
                radius = grooveR,
                center = center,
                style  = Stroke(width = strokePx)
            )
        }

        // ── 3. RADIAL MICRO-TEXTURE ───────────────────────────────────────────
        // 80 hairline spokes from the label edge to 90% of the disc radius,
        // simulating the microscopic groove texture visible on real vinyl.
        val twoPi = (2.0 * PI).toFloat()
        repeat(80) { i ->
            val angle = (i.toFloat() / 80f) * twoPi
            val cosA  = cos(angle.toDouble()).toFloat()
            val sinA  = sin(angle.toDouble()).toFloat()
            drawLine(
                color       = Color.White.copy(alpha = 0.03f),
                start       = Offset(cx + labelRadius * cosA, cy + labelRadius * sinA),
                end         = Offset(cx + r * 0.9f * cosA,   cy + r * 0.9f * sinA),
                strokeWidth = 0.3.dp.toPx()
            )
        }

        // ── 4. DIRECTIONAL HIGHLIGHT ──────────────────────────────────────────
        // Soft white radial gradient anchored at upper-left, simulating a
        // single overhead light source reflecting off the disc surface.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(cx - r * 0.35f, cy - r * 0.40f),
                radius = r * 0.70f
            ),
            radius = r,
            center = center
        )

        // ── 5. EDGE DARKENING ─────────────────────────────────────────────────
        // Transparent from center to 92% of the radius, then fades to near-
        // black at the rim — gives the disc physical depth and a pressed edge.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.92f to Color.Transparent,
                    1.00f to Color.Black.copy(alpha = 0.60f)
                ),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )

        // ── 6. CENTER LABEL ───────────────────────────────────────────────────
        // Flat label disc + a thin inner shadow ring that separates it from
        // the groove area and gives the edge a slight pressed-in appearance.
        drawCircle(color = skinLabelColor, radius = labelRadius, center = center)
        drawCircle(
            color  = Color.Black.copy(alpha = 0.40f),
            radius = labelRadius,
            center = center,
            style  = Stroke(width = 2.dp.toPx())
        )
    }
}

// ── Album art label ────────────────────────────────────────────────────────────

@Composable
internal fun AlbumArtLabel(
    coverUrl: String,
    modifier: Modifier = Modifier
) {
    if (coverUrl.isNotEmpty()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = "Album cover",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

// ── Tonearm Canvas ─────────────────────────────────────────────────────────────

@Composable
internal fun TonearmCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val pivot = Offset(w * 0.83f, h * 0.07f)
        val joint  = Offset(w * 0.63f, h * 0.27f)
        val tip    = Offset(w * 0.55f, h * 0.34f)

        val silver = Color(0xFFB8B8B8)

        drawLine(color = silver, start = pivot, end = joint,
            strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = silver, start = joint, end = tip,
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)

        drawCircle(color = Color(0xFF777777), radius = 9.dp.toPx(), center = pivot)
        drawCircle(color = Color(0xFF444444), radius = 5.dp.toPx(), center = pivot)
        drawCircle(color = Color(0xFFD0D0D0), radius = 3.dp.toPx(), center = tip)
    }
}
