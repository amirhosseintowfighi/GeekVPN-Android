package com.geekvpn.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GlassGradient
import com.geekvpn.ui.theme.SheetBase

/**
 * The glass of Dark Swiss: a white-to-blue frosted gradient with a 40% white
 * edge and a light inset line along the top. White text on every kind.
 */
enum class GlassKind {
    /** Cards and the connect button: the deep shadow of the design's 26px blur. */
    Milk,

    /** Chips, pill buttons and the tab bar: the same glass, a lighter shadow. */
    Clear,

    /** Sheets: the glass on an almost opaque navy, since they cover other content. */
    Sheet,
}

/**
 * A glass surface. On Android 12+ the backdrop under it is blurred with a
 * RenderEffect (Modifier.blur); below that the translucent gradient alone is
 * used, which on the dark ground reads almost the same.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    kind: GlassKind = GlassKind.Milk,
    shape: Shape = Geek.shapes.card,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Geek.colors
    val backdrop = LocalBackdrop.current
    var originInRoot by remember { mutableStateOf(Offset.Zero) }

    val base = if (kind == GlassKind.Sheet) SheetBase else colors.clearGlass
    val border = colors.clearGlassBorder
    val highlight = colors.clearGlassHighlight
    val gradient = remember { Brush.linearGradient(GlassGradient, start = Offset.Zero, end = Offset.Infinite) }
    // Below Android 9 shadows ignore spotColor and draw near-black, and through a
    // translucent fill that shows as a dark square inside the surface. Glass goes
    // without one there; drop this branch when minSdk reaches 28.
    val elevation: Dp = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> 0.dp
        kind == GlassKind.Clear -> 8.dp
        else -> 14.dp
    }
    val blurRadius: Dp = if (kind == GlassKind.Clear) 22.dp else 26.dp

    Box(
        modifier = modifier
            .shadow(elevation, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
            .onGloballyPositioned { originInRoot = it.positionInRoot() }
            .clip(shape),
    ) {
        if (backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                Modifier
                    .matchParentSize()
                    .blur(blurRadius, BlurredEdgeTreatment.Rectangle)
                    .drawBehind {
                        val offset = originInRoot - backdrop.originInRoot
                        translate(-offset.x, -offset.y) { backdrop.draw(this, backdrop.size) }
                    },
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(base, shape)
                .background(gradient, shape)
                .border(1.5.dp, border, shape)
                // CSS "inset 0 1px 0": a 1dp light edge along the top.
                .drawWithContent {
                    drawContent()
                    val y = 1.dp.toPx()
                    drawLine(highlight, Offset(0f, y), Offset(size.width, y), strokeWidth = y)
                },
        )
        content()
    }
}
