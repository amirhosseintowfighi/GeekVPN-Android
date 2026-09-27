package com.geekvpn.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/** The four tabs of the bottom navigation, in their right-to-left order. */
enum class GeekTab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.geek_tab_home, GeekIcons.Home),
    Services(R.string.geek_tab_services, GeekIcons.Shield),
    Shop(R.string.geek_tab_shop, GeekIcons.Bag),
    Account(R.string.geek_tab_account, GeekIcons.User),
}

/**
 * Floating tab bar: the selected tab is a 60dp milk-glass tile with its label
 * under it; the others are 52dp clear-glass tiles whose labels are kept (for
 * layout) but invisible, as in the design. Switching tabs animates between
 * the two.
 *
 * The tiles sit on a nearly opaque dock. Without it, a card scrolling under the
 * bar showed through the clear tiles in the same colour and the tabs got lost.
 */
@Composable
fun GeekBottomNav(
    selected: GeekTab,
    onSelect: (GeekTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Geek.colors
    val shape = Geek.shapes.card
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .shadow(16.dp, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .background(colors.navDock, shape)
            .border(1.dp, colors.clearGlassBorder, shape)
            .padding(top = 8.dp, bottom = 4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        GeekTab.entries.forEach { tab ->
            NavItem(tab = tab, selected = tab == selected, onClick = { onSelect(tab) })
        }
    }
}

@Composable
private fun NavItem(tab: GeekTab, selected: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    val label = stringResource(tab.label)
    // 0 = the clear 52dp tile, 1 = the milk 60dp one. A soft spring, so the
    // tile grows with a small bounce; colours and alphas use it clamped.
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "nav-${tab.name}",
    )
    val shown = progress.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            // selectable merges the label below into one Tab node, even when the label is invisible.
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = lerp(4.dp, 0.dp, shown))
                .size(lerp(52.dp, 60.dp, progress)),
        ) {
            GlassSurface(
                kind = GlassKind.Clear,
                shape = Geek.shapes.tile,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = 1f - shown },
            ) {}
            GlassSurface(
                kind = GlassKind.Milk,
                shape = Geek.shapes.tileLarge,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = shown },
            ) {}
            Icon(
                tab.icon,
                contentDescription = null,
                tint = lerp(colors.onBackground, colors.onGlass, shown),
                modifier = Modifier
                    .size(lerp(22.dp, 24.dp, shown))
                    .align(Alignment.Center),
            )
        }
        Text(
            text = label,
            style = Geek.type.micro,
            color = colors.onBackground.copy(alpha = shown),
        )
    }
}
