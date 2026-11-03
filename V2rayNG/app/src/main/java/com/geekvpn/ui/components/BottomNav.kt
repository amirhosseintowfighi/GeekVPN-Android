package com.geekvpn.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GlassGradient
import com.v2ray.ang.R

/** The four tabs of the bottom navigation, in their right-to-left order. */
enum class GeekTab(@StringRes val label: Int, val icon: ImageVector) {
    Home(R.string.geek_tab_home, GeekIcons.Home),
    Services(R.string.geek_tab_services, GeekIcons.Shield),
    Shop(R.string.geek_tab_shop, GeekIcons.Bag),
    Account(R.string.geek_tab_account, GeekIcons.User),
}

/**
 * Floating tab bar of Dark Swiss: a 64dp glass pill; the selected tab is a
 * white pill with its icon and name in navy, the others show their icon only
 * (TalkBack still reads each tab's name). Switching tabs springs the white
 * pill from one tab to the next.
 *
 * The glass sits on a nearly opaque dock, so content scrolling under the bar
 * does not show through it.
 */
@Composable
fun GeekBottomNav(
    selected: GeekTab,
    onSelect: (GeekTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Geek.colors
    val shape = Geek.shapes.pill
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 16.dp)
            .height(64.dp)
            .shadow(14.dp, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
            .clip(shape)
            .background(colors.navDock, shape)
            .background(Brush.linearGradient(GlassGradient), shape)
            .border(1.5.dp, colors.clearGlassBorder, shape)
            .padding(horizontal = 6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
    // 0 = an icon on the glass, 1 = the white pill with the name. A soft spring,
    // so the pill grows with a small bounce; colours and alphas use it clamped.
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "nav-${tab.name}",
    )
    val shown = progress.coerceIn(0f, 1f)
    val content = lerp(colors.onBackground.copy(alpha = 0.75f), colors.onAction, shown)
    Row(
        modifier = Modifier
            .height(52.dp)
            .widthIn(min = 52.dp)
            .clip(Geek.shapes.pill)
            .background(colors.action.copy(alpha = shown), Geek.shapes.pill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            // The selected tab's name is its visible label; the others have only an icon.
            .semantics { if (!selected) contentDescription = label }
            .padding(horizontal = lerp(14.dp, 16.dp, shown)),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tab.icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(tween(GeekMotion.PAGE_MS)) + fadeIn(tween(GeekMotion.PAGE_MS)),
            exit = shrinkHorizontally(tween(GeekMotion.FADE_OUT_MS)) + fadeOut(tween(GeekMotion.FADE_OUT_MS)),
        ) {
            Text(
                text = label,
                style = Geek.type.label,
                color = content,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { scaleX = 0.9f + 0.1f * shown; scaleY = scaleX },
            )
        }
    }
}
