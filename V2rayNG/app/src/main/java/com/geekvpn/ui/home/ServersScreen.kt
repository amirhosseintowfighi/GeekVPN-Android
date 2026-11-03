package com.geekvpn.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.connection.ConnectionLogic
import com.geekvpn.smartconnect.FailoverThreshold
import com.geekvpn.ui.common.Eyebrow
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.Hairline
import com.geekvpn.ui.components.CountryBadge
import com.geekvpn.ui.components.GeekCheckbox
import com.geekvpn.ui.components.GeekSegmented
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.components.LatencyIndicator
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/** Servers.html: the active service's configs, automatic selection, and a real-delay test. */
@Composable
fun ServersScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onAutoServerChange: (Boolean) -> Unit,
    onTest: () -> Unit,
    onUpdate: () -> Unit,
    updating: Boolean,
    /** Opens the clean-IP scanner; null when it does not apply to the selected config. */
    onCleanIp: (() -> Unit)? = null,
    onFailoverChange: (FailoverThreshold) -> Unit = {},
    /** Stars or unstars a server (by GUID); starred ones are listed first. */
    onFavorite: (String) -> Unit = {},
) {
    val colors = Geek.colors
    var query by rememberSaveable { mutableStateOf("") }
    var byPing by rememberSaveable { mutableStateOf(false) }
    val visible = remember(query, state.servers, byPing) {
        val needle = query.trim()
        val matching = if (needle.isEmpty()) state.servers
        else state.servers.filter {
            it.title.contains(needle, ignoreCase = true) || it.countryCode?.contains(needle, ignoreCase = true) == true
        }
        // Starred ones stay on top either way.
        if (byPing) ConnectionLogic.favoritesFirst(ConnectionLogic.byDelay(matching) { it.delayMs }) { it.favorite } else matching
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().imePadding().statusBarsPadding().navigationBarsPadding(),
    ) {
        item(key = "top") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassIconButton(GeekIcons.ChevronBack, stringResource(R.string.geek_servers_back_description), onBack)
                Eyebrow(
                    pluralStringResource(R.plurals.geek_eyebrow_servers_count, state.servers.size, state.servers.size),
                    Modifier.weight(1f),
                )
                if (state.testing) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.onBackground, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                } else {
                    GlassIconButton(GeekIcons.Gauge, stringResource(R.string.geek_servers_test_description), onTest, enabled = state.servers.isNotEmpty())
                }
                GlassIconButton(GeekIcons.Refresh, stringResource(R.string.geek_servers_update_description), onUpdate, enabled = !updating)
            }
        }
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.geek_servers_title), style = Geek.type.pageTitle.copy(fontSize = 36.sp, lineHeight = 44.sp), color = colors.onBackground)
                Text(
                    pluralStringResource(R.plurals.geek_servers_count, state.servers.size, state.servers.size),
                    style = Geek.type.body,
                    color = colors.onBackgroundMuted,
                )
            }
        }
        item(key = "search") { SearchField(query, { query = it }) }
        if (state.servers.size > 1) {
            item(key = "sort") { SortChips(byPing) { byPing = it } }
        }
        item(key = "auto") { AutoCard(state.autoServer, state.failover, onAutoServerChange, onFailoverChange) }
        if (onCleanIp != null) {
            item(key = "clean-ip") { CleanIpCard(onCleanIp) }
        }
        if (state.servers.isEmpty()) {
            item(key = "empty") { EmptyLine(stringResource(R.string.geek_servers_empty)) }
        } else if (visible.isEmpty()) {
            item(key = "no-match") { EmptyLine(stringResource(R.string.geek_servers_no_match)) }
        } else {
            item(key = "list-top") { Hairline() }
        }
        items(visible, key = { it.guid }) { server ->
            Column {
                ServerItem(
                    server = server,
                    selected = server.guid == state.selected?.guid,
                    onClick = { onSelect(server.guid) },
                    onFavorite = { onFavorite(server.guid) },
                )
                Hairline()
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val colors = Geek.colors
    val hint = stringResource(R.string.geek_servers_search)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSurface(kind = GlassKind.Clear, shape = Geek.shapes.pill, modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(GeekIcons.Search, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(hint, style = Geek.type.body, color = colors.onGlassMuted)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onChange,
                        singleLine = true,
                        textStyle = Geek.type.body.copy(color = colors.onGlass),
                        cursorBrush = SolidColor(colors.onGlass),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoCard(
    auto: Boolean,
    failover: FailoverThreshold,
    onChange: (Boolean) -> Unit,
    onFailover: (FailoverThreshold) -> Unit,
) {
    val colors = Geek.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Servers.dc.html: automatic selection is a solid white card when on.
        val fg = if (auto) colors.onAction else colors.onGlass
        val muted = if (auto) colors.onAction.copy(alpha = 0.7f) else colors.onGlassMuted
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .then(
                    if (auto) Modifier.background(colors.action)
                    else Modifier.background(colors.softButton).border(1.5.dp, colors.clearGlassBorder, RoundedCornerShape(20.dp)),
                )
                .clickable(role = Role.Switch) { onChange(!auto) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(GeekIcons.Bolt, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.geek_servers_auto_title), style = Geek.type.row.copy(fontWeight = FontWeight.ExtraBold), color = fg)
                Text(stringResource(R.string.geek_servers_auto_hint), style = Geek.type.caption, color = muted)
            }
            GeekSwitch(checked = auto, onCheckedChange = null)
        }
        if (auto) FailoverChoice(failover, onFailover)
    }
}

/** Spec §3.5's adjustable threshold: when a running connection moves to another server. */
@Composable
private fun FailoverChoice(current: FailoverThreshold, onSelect: (FailoverThreshold) -> Unit) {
    val colors = Geek.colors
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.geek_failover_title), style = Geek.type.label.copy(fontSize = 13.sp), color = colors.onBackground)
        GeekSegmented(
            options = FailoverThreshold.entries,
            selected = current,
            label = { stringResource(it.label) },
            onSelect = onSelect,
            height = 44.dp,
        )
        Text(
            stringResource(if (current == FailoverThreshold.Off) R.string.geek_failover_hint_off else R.string.geek_failover_hint),
            style = Geek.type.caption,
            color = colors.onBackgroundMuted,
        )
    }
}

