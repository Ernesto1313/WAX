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

/**
 * Returns the two-stop radial gradient colors that form the base body of the vinyl disc.
 *
 * Color philosophy per skin:
 * - **DARK** — near-black classic vinyl (`#1A1A1A` → `#000000`): mimics a standard black
 *   lacquer record under studio lighting, the most universally recognizable vinyl look.
 * - **VINTAGE_WOOD** — warm amber-brown (`#5D3010` → `#1A0B00`): evokes the deep mahogany
 *   tones of 1960s–70s turntable plinths and coloured pressings from that era.
 * - **MINIMALIST** — cool light grey (`#F0F0F0` → `#D0D0D0`): a clean, almost white-label
 *   aesthetic; references the stripped-back design language of modern hi-fi equipment.
 *
 * The two-stop list is consumed by [Brush.radialGradient] inside [VinylCanvas] to produce
 * a subtle centre-to-edge darkening that gives the disc a sense of roundness and depth.
 */
internal fun TurntableSkin.bodyGradientColors() = when (this) {
    TurntableSkin.DARK         -> listOf(Color(0xFF1A1A1A), Color(0xFF000000))
    TurntableSkin.VINTAGE_WOOD -> listOf(Color(0xFF5d3010), Color(0xFF1a0b00))
    TurntableSkin.MINIMALIST   -> listOf(Color(0xFFf0f0f0), Color(0xFFd0d0d0))
}

/**
 * Returns the solid fill color of the centre label disc drawn over the vinyl body.
 *
 * The label sits in the innermost [labelRadiusFraction] of the record and is overlaid
 * by [AlbumArtLabel] when a cover URL is available. Its color therefore only shows at
 * the edges of the label disc (the ring between the artwork and the spindle hole).
 *
 * Each skin's label color is slightly lighter than its body to create a subtle but
 * readable distinction between the pressing and the paper label area.
 */
internal fun TurntableSkin.labelColor() = when (this) {
    TurntableSkin.DARK         -> Color(0xFF1C1C1C)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFF4a2010)
    TurntableSkin.MINIMALIST   -> Color(0xFFd8d8d8)
}

/**
 * Returns the tint color applied to groove rings and radial micro-texture spokes.
 *
 * - **DARK** — pure white: on the near-black body, white grooves best simulate the
 *   reflective micro-ridges of a real lacquer pressing under white light.
 * - **VINTAGE_WOOD** — warm gold (`#C8A96E`): complements the amber body and evokes
 *   the warm-coloured light reflections seen on aged vinyl pressings.
 * - **MINIMALIST** — mid grey (`#888888`): understated; grooves blend softly with the
 *   light body rather than creating sharp contrast.
 */
internal fun TurntableSkin.grooveColor() = when (this) {
    TurntableSkin.DARK         -> Color.White
    TurntableSkin.VINTAGE_WOOD -> Color(0xFFc8a96e)
    TurntableSkin.MINIMALIST   -> Color(0xFF888888)
}

/**
 * Returns the base alpha transparency applied to [grooveColor] for groove rings and spokes.
 *
 * Lower alpha on DARK keeps the grooves barely visible (realistic — grooves on black vinyl
 * are not strongly visible from a distance). Higher alpha on MINIMALIST compensates for
 * the low contrast between the grey groove color and the light body.
 */
internal fun TurntableSkin.grooveAlpha() = when (this) {
    TurntableSkin.DARK         -> 0.08f
    TurntableSkin.VINTAGE_WOOD -> 0.15f
    TurntableSkin.MINIMALIST   -> 0.20f
}

/**
 * Returns the color used for the off-centre directional highlight arc that simulates
 * a light source striking the vinyl surface from the upper-left.
 *
 * - **DARK** — cool white at 12% opacity: subtle gloss on black lacquer.
 * - **VINTAGE_WOOD** — warm amber-orange at 18% opacity: mimics incandescent lamp
 *   reflection off a coloured pressing.
 * - **MINIMALIST** — bright white at 30% opacity: a more pronounced sheen appropriate
 *   for a light-coloured, high-gloss surface.
 */
