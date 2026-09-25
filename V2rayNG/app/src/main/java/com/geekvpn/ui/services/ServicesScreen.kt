package com.geekvpn.ui.services

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.connection.ConnectionPhase
import com.geekvpn.connection.ServiceStatus
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.PageTitle
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatGib
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekDangerButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.home.HomeUiState
import com.geekvpn.ui.home.ManualGroup
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/** What the Services tab can ask of the activity. */
interface ServicesActions {
    fun onBuy()
    fun onAddSubscription()
    fun onImportClipboard()
    fun onUse(subscriptionId: String)
    fun onConnect()
    fun onDisconnect()
    fun onRefresh()
    fun onCopy(url: String)
    fun onUseManual(groupId: String)
}

/** Services.html: the account's services as cards, then the manual links. */
@Composable
fun ServicesScreen(state: HomeUiState, isSignedIn: Boolean, actions: ServicesActions) {
    val colors = Geek.colors
    var showAll by rememberSaveable { mutableStateOf(false) }
    val active = state.services.filter { it.active }
    val shown = if (showAll) state.services else active

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PageTitle(stringResource(R.string.geek_services_title), Modifier.weight(1f))
            AddLinkButton(actions)
            if (isSignedIn) {
                GlassIconButton(GeekIcons.Plus, stringResource(R.string.geek_services_buy_description), actions::onBuy, kind = GlassKind.Milk)
            }
        }

        if (isSignedIn) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stringResource(R.string.geek_services_filter_active, formatNumber(active.size.toLong(), appLocale())), !showAll) { showAll = false }
                FilterChip(stringResource(R.string.geek_services_filter_all, formatNumber(state.services.size.toLong(), appLocale())), showAll) { showAll = true }
            }
            if (shown.isEmpty()) {
                Text(stringResource(R.string.geek_services_empty), style = Geek.type.body, color = colors.onBackgroundMuted)
            }
            shown.forEach { service ->
                ServiceCard(
                    service = service,
                    isActive = state.activeService?.subscriptionId == service.subscriptionId,
                    phase = state.phase,
                    updating = state.updating,
                    actions = actions,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.geek_services_manual), style = Geek.type.label, color = colors.onBackground, modifier = Modifier.weight(1f))
            AddChip(actions)
        }
        if (state.manualGroups.isEmpty()) {
            ManualHint()
        } else {
            state.manualGroups.forEach { group ->
                ManualRow(group, selected = state.groupId == group.guid) { actions.onUseManual(group.guid) }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    GlassSurface(
        kind = if (selected) GlassKind.Milk else GlassKind.Clear,
        shape = Geek.shapes.pill,
        modifier = Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Text(
            text,
            style = Geek.type.caption.copy(fontWeight = FontWeight.Bold),
            color = if (selected) colors.onGlass else colors.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun ServiceCard(
    service: ServiceStatus,
    isActive: Boolean,
    phase: ConnectionPhase,
    updating: Boolean,
    actions: ServicesActions,
) {
    val colors = Geek.colors
    val locale = appLocale()
    val connected = isActive && phase == ConnectionPhase.On
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Column {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(colors.logoBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.ic_geek_logo), contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(service.title, style = Geek.type.row.copy(fontWeight = FontWeight.ExtraBold), color = colors.onGlass, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "#" + service.subscriptionId.replace("-", "").take(8),
                            style = Geek.type.numberSmall.copy(fontWeight = FontWeight.Normal, textDirection = TextDirection.Ltr),
                            color = colors.onGlassMuted,
                        )
                    }
                    StateChip(service, connected)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Meter(
                        label = stringResource(R.string.geek_services_time_left),
                        value = service.daysLeft?.let { formatNumber(it.toLong(), locale) } ?: stringResource(R.string.geek_services_unlimited),
                        unit = if (service.daysLeft != null) stringResource(R.string.geek_services_days_unit) else null,
                        // Thirty days is a full bar: plans are sold by the month.
                        fraction = service.daysLeft?.let { (it / 30f).coerceIn(0f, 1f) } ?: 1f,
                        barColor = colors.warning,
                        modifier = Modifier.weight(1f),
                    )
                    Meter(
                        label = stringResource(R.string.geek_services_data_left),
                        value = service.remainingGib?.let { formatGib(it, locale) } ?: stringResource(R.string.geek_services_unlimited),
                        unit = if (service.remainingGib != null) stringResource(R.string.geek_services_gb_unit) else null,
                        fraction = service.remainingFraction,
                        barColor = colors.action,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            TicketDivider()
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    connected -> GeekDangerButton(
                        text = stringResource(R.string.geek_services_disconnect),
                        icon = GeekIcons.Unlink,
                        onClick = actions::onDisconnect,
                        modifier = Modifier.weight(1f),
                    )
                    isActive -> GeekSecondaryButton(
                        text = stringResource(R.string.geek_services_connect),
                        icon = GeekIcons.Link,
                        onClick = actions::onConnect,
                        enabled = service.active && phase == ConnectionPhase.Off,
                        modifier = Modifier.weight(1f),
                    )
                    else -> GeekSecondaryButton(
                        text = stringResource(R.string.geek_services_use),
                        icon = GeekIcons.Check,
                        onClick = { actions.onUse(service.subscriptionId) },
                        enabled = service.active,
                        modifier = Modifier.weight(1f),
                    )
                }
                SquareButton(GeekIcons.Refresh, stringResource(R.string.geek_services_refresh_description), enabled = !updating, onClick = actions::onRefresh)
                val url = service.subscriptionUrl
                if (url != null) {
                    SquareButton(GeekIcons.Copy, stringResource(R.string.geek_services_copy_description)) { actions.onCopy(url) }
                }
            }
        }
    }
}

@Composable
private fun StateChip(service: ServiceStatus, connected: Boolean) {
    val colors = Geek.colors
    val (text, fg, bg) = when {
        connected -> Triple(R.string.geek_services_state_connected, colors.success, colors.successSoft)
        service.active -> Triple(R.string.geek_services_state_active, colors.success, colors.successSoft)
        service.remainingGib == 0.0 -> Triple(R.string.geek_services_state_exhausted, colors.danger, colors.dangerSoft)
        service.daysLeft == 0 -> Triple(R.string.geek_services_state_expired, colors.danger, colors.dangerSoft)
        else -> Triple(R.string.geek_services_state_suspended, colors.warning, colors.warningSoft)
    }
    Row(
        modifier = Modifier.clip(Geek.shapes.badge).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(fg))
        Text(stringResource(text), style = Geek.type.micro, color = fg)
    }
}