/** Servers.dc.html's line under the auto card: the way into the clean-IP scanner, for a CDN-fronted direct config. */
@Composable
private fun CleanIpCard(onOpen: () -> Unit) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.geek_scan_title), style = Geek.type.caption, color = colors.onBackgroundMuted, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.geek_scan_action), style = Geek.type.caption.copy(fontWeight = FontWeight.ExtraBold), color = colors.onBackground)
        Icon(GeekIcons.ChevronRow, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun ServerItem(server: ServerRow, selected: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    val colors = Geek.colors
    val selectedLabel = stringResource(R.string.geek_servers_selected_description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (selected) contentDescription = "${server.title}, $selectedLabel"
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CountryBadge(code = server.countryCode ?: "··", selected = selected, modifier = Modifier.width(30.dp))
        Text(
            server.title,
            style = Geek.type.row.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold),
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LatencyIndicator(delayMs = server.delayMs)
        FavoriteButton(server.favorite, onFavorite)
        GeekCheckbox(checked = selected, onCheckedChange = null)
    }
}

/** List order: the subscription's own, or fastest first by the last test. */
@Composable
private fun SortChips(byPing: Boolean, onChange: (Boolean) -> Unit) {
    val list = stringResource(R.string.geek_servers_sort_list)
    val ping = stringResource(R.string.geek_servers_sort_ping)
    GeekSegmented(options = listOf(false, true), selected = byPing, label = { if (it) ping else list }, onSelect = onChange, height = 44.dp)
}

/** Its own touch target inside the row, so starring does not also select. */
@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    val label = stringResource(if (favorite) R.string.geek_servers_unfavorite else R.string.geek_servers_favorite)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(Geek.shapes.pill)
            .toggleable(value = favorite, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (favorite) GeekIcons.StarFilled else GeekIcons.Star,
            contentDescription = null,
            tint = if (favorite) colors.warning else colors.onGlassMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = Geek.type.body,
        color = Geek.colors.onBackgroundMuted,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}
