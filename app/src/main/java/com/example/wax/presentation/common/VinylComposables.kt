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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wax.domain.model.TurntableSkin

private val SpindleColor = Color(0xFF0D0D0D)

// ── Skin helpers ───────────────────────────────────────────────────────────────

internal fun TurntableSkin.baseColor() = when (this) {
    TurntableSkin.DARK         -> Color(0xFF0D0D0D)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFF2C1810)
    TurntableSkin.MINIMALIST   -> Color(0xFFF0F0F0)
}

internal fun TurntableSkin.grooveColor() = when (this) {
    TurntableSkin.DARK         -> Color.White.copy(alpha = 0.07f)
    TurntableSkin.VINTAGE_WOOD -> Color.White.copy(alpha = 0.10f)
    TurntableSkin.MINIMALIST   -> Color.Black.copy(alpha = 0.08f)
}

internal fun TurntableSkin.labelColor() = when (this) {
    TurntableSkin.DARK         -> Color(0xFF1C1C1C)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFF3B2418)
    TurntableSkin.MINIMALIST   -> Color(0xFFE0E0E0)
}

// ── Turntable section ─────────────────────────────────────────────────────────

@Composable
internal fun TurntableSection(
    coverUrl: String,
    vinylDominantColor: Color,
    vinylVibrantColor: Color,
    isPlaying: Boolean,
    isSessionActive: Boolean,
    turntableSkin: TurntableSkin,
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
                    dominantColor   = vinylDominantColor,
                    vibrantColor    = vinylVibrantColor,
                    skinBaseColor   = turntableSkin.baseColor(),
                    skinGrooveColor = turntableSkin.grooveColor(),
                    skinLabelColor  = turntableSkin.labelColor(),
                    modifier        = Modifier.fillMaxSize()
                )
                AlbumArtLabel(
                    coverUrl = coverUrl,
                    modifier = Modifier
                        .size(vinylSize * 0.28f)
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
    dominantColor: Color,
    vibrantColor: Color,
    skinBaseColor: Color,
    skinGrooveColor: Color,
    skinLabelColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        val center = Offset(cx, cy)
        val labelRadius = r * 0.28f
        val grooveStart = labelRadius + 2.dp.toPx()
        val grooveEnd   = r - 3.dp.toPx()

        val vinylBase = lerp(skinBaseColor, dominantColor, 0.15f)
        drawCircle(color = vinylBase, radius = r, center = center)

        drawCircle(
            brush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.18f to vibrantColor.copy(alpha = 0.20f),
                    0.45f to Color.Transparent,
                    0.65f to vibrantColor.copy(alpha = 0.10f),
                    1.00f to Color.Transparent
                ),
                center = center
            ),
            radius = r,
            center = center
        )

        val grooveCount = 44
        repeat(grooveCount) { i ->
            val t = i.toFloat() / grooveCount
            val grooveR = grooveStart + (grooveEnd - grooveStart) * t
            val strokePx = if (i % 3 == 0) 1.8.dp.toPx() else 0.9.dp.toPx()
            drawCircle(
                color = skinGrooveColor,
                radius = grooveR,
                center = center,
                style = Stroke(width = strokePx)
            )
        }

        val midR = (grooveStart + grooveEnd) / 2f
        val iridSpacing = (grooveEnd - grooveStart) * 0.07f
        for (k in -1..1) {
            drawCircle(
                color = vibrantColor.copy(alpha = 0.05f),
                radius = midR + k * iridSpacing,
                center = center,
                style = Stroke(width = 2.2.dp.toPx())
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(cx - r * 0.30f, cy - r * 0.38f),
                radius = r * 0.62f
            ),
            radius = r,
            center = center
        )

        drawCircle(color = skinLabelColor, radius = labelRadius, center = center)
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