@Composable
private fun Meter(label: String, value: String, unit: String?, fraction: Float, barColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val colors = Geek.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = Geek.type.micro.copy(fontWeight = FontWeight.Normal), color = colors.onGlassMuted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = Geek.type.pageTitle.copy(fontSize = 28.sp), color = colors.onGlass)
            if (unit != null) Text(unit, style = Geek.type.caption, color = colors.onGlassMuted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(colors.track)) {
            Box(Modifier.fillMaxWidth(fraction).height(5.dp).clip(RoundedCornerShape(3.dp)).background(barColor))
        }
    }
}

/** The dashed tear line of the ticket-shaped card. */
@Composable
private fun TicketDivider() {
    val line = Geek.colors.checkboxBorder
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = line,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
    }
}

@Composable
private fun SquareButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = Geek.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(Geek.shapes.button)
            .background(colors.softButton)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = colors.onGlass, modifier = Modifier.size(20.dp))
    }
}

/** The link button in the header and "افزودن" share one menu. */
@Composable
private fun AddLinkButton(actions: ServicesActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        GlassIconButton(GeekIcons.Link, stringResource(R.string.geek_services_link_description), { open = true })
        AddMenu(open, { open = false }, actions)
    }
}

@Composable
private fun AddChip(actions: ServicesActions) {
    val colors = Geek.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        GlassSurface(
            kind = GlassKind.Clear,
            shape = Geek.shapes.pill,
            modifier = Modifier.clickable(role = Role.Button) { open = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(GeekIcons.Plus, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.geek_services_add), style = Geek.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.onBackground)
            }
        }
        AddMenu(open, { open = false }, actions)
    }
}

@Composable
private fun AddMenu(open: Boolean, onDismiss: () -> Unit, actions: ServicesActions) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.geek_services_add_subscription), style = Geek.type.body) },
            leadingIcon = { Icon(GeekIcons.Link, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.onAddSubscription()
            },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.geek_services_add_clipboard), style = Geek.type.body) },
            leadingIcon = { Icon(GeekIcons.Copy, contentDescription = null) },
            onClick = {
                onDismiss()
                actions.onImportClipboard()
            },
        )
    }
}

@Composable
private fun ManualHint() {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.onBackground.copy(alpha = 0.34f), Geek.shapes.row)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassSurface(kind = GlassKind.Clear, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(38.dp)) {
            Icon(GeekIcons.Link, contentDescription = null, tint = colors.onBackground, modifier = Modifier.align(Alignment.Center).size(20.dp))
        }
        Text(stringResource(R.string.geek_services_manual_hint), style = Geek.type.body, color = colors.onBackground, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ManualRow(group: ManualGroup, selected: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    GlassSurface(
        kind = if (selected) GlassKind.Milk else GlassKind.Clear,
        shape = Geek.shapes.row,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val fg = if (selected) colors.onGlass else colors.onBackground
            Icon(GeekIcons.Link, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Text(
                group.name ?: stringResource(R.string.geek_services_manual_single),
                style = Geek.type.row,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                pluralStringResource(R.plurals.geek_manual_servers, group.servers, group.servers),
                style = Geek.type.caption,
                color = if (selected) colors.onGlassMuted else colors.onBackgroundMuted,
            )
        }
    }
}
