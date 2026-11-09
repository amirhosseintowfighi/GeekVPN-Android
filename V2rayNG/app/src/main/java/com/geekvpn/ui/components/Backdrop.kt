package com.geekvpn.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import com.geekvpn.ui.common.contentWidth
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekColors

/**
 * What a glass surface needs to redraw the backdrop underneath itself: the
 * drawing, and where the backdrop sits in the window.
 *
 * Compose has no backdrop-filter. Because every glass surface in the design
 * sits directly on this one deterministic background, a glass surface can
 * instead draw the backdrop again, offset to its own position, and blur that.
 */
@Stable
class BackdropHandle internal constructor(
    internal val draw: DrawScope.(Size) -> Unit,
) {
    internal var originInRoot by mutableStateOf(Offset.Zero)
    internal var size by mutableStateOf(Size.Zero)
}

internal val LocalBackdrop = staticCompositionLocalOf<BackdropHandle?> { null }

/**
 * The screen background of Dark Swiss: the near-black ground, one field of
 * blue light per screen ([layout], the design's glow of each artboard) and an
 * edge vignette. Changing [layout] crossfades between the two lights.
 */
@Composable
fun GeekBackdrop(
    modifier: Modifier = Modifier,
    layout: BackdropLayout = BackdropLayout.Moon,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Geek.colors
    val handle = remember(colors, layout) {
        BackdropHandle { size -> drawGeekBackdrop(size, colors, layout) }
    }
    var shown by remember { mutableStateOf(layout) }
    var previous by remember { mutableStateOf<BackdropLayout?>(null) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(layout) {
        if (layout == shown) return@LaunchedEffect
        previous = shown
        shown = layout
        fade.snapTo(0f)
        fade.animateTo(1f, tween(GeekMotion.PAGE_MS))
        previous = null
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .onGloballyPositioned {
                handle.originInRoot = it.positionInRoot()
                handle.size = it.size.toSize()
            }
            .drawBehind {
                val from = previous
                if (from == null) {
                    drawGeekBackdrop(size, colors, shown)
                } else {
                    // The old light fades out under the new one.
                    drawGeekBackdrop(size, colors, from, 1f - fade.value)
                    drawGeekBackdrop(size, colors, shown, fade.value, ground = false)
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalBackdrop provides handle) {
            // The backdrop fills a tablet or a TV; the screen itself stays phone-wide, centred.
            Box(Modifier.contentWidth().fillMaxSize(), content = content)
        }
    }
}

/** The light of each artboard of the design, named after its shape. */
enum class BackdropLayout {
    /** A lit planet in the top corner (Home-Off, Shop, Wallet). */
    Moon,

    /** A soft ribbon of light down the screen (Home-On, Route). */
    Ribbon,

    /** A bright horizon low on the screen (Servers). */
    Horizon,

    /** A cloud of light on the side (Services). */
    Blob,

    /** Vertical bars of light fading across (Account, Deposit). */
    Bars,

    /** A column of light behind the hero (Main, the login screen). */
    Column,
}

/**
 * Draws [layout] in the 390x844 artboard's coordinates, stretched to [size].
 * The design's Gaussian blurs are drawn as radial falloffs and stacked strokes,
 * which look the same at this softness and need no RenderEffect (API 31).
 */
internal fun DrawScope.drawGeekBackdrop(
    size: Size,
    colors: GeekColors,
    layout: BackdropLayout = BackdropLayout.Moon,
    alpha: Float = 1f,
    ground: Boolean = true,
) {
    if (ground) drawRect(colors.background, size = size)
    val sx = size.width / ARTBOARD_W
    val sy = size.height / ARTBOARD_H
    val art = Art(this, sx, sy, alpha)
    when (layout) {
        BackdropLayout.Moon -> {
            art.glow(39f, 211f, 273f, 380f, Color(0xFF0B2FD0), 0.55f, 39f)
            art.glow(-47f, -42f, 316f, 316f, Color(0xFF1E5BFF), 0.55f, 18f)
            // The planet itself: a hard disc, deep blue in the middle, bright at the rim.
            val center = art.at(-47f, -42f)
            val radius = 304f * sx
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFF0A1A8C), 0.82f to Color(0xFF1238E8), 1f to Color(0xFF3D7BFF),
                    center = center, radius = radius,
                ),
                radius = radius, center = center, alpha = alpha,
            )
            drawCircle(Color(0xFFBFD8FF), radius = radius, center = center, alpha = 0.85f * alpha, style = Stroke(2.4f * sx))
        }
        BackdropLayout.Ribbon -> {
            val path = art.path { moveTo(0f, -42f); cubicTo(215f, 211f, -39f, 523f, 176f, 886f) }
            art.softStroke(path, Color(0xFF1F6FE0), 0.75f, 125f, 39f)
            art.softStroke(path, Color(0xFF4FA2FF), 0.45f, 31f, 18f)
        }
        BackdropLayout.Horizon -> {
            art.glow(20f, 802f, 215f, 253f, Color(0xFF1E5FD6), 0.9f, 39f)
            art.glow(0f, 844f, 117f, 135f, Color(0xFF9CD8FF), 0.85f, 18f)
            art.glow(215f, 127f, 234f, 211f, Color(0xFF0A1F7A), 0.55f, 39f)
            val below = art.path {
                moveTo(-20f, 861f); cubicTo(98f, 675f, 215f, 464f, 429f, 304f)
                lineTo(429f, 1013f); lineTo(-20f, 1013f); close()
            }
            drawPath(below, Color(0xFF061650), alpha = 0.65f * alpha)
            val edge = art.path { moveTo(-20f, 861f); cubicTo(98f, 675f, 215f, 464f, 429f, 304f) }
            art.softStroke(edge, Color(0xFF3E8BFF), 0.55f, 23f, 5f)
            drawPath(edge, Color(0xFFD6F1FF), alpha = 0.9f * alpha, style = Stroke(2.4f * sx))
        }
        BackdropLayout.Blob -> {
            art.glow(30f, 520f, 150f, 200f, Color(0xFF3FB8FF), 0.65f, 39f)
            art.glow(45f, 510f, 125f, 150f, Color(0xFF1424D8), 0.95f, 18f)
            art.glow(20f, 464f, 47f, 68f, Color(0xFF06106A), 0.9f, 18f)
        }
        BackdropLayout.Bars -> {
            val bar = Brush.verticalGradient(
                0f to Color(0x000B2A7A), 0.45f to Color(0x8C2F7FD8), 1f to Color(0xFFCDEBFF),
                startY = 0f, endY = size.height,
            )
            Bars.forEach { (x, w, opacity) ->
                drawRect(bar, topLeft = Offset(x * sx, 0f), size = Size(w * sx, size.height), alpha = opacity * alpha)
            }
            art.glow(78f, 844f, 176f, 186f, Color(0xFFBFE4FF), 0.45f, 18f)
        }
        BackdropLayout.Column -> {
            // A column of faint discs rising into one bright glow at the top.
            for (i in 0 until 17) {
                val opacity = 0.06f + 0.5f * (i / 16f) * (i / 16f)
                drawCircle(Color(0xFF1A2FFF), radius = 105f * sx, center = art.at(195f, 421f - i * 23.125f), alpha = opacity * alpha)
            }
            art.glow(195f, 93f, 136f, 152f, Color(0xFF1A2FFF), 0.35f, 39f)
        }
    }
    // The vignette: edges sink to black.
    val c = Offset(size.width / 2f, size.height / 2f)
    scale(1f, size.height / size.width, pivot = c) {
        drawCircle(
            Brush.radialGradient(0.55f to Color.Transparent, 1f to Color(0x8C000000), center = c, radius = size.width * 0.75f),
            radius = size.width * 2f, center = c, alpha = alpha,
        )
    }
}