internal fun TurntableSkin.highlightColor() = when (this) {
    TurntableSkin.DARK         -> Color.White.copy(alpha = 0.12f)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFFFFAA44).copy(alpha = 0.18f)
    TurntableSkin.MINIMALIST   -> Color.White.copy(alpha = 0.30f)
}

/**
 * Returns the color blended into the outer rim via a radial gradient to create the
 * illusion of physical edge depth (the disc thins and darkens toward its circumference).
 *
 * A semi-transparent dark color on DARK and VINTAGE_WOOD produces a natural vignette.
 * MINIMALIST uses a lighter grey at reduced opacity so the rim doesn't look too heavy
 * against the pale body.
 */
internal fun TurntableSkin.rimColor() = when (this) {
    TurntableSkin.DARK         -> Color.Black.copy(alpha = 0.60f)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFF1a0b00).copy(alpha = 0.50f)
    TurntableSkin.MINIMALIST   -> Color(0xFF888888).copy(alpha = 0.25f)
}

/**
 * Returns the fill color of the 8 dp spindle dot drawn at the exact centre of the disc.
 *
 * The spindle represents the centre hole of a real record — it should contrast slightly
 * with the label to be visible, but not dominate. MINIMALIST uses a light grey rather
 * than near-black to maintain the overall pale palette.
 */
internal fun TurntableSkin.spindleColor() = when (this) {
    TurntableSkin.DARK         -> Color(0xFF0D0D0D)
    TurntableSkin.VINTAGE_WOOD -> Color(0xFF1a0b00)
    TurntableSkin.MINIMALIST   -> Color(0xFFCCCCCC)
}

/**
 * Returns the short human-readable display name shown in the Settings skin picker.
 *
 * Intentionally brief ("Dark", "Vintage", "Minimal") to fit within small chip/radio labels
 * without truncation on any screen density.
 */
internal fun TurntableSkin.displayName() = when (this) {
    TurntableSkin.DARK         -> "Dark"
    TurntableSkin.VINTAGE_WOOD -> "Vintage"
    TurntableSkin.MINIMALIST   -> "Minimal"
}

// ── Turntable section ─────────────────────────────────────────────────────────

/**
 * Composable that renders the complete turntable assembly: spinning vinyl disc,
 * album art label, spindle dot, and the static tonearm overlay.
 *
 * **Layout — [BoxWithConstraints]**: `BoxWithConstraints` is used instead of a plain `Box`
 * because it exposes `maxWidth` as a runtime `Dp` value, allowing `vinylSize` to be
 * derived as a fraction of the actual available width rather than a hard-coded dp value.
 * This makes the disc responsive — it scales proportionally whether the composable is
 * shown in a compact portrait phone or a large tablet layout.
 *
 * **Size — `maxWidth * 0.85f`**: 85% of the available width leaves a small margin on each
 * side and keeps the tonearm (which extends beyond the vinyl) within the total bounding
 * box (`vinylSize + 72.dp`) without clipping.
 *
 * **Rotation — [Animatable]**: A single `Animatable<Float>` drives the `rotate` modifier.
 * It continuously animates from `rotation.value` to `rotation.value + 360f` using
 * [LinearEasing] so the spin speed is constant. On each full rotation, `snapTo(value % 360f)`
 * keeps the accumulated float from growing to infinity — but crucially it does NOT reset to
 * `0f`. This means when [shouldSpin] becomes `false` (playback paused), the animation simply
 * stops at its current angle with no visible snap-back, preserving the physical feel of a
 * platter coasting to a halt.
 *
 * **Spin logic**: When [isSessionActive] is `false` (no active Spotify session), the disc
 * always spins slowly (`durationMs = 8000`) as a decorative idle state. When the session is
 * active, spin speed increases (`durationMs = 3000`) and `shouldSpin` follows [isPlaying],
 * so the disc stops when Spotify is paused.
 *
 * **[labelRadiusFraction]**: Propagated both to [VinylCanvas] (to size the painted label disc)
 * and to [AlbumArtLabel] (to size the artwork image) so the two always match exactly.
 *
 * @param coverUrl            Remote or locally-cached URL of the album artwork.
 * @param isPlaying           Whether Spotify is currently playing; controls whether the disc spins.
 * @param isSessionActive     Whether an active Spotify media session exists.
 * @param turntableSkin       The selected visual theme applied to the vinyl disc colors.
 * @param labelRadiusFraction Fraction of the vinyl radius used for the centre label area (default 0.28).
 * @param modifier            Modifier applied to the [BoxWithConstraints] root.
 */
