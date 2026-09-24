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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekColors
import com.v2ray.ang.R

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
 * glowing circles, concentric hairline rings around the top one, and the logo
 * as a 6% watermark. Geometry is the 390x844 artboard's, in dp.
 */
@Composable
fun GeekBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Geek.colors
    val logo = painterResource(R.drawable.ic_geek_logo)
    val handle = remember(colors, logo) {
        BackdropHandle { size -> drawGeekBackdrop(size, colors, logo) }
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
    ) {
        CompositionLocalProvider(LocalBackdrop provides handle) {
            content()
        }
    }
}

private val RingRadii = listOf(120, 200, 280, 360, 440, 520)
private val RingAlphas = listOf(0.100f, 0.086f, 0.072f, 0.058f, 0.044f, 0.030f)

internal fun DrawScope.drawGeekBackdrop(size: Size, colors: GeekColors, logo: Painter) {
    val bottomGlow = Offset(size.width * 0.1f, size.height - 67.52.dp.toPx())
    drawCircle(colors.backdropGlow, radius = 260.dp.toPx(), center = bottomGlow)

    val topGlow = Offset(size.width * 0.85f, 160.dp.toPx())
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

    // 351dp wide, 109dp past the left edge, bottom 151dp above the screen's bottom.
    val logoWidth = 351.dp.toPx()
    val logoHeight = logoWidth * logo.intrinsicSize.height / logo.intrinsicSize.width
    translate(left = -109.dp.toPx(), top = size.height - 151.dp.toPx() - logoHeight) {
        with(logo) {
            draw(Size(logoWidth, logoHeight), colorFilter = ColorFilter.tint(colors.watermark))
        }
    }
}
