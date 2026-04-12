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

// ── Skin helpers ───────────────────────────────────────────────────────────────

internal fun TurntableSkin.bodyGradientColors() = when (this) {
    TurntableSkin.DARK         -> listOf(Color(0xFF1A1A1A), Color(0xFF000000))
    TurntableSkin.VINTAGE_WOOD -> listOf(Color(0xFF5d3010), Color(0xFF1a0b00))
    TurntableSkin.MINIMALIST   -> listOf(Color(0xFFf0f0f0), Color(0xFFd0d0d0))
}

internal fun TurntableSkin.labelColor() = when (this) {
    TurntableSkin.DARK         -> Color(0xFF1C1C1C)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFF4a2010)
    TurntableSkin.MINIMALIST   -> Color(0xFFd8d8d8)
}

internal fun TurntableSkin.grooveColor() = when (this) {
    TurntableSkin.DARK         -> Color.White
    TurntableSkin.VINTAGE_WOOD -> Color(0xFFc8a96e)
    TurntableSkin.MINIMALIST   -> Color(0xFF888888)
}

internal fun TurntableSkin.grooveAlpha() = when (this) {
    TurntableSkin.DARK         -> 0.08f
    TurntableSkin.VINTAGE_WOOD -> 0.15f
    TurntableSkin.MINIMALIST   -> 0.20f
}

internal fun TurntableSkin.highlightColor() = when (this) {
    TurntableSkin.DARK         -> Color.White.copy(alpha = 0.12f)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFFFFAA44).copy(alpha = 0.18f)
    TurntableSkin.MINIMALIST   -> Color.White.copy(alpha = 0.30f)
}

internal fun TurntableSkin.rimColor() = when (this) {
    TurntableSkin.DARK         -> Color.Black.copy(alpha = 0.60f)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFF1a0b00).copy(alpha = 0.50f)
    TurntableSkin.MINIMALIST   -> Color(0xFF888888).copy(alpha = 0.25f)
}

internal fun TurntableSkin.spindleColor() = when (this) {
    TurntableSkin.DARK         -> Color(0xFF0D0D0D)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFF1a0b00)
    TurntableSkin.MINIMALIST   -> Color(0xFFCCCCCC)
}

internal fun TurntableSkin.displayName() = when (this) {
    TurntableSkin.DARK         -> "Dark"
    TurntableSkin.VINTAGE_WOOD -> "Vintage"
    TurntableSkin.MINIMALIST   -> "Minimal"
}

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
                    skin                = turntableSkin,
                    labelRadiusFraction = labelRadiusFraction,
                    modifier            = Modifier.fillMaxSize()
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
                        .background(turntableSkin.spindleColor(), CircleShape)
                )
            }
            TonearmCanvas(modifier = Modifier.fillMaxSize())
        }
    }
}

// ── Vinyl Canvas ───────────────────────────────────────────────────────────────

@Composable
internal fun VinylCanvas(
    skin: TurntableSkin,
    labelRadiusFraction: Float = 0.28f,
    modifier: Modifier = Modifier
) {
    val bodyGradient    = skin.bodyGradientColors()
    val labelColor      = skin.labelColor()
    val grooveColor     = skin.grooveColor()
    val grooveAlpha     = skin.grooveAlpha()
    val highlightColor  = skin.highlightColor()
    val rimColor        = skin.rimColor()

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
                colors = bodyGradient,
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )

        // ── 2. GROOVE RINGS ───────────────────────────────────────────────────
        val grooveCount = 55
        repeat(grooveCount) { i ->
            val t       = i.toFloat() / (grooveCount - 1)
            val grooveR = grooveStart + (grooveEnd - grooveStart) * t
            val strokePx = if (i % 3 == 0) 1.2.dp.toPx() else 0.7.dp.toPx()
            drawCircle(
                color  = grooveColor.copy(alpha = grooveAlpha),
                radius = grooveR,
                center = center,
                style  = Stroke(width = strokePx)
            )
        }

        // ── 3. RADIAL MICRO-TEXTURE ───────────────────────────────────────────
        val twoPi = (2.0 * PI).toFloat()
        val spokeAlpha = grooveAlpha * 0.35f
        repeat(80) { i ->
            val angle = (i.toFloat() / 80f) * twoPi
            val cosA  = cos(angle.toDouble()).toFloat()
            val sinA  = sin(angle.toDouble()).toFloat()
            drawLine(
                color       = grooveColor.copy(alpha = spokeAlpha),
                start       = Offset(cx + labelRadius * cosA, cy + labelRadius * sinA),
                end         = Offset(cx + r * 0.9f * cosA,   cy + r * 0.9f * sinA),
                strokeWidth = 0.3.dp.toPx()
            )
        }

        // ── 4. DIRECTIONAL HIGHLIGHT ──────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(highlightColor, Color.Transparent),
                center = Offset(cx - r * 0.35f, cy - r * 0.40f),
                radius = r * 0.70f
            ),
            radius = r,
            center = center
        )

        // ── 5. EDGE DARKENING ─────────────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.92f to Color.Transparent,
                    1.00f to rimColor
                ),
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )

        // ── 6. CENTER LABEL ───────────────────────────────────────────────────
        drawCircle(color = labelColor, radius = labelRadius, center = center)
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
