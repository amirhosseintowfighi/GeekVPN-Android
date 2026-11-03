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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.geekvpn.ui.common.Eyebrow
import com.geekvpn.ui.common.Hairline
import com.geekvpn.ui.common.IndexRow
import com.geekvpn.ui.common.PageTitle
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSegmented
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
        kind = GlassKind.Sheet,
        shape = Geek.shapes.sheet,
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.checkboxBorder),
            )
            PageTitle(
                stringResource(R.string.geek_route_title),
                subtitle = stringResource(R.string.geek_route_subtitle),
                eyebrow = stringResource(R.string.geek_eyebrow_route),
            )
            GeekSegmented(
                options = RouteMode.entries,
                selected = choice,
                label = { stringResource(it.label) },
                onSelect = { choice = it },
                height = 50.dp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (choice == RouteMode.Smart) Eyebrow(stringResource(R.string.geek_eyebrow_recommended))
                Text(stringResource(choice.label), style = Geek.type.sectionTitle.copy(fontSize = 22.sp), color = colors.onGlass)
                Text(stringResource(choice.description), style = Geek.type.body, color = colors.onGlass.copy(alpha = 0.85f))
            }
            // Where each part of the traffic goes in the chosen mode.
            Column {
                Hairline()
                val direct = stringResource(R.string.geek_route_via_direct)
                val vpn = stringResource(R.string.geek_route_via_vpn)
                when (choice) {
                    RouteMode.Smart -> {
                        IndexRow(stringResource(R.string.geek_code_iran), stringResource(R.string.geek_route_part_iran), direct)
                        Hairline()
                        IndexRow(stringResource(R.string.geek_code_world), stringResource(R.string.geek_route_part_rest), vpn)
                    }
                    RouteMode.Global -> IndexRow(stringResource(R.string.geek_code_world), stringResource(R.string.geek_route_part_all), vpn)
                    RouteMode.Direct -> IndexRow(stringResource(R.string.geek_code_world), stringResource(R.string.geek_route_part_all), direct)
                }
                Hairline()
            }
            Column {
                Hairline()
                IndexRow(
                    code = stringResource(R.string.geek_code_apps),
                    title = stringResource(R.string.geek_route_per_app),
                    hint = stringResource(R.string.geek_route_per_app_hint),
                    value = null,
                    onClick = onPerApp,
                )
                Hairline()
            }
            GeekPrimaryButton(
                text = stringResource(R.string.geek_route_save),
                icon = GeekIcons.ArrowAction,
                onClick = { onSave(choice) },
            )
        }
    }
}

private val RouteMode.description: Int
    get() = when (this) {
        RouteMode.Smart -> R.string.geek_route_smart_desc
        RouteMode.Global -> R.string.geek_route_global_desc
        RouteMode.Direct -> R.string.geek_route_direct_desc
    }
