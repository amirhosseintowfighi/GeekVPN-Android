package com.geekvpn.ui.services

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.geekvpn.account.AutoRenewState
import com.geekvpn.connection.ConnectionPhase
import com.geekvpn.connection.ServiceStatus
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.Hairline
import com.geekvpn.ui.common.PageTitle
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatGib
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekDangerButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GeekSegmented
import com.geekvpn.ui.components.GeekSwitch
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

    /** "تمدید": buy more of this service in the shop. */
    fun onRenew(subscriptionId: String)
    fun onAddSubscription()
    fun onImportClipboard()
    fun onUse(subscriptionId: String)
    fun onConnect()
    fun onDisconnect()
    fun onRefresh()
    fun onCopy(url: String)

    /** The Android share sheet with the service link. */
    fun onShare(url: String)
    fun onUseManual(groupId: String)
    fun onEditManual(groupId: String)

    /** "تمدید خودکار از کیف پول" on one service. */
    fun onAutoRenew(subscriptionId: String, enabled: Boolean)
}

/** Services.dc.html: the account's services as glass cards, then the manual links on hairlines. */
@Composable
fun ServicesScreen(
    state: HomeUiState,
    isSignedIn: Boolean,
    actions: ServicesActions,
    autoRenew: Map<String, AutoRenewState> = emptyMap(),
) {
    val colors = Geek.colors
    var showAll by rememberSaveable { mutableStateOf(false) }
    val active = state.services.filter { it.active }
    val shown = if (showAll) state.services else active

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PageTitle(stringResource(R.string.geek_services_title), Modifier.weight(1f), eyebrow = stringResource(R.string.geek_eyebrow_services))
            if (isSignedIn) {
                GlassIconButton(GeekIcons.Plus, stringResource(R.string.geek_services_buy_description), actions::onBuy)
            }
        }

        if (isSignedIn) {
            val activeLabel = stringResource(R.string.geek_services_filter_active, formatNumber(active.size.toLong(), appLocale()))
            val allLabel = stringResource(R.string.geek_services_filter_all, formatNumber(state.services.size.toLong(), appLocale()))
            GeekSegmented(
                options = listOf(false, true),
                selected = showAll,
                label = { all -> if (all) allLabel else activeLabel },
                onSelect = { showAll = it },
                height = 44.dp,
            )
            if (shown.isEmpty()) {
                Text(stringResource(R.string.geek_services_empty), style = Geek.type.body, color = colors.onBackgroundMuted)
            }
            shown.forEach { service ->
                ServiceCard(
                    service = service,
                    isActive = state.activeService?.subscriptionId == service.subscriptionId,
                    phase = state.phase,
                    updating = state.updating,
                    autoRenew = autoRenew[service.subscriptionId],
                    actions = actions,
                )
            }
        }

        Column {
            Row(Modifier.padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.geek_services_manual), style = Geek.type.sectionTitle.copy(fontSize = 17.sp), color = colors.onBackground, modifier = Modifier.weight(1f))
                AddLinkButton(actions)
            }
            Hairline()
            state.manualGroups.forEach { group ->
                ManualRow(
                    group = group,
                    selected = state.groupId == group.guid,
                    onClick = { actions.onUseManual(group.guid) },
                    // Configs imported one by one live in v2rayNG's default group, which has no link to edit.
                    onEdit = if (group.name != null) ({ actions.onEditManual(group.guid) }) else null,
                )
                Hairline()
            }
            Text(
                stringResource(R.string.geek_services_manual_hint),
                style = Geek.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun ServiceCard(
    service: ServiceStatus,
    isActive: Boolean,
    phase: ConnectionPhase,
    updating: Boolean,
    autoRenew: AutoRenewState?,
    actions: ServicesActions,
) {
    val colors = Geek.colors
    val locale = appLocale()
    val connected = isActive && phase == ConnectionPhase.On
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(service.title, style = Geek.type.sectionTitle.copy(fontSize = 18.sp), color = colors.onGlass, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "#" + service.subscriptionId.replace("-", "").take(8),
                        style = Geek.type.eyebrow.copy(letterSpacing = 0.12.em),
                        color = colors.onGlassMuted,
                    )
                }
                StateChip(service, connected)
            }
            Hairline()
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Meter(
                    eyebrow = stringResource(R.string.geek_eyebrow_time_left),
                    label = stringResource(R.string.geek_services_time_left),
                    value = service.daysLeft?.let { formatNumber(it.toLong(), locale) } ?: stringResource(R.string.geek_services_unlimited),
                    unit = if (service.daysLeft != null) stringResource(R.string.geek_services_days_unit) else null,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(colors.hairline))
                Meter(
                    eyebrow = stringResource(R.string.geek_eyebrow_data_left),
                    label = stringResource(R.string.geek_services_data_left),
                    value = service.remainingGib?.let { formatGib(it, locale) } ?: stringResource(R.string.geek_services_unlimited),
                    unit = if (service.remainingGib != null) stringResource(R.string.geek_services_gb_unit) else null,
                    modifier = Modifier.weight(1f),
                )
            }
            // What is left of the traffic, as the design's one thin bar.
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.track)) {
                Box(Modifier.fillMaxWidth(service.remainingFraction).fillMaxHeight().background(colors.onGlass))
            }
            if (autoRenew != null && autoRenew.available) {
                AutoRenewRow(autoRenew) { actions.onAutoRenew(service.subscriptionId, it) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    connected -> GeekSecondaryButton(
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
                SquareButton(GeekIcons.Bag, stringResource(R.string.geek_services_renew_description)) { actions.onRenew(service.subscriptionId) }
                SquareButton(GeekIcons.Refresh, stringResource(R.string.geek_services_refresh_description), enabled = !updating, onClick = actions::onRefresh)
                val url = service.subscriptionUrl
                if (url != null) {
                    var showQr by rememberSaveable { mutableStateOf(false) }
                    SquareButton(GeekIcons.Qr, stringResource(R.string.geek_services_qr_title)) { showQr = true }
                    if (showQr) {
                        LinkQrDialog(
                            url = url,
                            onCopy = {
                                showQr = false
                                actions.onCopy(url)
                            },
                            onShare = {
                                showQr = false
                                actions.onShare(url)
                            },
                            onDismiss = { showQr = false },
                        )
                    }
                }
            }
        }
    }
}