@Composable
internal fun TurntableSection(
    coverUrl: String,
    isPlaying: Boolean,
    isSessionActive: Boolean,
    turntableSkin: TurntableSkin,
    labelRadiusFraction: Float = 0.28f,
    modifier: Modifier = Modifier
) {
    // No active session → always spin (decorative idle); active session → spin only if playing
    val shouldSpin = !isSessionActive || isPlaying
    // Faster rotation during live playback to feel more energetic
    val durationMs = if (isSessionActive) 3000 else 8000

    // Single Animatable drives the rotate() modifier; survives recompositions via remember
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(shouldSpin, durationMs) {
        if (shouldSpin) {
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(durationMillis = durationMs, easing = LinearEasing)
                )
                // Prevent the float from growing unbounded across many rotations;
                // snapTo does NOT reset to 0 — the disc keeps its current angle on pause.
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }

    // BoxWithConstraints exposes maxWidth at runtime so vinylSize can be a responsive fraction
    BoxWithConstraints(modifier = modifier) {
        // 85% of available width — leaves room for the tonearm that extends beyond the disc edge
        val vinylSize = maxWidth * 0.85f

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(vinylSize + 72.dp)   // extra 72 dp accommodates the tonearm overhang
                .align(Alignment.Center)
        ) {
            // Rotating layer: disc body + album art label + spindle dot all spin together
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
                // Spindle dot — rendered on top of the label at the exact centre
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(turntableSkin.spindleColor(), CircleShape)
                )
            }
            // Tonearm is NOT inside the rotating Box — it is static and drawn over the disc
            TonearmCanvas(modifier = Modifier.fillMaxSize())
        }
    }
}

// ── Vinyl Canvas ───────────────────────────────────────────────────────────────

