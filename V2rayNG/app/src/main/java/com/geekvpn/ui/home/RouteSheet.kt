package com.geekvpn.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.connection.RouteMode
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/**
 * Route.html as an overlay sheet on the current screen. The choice is only
 * applied on "ذخیره"; the per-app row opens v2rayNG's own per-app proxy screen.
 */
@Composable
fun BoxScope.RouteSheet(
    current: RouteMode,
    onSave: (RouteMode) -> Unit,
    onPerApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Geek.colors
    val dismissLabel = stringResource(R.string.geek_sheet_close)
    var choice by rememberSaveable { mutableStateOf(current) }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            // TalkBack reads the backdrop as a button; name it like the sheet's own close.
            .semantics { contentDescription = dismissLabel }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    )
    GlassSurface(
        kind = GlassKind.Milk,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.track),
            )
            Column {
                Text(stringResource(R.string.geek_route_title), style = Geek.type.sectionTitle, color = colors.onGlass)
                Text(stringResource(R.string.geek_route_subtitle), style = Geek.type.caption, color = colors.onGlassMuted)
            }
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RouteMode.entries.forEach { mode ->
                    ModeTile(
                        icon = mode.icon,
                        label = stringResource(mode.label),
                        selected = mode == choice,
                        onClick = { choice = mode },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Geek.shapes.tileLarge)
                    .background(colors.soft)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(choice.icon, contentDescription = null, tint = colors.logoBlue, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(choice.label), style = Geek.type.row.copy(fontSize = 14.sp), color = colors.onGlass)
                        if (choice == RouteMode.Smart) {
                            Text(
                                stringResource(R.string.geek_route_recommended),
                                style = Geek.type.micro,
                                color = colors.success,
                            )
                        }
                    }
                    Text(stringResource(choice.description), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Geek.shapes.tileLarge)
                    .clickable(role = Role.Button, onClick = onPerApp)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.soft), contentAlignment = Alignment.Center) {
                    Icon(GeekIcons.List, contentDescription = null, tint = colors.onGlass, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.geek_route_per_app), style = Geek.type.row.copy(fontSize = 14.sp), color = colors.onGlass)
                    Text(stringResource(R.string.geek_route_per_app_hint), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
                }
                Icon(GeekIcons.ChevronStart, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
            }
            GeekPrimaryButton(
                text = stringResource(R.string.geek_route_save),
                icon = GeekIcons.Check,
                onClick = { onSave(choice) },
            )
        }
    }
}

@Composable
private fun ModeTile(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = Geek.colors
    Column(
        modifier = modifier
            .height(104.dp)
            .clip(Geek.shapes.tileLarge)
            .background(if (selected) colors.action else colors.softButton)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        val tint = if (selected) colors.onAction else colors.onGlass
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = Geek.type.row.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = tint)
    }
}

private val RouteMode.icon: ImageVector
    get() = when (this) {
        RouteMode.Smart -> GeekIcons.Sparkle
        RouteMode.Global -> GeekIcons.Globe
        RouteMode.Direct -> GeekIcons.Block
    }

private val RouteMode.description: Int
    get() = when (this) {
        RouteMode.Smart -> R.string.geek_route_smart_desc
        RouteMode.Global -> R.string.geek_route_global_desc
        RouteMode.Direct -> R.string.geek_route_direct_desc
    }