/** The wallet renews this service shortly before it runs out (the server's worker does it). */
@Composable
private fun AutoRenewRow(state: AutoRenewState, onChange: (Boolean) -> Unit) {
    val colors = Geek.colors
    val note = when (state.lastResult) {
        "insufficient_funds" -> R.string.geek_autorenew_last_funds
        "unavailable" -> R.string.geek_autorenew_last_unavailable
        "pending" -> R.string.geek_autorenew_last_pending
        "renewed" -> R.string.geek_autorenew_last_renewed
        else -> R.string.geek_autorenew_hint
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Geek.shapes.tile)
            .toggleable(value = state.enabled, enabled = !state.busy, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.geek_autorenew_title), style = Geek.type.label, color = colors.onGlass)
            Text(
                stringResource(note),
                style = Geek.type.caption,
                color = if (state.lastResult == "insufficient_funds" || state.lastResult == "unavailable") colors.danger else colors.onGlassMuted,
            )
        }
        GeekSwitch(checked = state.enabled, onCheckedChange = null, enabled = !state.busy)
    }
}

/** «متصل» solid white; any other state a quiet outlined pill, red when the service ran out. */
@Composable
private fun StateChip(service: ServiceStatus, connected: Boolean) {
    val colors = Geek.colors
    val (text, fg) = when {
        connected -> R.string.geek_services_state_connected to colors.onAction
        service.active -> R.string.geek_services_state_active to colors.onGlass
        service.remainingGib == 0.0 -> R.string.geek_services_state_exhausted to colors.danger
        service.daysLeft == 0 -> R.string.geek_services_state_expired to colors.danger
        else -> R.string.geek_services_state_suspended to colors.warning
    }
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(Geek.shapes.pill)
            .then(
                if (connected) Modifier.background(colors.action)
                else Modifier.background(colors.softButton).border(1.dp, colors.checkboxBorder, Geek.shapes.pill),
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(text), style = Geek.type.micro.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = fg)
    }
}

/** "TIME LEFT / ۲۸ روز": the Latin eyebrow over a heavy number and its unit. */
@Composable
private fun Meter(eyebrow: String, label: String, value: String, unit: String?, modifier: Modifier) {
    val colors = Geek.colors
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = listOfNotNull(label, value, unit).joinToString(" ") }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(eyebrow.uppercase(java.util.Locale.ROOT), style = Geek.type.eyebrow.copy(fontSize = 9.sp), color = colors.onGlassMuted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = Geek.type.sectionTitle.copy(fontSize = 22.sp), color = colors.onGlass)
            if (unit != null) Text(unit, style = Geek.type.caption.copy(fontSize = 13.sp), color = colors.onGlassMuted, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** A round glass button with one icon (renew, refresh, QR). */
@Composable
private fun SquareButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = Geek.colors
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(Geek.shapes.pill)
            .background(colors.softButton)
            .border(1.5.dp, colors.clearGlassBorder, Geek.shapes.pill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = colors.onGlass, modifier = Modifier.size(20.dp))
    }
}

/** «افزودن +» beside «لینک‌های دستی»: a glass pill opening the add menu. */
@Composable
private fun AddLinkButton(actions: ServicesActions) {
    val colors = Geek.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        GlassSurface(
            kind = GlassKind.Clear,
            shape = Geek.shapes.pill,
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.geek_services_link_description)) { open = true },
        ) {
            Row(
                modifier = Modifier.heightIn(min = 44.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.geek_services_add), style = Geek.type.label.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = colors.onBackground)
                Icon(GeekIcons.Plus, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(16.dp))
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

/** One manual link on the hairline list: the selected one in white, with an edit button when it has a link. */
@Composable
private fun ManualRow(group: ManualGroup, selected: Boolean, onClick: () -> Unit, onEdit: (() -> Unit)?) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(colors.onBackground) else Modifier.border(1.5.dp, colors.checkboxBorder, CircleShape)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                group.name ?: stringResource(R.string.geek_services_manual_single),
                style = Geek.type.row.copy(fontSize = 16.sp),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.geek_manual_servers, group.servers, group.servers),
                style = Geek.type.caption,
                color = colors.onBackgroundMuted,
            )
        }
        if (onEdit != null) {
            val label = stringResource(R.string.geek_links_edit_title)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(Geek.shapes.pill)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = onEdit)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(GeekIcons.Pencil, contentDescription = null, tint = colors.onBackgroundMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
