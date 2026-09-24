package com.geekvpn.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
 * layout) but invisible, as in the design.
 */
@Composable
fun GeekBottomNav(
    selected: GeekTab,
    onSelect: (GeekTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
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
    Column(
        modifier = Modifier
            // selectable merges the label below into one Tab node, even when the label is invisible.
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) {
            GlassSurface(
                kind = GlassKind.Milk,
                shape = Geek.shapes.tileLarge,
                modifier = Modifier.size(60.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = colors.onGlass,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                )
            }
        } else {
            GlassSurface(
                kind = GlassKind.Clear,
                shape = Geek.shapes.tile,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(52.dp),
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = colors.onBackground,
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.Center),
                )
            }
        }
        Text(
            text = label,
            style = Geek.type.micro,
            color = if (selected) colors.onBackground else Color.Transparent,
        )
    }
}