/**
 * Renders the vinyl disc as a layered [Canvas] drawing. All dimensions are derived from
 * `size.minDimension / 2f` so the disc scales correctly regardless of whether the canvas
 * is square or slightly rectangular.
 *
 * The disc is built from six composited layers drawn back-to-front:
 *
 * **Layer 1 — Base body**: A [Brush.radialGradient] fills the full disc circle using the
 * two stops from [TurntableSkin.bodyGradientColors]. The gradient runs from a slightly
 * lighter centre to a darker edge, giving the flat circle a sense of spherical curvature.
 *
 * **Layer 2 — Groove rings**: 55 concentric circles are distributed evenly between
 * `grooveStart` (just outside the label disc) and `grooveEnd` (just inside the outer rim).
 * Every third ring is drawn at 1.2 dp stroke width instead of 0.7 dp, replicating the
 * irregular spacing visible on real lacquer pressings where groove pitch varies with audio
 * density. [TurntableSkin.grooveAlpha] keeps the rings subtle so they read as texture
 * rather than distinct lines.
 *
 * **Layer 3 — Radial micro-texture spokes**: 80 evenly-angled line segments radiate from
 * the label edge to 90% of the disc radius. They are drawn at 0.3 dp stroke width and
 * `grooveAlpha * 0.35f` opacity — barely visible individually but adding a fine-grain
 * sheen when viewed as a whole, simulating the microscopic radial scratches on aged vinyl.
 *
 * **Layer 4 — Directional highlight**: A [Brush.radialGradient] centred at
 * `(cx − r*0.35, cy − r*0.40)` (upper-left quadrant) fades from [TurntableSkin.highlightColor]
 * to transparent. This simulates a directional light source above-left of the platter,
 * producing the characteristic bright crescent seen on glossy black records.
 *
 * **Layer 5 — Edge darkening**: A second radial gradient is completely transparent from the
 * centre to 92% of the radius, then fades to [TurntableSkin.rimColor] at the edge. This
 * creates a vignette that makes the disc appear to curve away at its circumference, giving
 * the illusion of physical thickness and depth.
 *
 * **Layer 6 — Centre label with inner shadow ring**: A solid disc at `labelRadius` is filled
 * with [TurntableSkin.labelColor]. A dark stroke ring (`Color.Black` at 40% opacity, 2 dp
 * wide) is drawn on top along the label's circumference to simulate the shadow cast by the
 * slightly raised paper label edge — a subtle skeuomorphic detail.
 *
 * @param skin                The [TurntableSkin] controlling all color and alpha values.
 * @param labelRadiusFraction Fraction of the disc radius occupied by the centre label (default 0.28).
 * @param modifier            Modifier applied to the [Canvas].
 */
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
        // Groove band starts just outside the label disc and ends just inside the outer rim
        val grooveStart = labelRadius + 2.dp.toPx()
        val grooveEnd   = r - 3.dp.toPx()

        // ── 1. BASE BODY ──────────────────────────────────────────────────────
        // Radial gradient from a slightly lighter centre to a darker edge gives the flat
        // circle the appearance of a rounded, three-dimensional object.
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
        // 55 rings distributed linearly across the groove band (t goes 0..1).
        // Every 3rd ring is drawn slightly thicker (1.2 dp vs 0.7 dp) to replicate the
        // uneven pitch of audio grooves on a real lacquer master — denser audio content
        // pushes grooves closer together while quiet passages allow wider spacing.
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
        // 80 hair-thin spokes radiate from the label edge to 90% of the disc radius.
        // Individually nearly invisible at 0.3 dp / 35% of grooveAlpha, they collectively
        // add a fine-grain reflective sheen that simulates the microscopic radial scratches
        // and surface texture visible on real vinyl under oblique lighting.
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
        // A radial gradient centred in the upper-left quadrant (cx − r*0.35, cy − r*0.40)
        // fades from highlightColor to transparent over 70% of the disc radius.
        // The off-centre origin simulates a fixed light source above and to the left of
        // the platter, producing the bright crescent characteristic of glossy vinyl under
        // studio or living-room lighting.
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
        // Transparent from 0% to 92% of the radius, then transitions to rimColor at 100%.
        // This narrow vignette makes the disc appear to curve away at its circumference,
        // giving the illusion of physical thickness and simulating the way real records
        // catch less light near their outer edge.
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
        // Solid disc at labelRadius filled with the skin's label color; [AlbumArtLabel]
        // will cover most of this with the album artwork, so the label color only shows
        // as a thin ring between the artwork and the spindle hole.
        drawCircle(color = labelColor, radius = labelRadius, center = center)
        // Dark stroke ring along the label circumference simulates the inner shadow cast
        // by the slightly raised edge of a real paper label glued to the pressing.
        drawCircle(
            color  = Color.Black.copy(alpha = 0.40f),
            radius = labelRadius,
            center = center,
            style  = Stroke(width = 2.dp.toPx())
        )
    }
}

// ── Album art label ────────────────────────────────────────────────────────────

/**
 * Renders the album artwork centred on the vinyl label disc.
 *
 * **Coil [AsyncImage]**: The image is loaded asynchronously by Coil using [ImageRequest]
 * with `crossfade(true)` so the artwork fades in smoothly once the network or cache response
 * arrives, avoiding an abrupt pop. `ContentScale.Crop` fills the circular clipping area
 * completely — the image is scaled up until its shorter dimension equals the target size,
 * then the excess is cropped symmetrically. This prevents letterboxing/pillarboxing inside
 * the circular frame regardless of the artwork's aspect ratio (most album covers are square,
 * but some older releases are not).
 *
 * **Circular clipping**: The caller applies `.clip(CircleShape)` via [modifier] before
 * passing it here. The circle's diameter equals `vinylSize * labelRadiusFraction`, keeping
 * the artwork exactly flush with the label disc drawn by [VinylCanvas].
 *
 * **Fallback behavior**: When [coverUrl] is empty (album not yet loaded, or cache miss
 * before the network response), the composable renders nothing. The [VinylCanvas] label
 * disc and its stroke ring remain visible underneath, so the user sees the bare label
 * area instead of a broken image placeholder.
 *
 * @param coverUrl The resolved artwork URL (network URL or local file path from cache).
 *                 An empty string suppresses the image entirely.
 * @param modifier Applied to the [AsyncImage]; should include `.clip(CircleShape)` and a
 *                 `.size(...)` equal to `vinylSize * labelRadiusFraction`.
 */
