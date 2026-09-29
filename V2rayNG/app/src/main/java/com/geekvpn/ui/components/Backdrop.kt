package com.geekvpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
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
 * The shared screen background of Home-Off.html and friends: brand blue, two
 * glowing circles and concentric hairline rings around the top one. The logo
 * watermark of the design is left out on purpose. Geometry is the 390x844 artboard's, in dp.
 */
@Composable
fun GeekBackdrop(
    modifier: Modifier = Modifier,
    layout: BackdropLayout = BackdropLayout.Corner,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Geek.colors
    val handle = remember(colors, layout) {
        BackdropHandle { size -> drawGeekBackdrop(size, colors, layout) }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .onGloballyPositioned {
                handle.originInRoot = it.positionInRoot()
                handle.size = it.size.toSize()
            }
            .drawBehind { handle.draw(this, size) },
        contentAlignment = Alignment.TopCenter,
    ) {
        CompositionLocalProvider(LocalBackdrop provides handle) {
            // The backdrop fills a tablet or a TV; the screen itself stays phone-wide, centred.
            Box(Modifier.contentWidth().fillMaxSize(), content = content)
        }
    }
}

private val RingRadii = listOf(120, 200, 280, 360, 440, 520)
private val RingAlphas = listOf(0.100f, 0.086f, 0.072f, 0.058f, 0.044f, 0.030f)

/** Where the upper glow and its rings sit. */
enum class BackdropLayout {
    /** Top right corner, behind the header (Home-Off.html and the other tab screens). */
    Corner,

    /** Centred behind a hero logo (Main.html, the login screen). */
    Hero,
}

internal fun DrawScope.drawGeekBackdrop(
    size: Size,
    colors: GeekColors,
    layout: BackdropLayout = BackdropLayout.Corner,
) {
    val bottomGlow = Offset(size.width * 0.1f, size.height - 67.52.dp.toPx())
    drawCircle(colors.backdropGlow, radius = 260.dp.toPx(), center = bottomGlow)

    val topGlow = when (layout) {
        BackdropLayout.Corner -> Offset(size.width * 0.85f, 160.dp.toPx())
        BackdropLayout.Hero -> Offset(size.width * 0.5f, 300.dp.toPx())
    }
    drawCircle(colors.backdropGlowStrong, radius = 190.dp.toPx(), center = topGlow)
    val hairline = Stroke(width = 1.dp.toPx())
    RingRadii.forEachIndexed { index, radius ->
        drawCircle(
            color = colors.backdropLine.copy(alpha = RingAlphas[index]),
            radius = radius.dp.toPx(),
            center = topGlow,
            style = hairline,
        )
    }
}