private const val ARTBOARD_W = 390f
private const val ARTBOARD_H = 844f

/** Deposit.dc.html's bars: x, width and opacity. */
private val Bars = listOf(
    Triple(0f, 16f, 0.95f), Triple(15f, 30f, 0.89f), Triple(43f, 29f, 0.78f), Triple(69f, 19f, 0.68f),
    Triple(86f, 23f, 0.61f), Triple(108f, 22f, 0.52f), Triple(128f, 26f, 0.44f), Triple(152f, 29f, 0.34f),
    Triple(179f, 15f, 0.24f), Triple(193f, 14f, 0.18f), Triple(206f, 30f, 0.13f), Triple(234f, 22f, 0.02f),
)

/** Drawing in artboard units: x by [sx], y by [sy]. */
private class Art(val scope: DrawScope, val sx: Float, val sy: Float, val alpha: Float) {
    fun at(x: Float, y: Float) = Offset(x * sx, y * sy)

    fun path(block: Path.() -> Unit): Path {
        val path = Path().apply(block)
        path.transform(Matrix().apply { scale(sx, sy) })
        return path
    }

    /** An ellipse blurred by [sigma]: solid to r - sigma, gone by r + 2 sigma. */
    fun glow(x: Float, y: Float, rx: Float, ry: Float, color: Color, opacity: Float, sigma: Float) = with(scope) {
        val center = at(x, y)
        val outer = (rx + 2f * sigma) * sx
        val solid = ((rx - sigma) / (rx + 2f * sigma)).coerceIn(0f, 0.95f)
        scale(1f, (ry * sy) / (rx * sx), pivot = center) {
            drawCircle(
                Brush.radialGradient(0f to color, solid to color, 1f to color.copy(alpha = 0f), center = center, radius = outer),
                radius = outer, center = center, alpha = opacity * alpha,
            )
        }
    }

    /** A stroke blurred by [sigma]: thinner, denser copies stacked inside wider, fainter ones. */
    fun softStroke(path: Path, color: Color, opacity: Float, width: Float, sigma: Float) = with(scope) {
        val layers = 8
        for (i in 0 until layers) {
            val w = (width + 4f * sigma * (1f - i / (layers - 1f))) * sx
            drawPath(path, color, alpha = opacity * alpha / layers * 1.6f, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}