@Composable
internal fun AlbumArtLabel(
    coverUrl: String,
    modifier: Modifier = Modifier
) {
    // Only render when a URL is available; empty string shows the bare label disc underneath
    if (coverUrl.isNotEmpty()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(coverUrl)
                .crossfade(true)  // smooth fade-in from cache or network
                .build(),
            contentDescription = "Album cover",
            contentScale = ContentScale.Crop,  // fill circle without letterboxing
            modifier = modifier
        )
    }
}

// ── Tonearm Canvas ─────────────────────────────────────────────────────────────

/**
 * Draws a static skeuomorphic tonearm positioned in the upper-right quadrant of the canvas,
 * resting in the playing position (arm lowered onto the groove area).
 *
 * **Geometry** (all coordinates are proportional fractions of `size.width` / `size.height`):
 *
 * - **Pivot** `(w*0.83, h*0.07)` — the fixed rotation bearing at the upper-right from which
 *   the arm extends. Drawn as two concentric circles: an outer ring in mid-grey (`#777777`,
 *   9 dp radius) representing the bearing housing, and an inner dot in dark grey (`#444444`,
 *   5 dp radius) representing the bearing pin itself.
 *
 * - **Joint** `(w*0.63, h*0.27)` — the elbow-like bend midway along the arm where the
 *   straight shaft transitions to the angled headshell. In a real tonearm this is where
 *   the counterweight end meets the cartridge-carrying section.
 *
 * - **Tip / needle** `(w*0.55, h*0.34)` — the cartridge / stylus contact point sitting
 *   approximately over the outermost groove of the vinyl. Drawn as a small light-grey disc
 *   (`#D0D0D0`, 3 dp radius) representing the stylus diamond tip.
 *
 * **Drawing order**:
 * 1. **Shaft** (pivot → joint): 4 dp wide silver (`#B8B8B8`) line with rounded caps —
 *    the main structural tube of the tonearm.
 * 2. **Headshell** (joint → tip): 3 dp wide, same silver — thinner than the shaft to
 *    suggest the lighter cartridge-holder assembly at the business end of the arm.
 * 3. **Pivot housing circles**: two concentric circles over the pivot point add depth
 *    and recognisability to the bearing mechanism.
 * 4. **Needle dot**: small circle at the tip marks the stylus position.
 *
 * The entire tonearm is drawn on a canvas that fills the same bounding box as
 * [TurntableSection]'s outer container (`vinylSize + 72.dp`), so the proportional
 * coordinates naturally position the arm above and to the right of the vinyl disc.
 *
 * @param modifier Modifier applied to the [Canvas]; should be `Modifier.fillMaxSize()` so
 *                 the proportional coordinate system matches the container.
 */
@Composable
internal fun TonearmCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Proportional key points — fixed bearing, mid-arm elbow, and stylus tip
        val pivot = Offset(w * 0.83f, h * 0.07f)   // bearing at upper-right
        val joint  = Offset(w * 0.63f, h * 0.27f)  // elbow where shaft meets headshell
        val tip    = Offset(w * 0.55f, h * 0.34f)  // cartridge / stylus contact point

        val silver = Color(0xFFB8B8B8)

        // Shaft: main structural tube from bearing to elbow (thicker, 4 dp)
        drawLine(color = silver, start = pivot, end = joint,
            strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        // Headshell: lighter cartridge-holder section from elbow to stylus tip (3 dp)
        drawLine(color = silver, start = joint, end = tip,
            strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)

        // Pivot bearing housing: outer ring (bearing casing) + inner dot (bearing pin)
        drawCircle(color = Color(0xFF777777), radius = 9.dp.toPx(), center = pivot)
        drawCircle(color = Color(0xFF444444), radius = 5.dp.toPx(), center = pivot)
        // Stylus needle dot at the tip of the headshell
        drawCircle(color = Color(0xFFD0D0D0), radius = 3.dp.toPx(), center = tip)
    }
}
