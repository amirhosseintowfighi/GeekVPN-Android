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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geekvpn.ui.theme.Geek

/** The two glass materials of the design. */
enum class GlassKind {
    /** "شیشه‌ی شیری": 88% white, dark text. Cards, sheets, the selected nav tile. */
    Milk,

    /** "شیشه‌ی شفاف": 13% white with a 34% border, white text. Chips and tiles on the backdrop. */
    Clear,
}

/**
 * A glass card. On Android 12+ the backdrop under it is blurred with a
 * RenderEffect (Modifier.blur); below that the translucent fill alone is used,
 * which is how the design degrades anyway since milk glass is 88% opaque.
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

    val fill = if (kind == GlassKind.Milk) colors.milkGlass else colors.clearGlass
    val border = if (kind == GlassKind.Milk) colors.milkGlassBorder else colors.clearGlassBorder
    val highlight = if (kind == GlassKind.Milk) colors.milkGlassHighlight else colors.clearGlassHighlight
    val elevation: Dp = if (kind == GlassKind.Milk) 12.dp else 8.dp
    val blurRadius: Dp = if (kind == GlassKind.Milk) 24.dp else 18.dp

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
                .background(fill, shape)
                .border(1.dp, border, shape)
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
