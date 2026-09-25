package com.geekvpn.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.components.CountryBadge
import com.geekvpn.ui.components.GeekCheckbox
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
) {
    val colors = Geek.colors
    var query by rememberSaveable { mutableStateOf("") }
    val visible = remember(query, state.servers) {
        val needle = query.trim()
        if (needle.isEmpty()) state.servers
        else state.servers.filter {
            it.title.contains(needle, ignoreCase = true) || it.countryCode?.contains(needle, ignoreCase = true) == true
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), onBack)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.geek_servers_title), style = Geek.type.pageTitle, color = colors.onBackground)
                Text(
                    pluralStringResource(R.plurals.geek_servers_count, state.servers.size, state.servers.size),
                    style = Geek.type.caption,
                    color = colors.onBackgroundMuted,
                )
            }
            if (state.testing) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.onBackground, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            } else {
                GlassIconButton(GeekIcons.Gauge, stringResource(R.string.geek_servers_test_description), onTest, enabled = state.servers.isNotEmpty())
            }
            GlassIconButton(GeekIcons.Refresh, stringResource(R.string.geek_servers_update_description), onUpdate, enabled = !updating)
        }

        GlassSurface(
            kind = GlassKind.Milk,
            shape = Geek.shapes.sheet,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                item(key = "search") { SearchField(query, { query = it }) }
                item(key = "auto") { AutoCard(state.autoServer, onAutoServerChange) }
                if (onCleanIp != null) {
                    item(key = "clean-ip") { CleanIpCard(onCleanIp) }
                }
                if (state.servers.isEmpty()) {
                    item(key = "empty") { EmptyLine(stringResource(R.string.geek_servers_empty)) }
                } else if (visible.isEmpty()) {
                    item(key = "no-match") { EmptyLine(stringResource(R.string.geek_servers_no_match)) }
                }
                items(visible, key = { it.guid }) { server ->
                    ServerItem(
                        server = server,
                        selected = server.guid == state.selected?.guid,
                        onClick = { onSelect(server.guid) },
                    )
                }
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
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .padding(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
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
                        cursorBrush = SolidColor(colors.action),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoCard(auto: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tileLarge, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { onChange(!auto) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GeekIcons.Bolt, contentDescription = null, tint = colors.logoBlue, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.geek_servers_auto_title), style = Geek.type.row, color = colors.onGlass)
                Text(stringResource(R.string.geek_servers_auto_hint), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
            }
            GeekSwitch(checked = auto, onCheckedChange = null)
        }
    }
}

/** Servers.html's second tile: the way into the clean-IP scanner, for a CDN-fronted direct config. */
@Composable
private fun CleanIpCard(onOpen: () -> Unit) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tileLarge, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(GeekIcons.Sparkle, contentDescription = null, tint = colors.logoBlue, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.geek_scan_title), style = Geek.type.row, color = colors.onGlass)
                Text(stringResource(R.string.geek_scan_entry_hint), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.link)
            }
            Icon(GeekIcons.ChevronStart, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ServerItem(server: ServerRow, selected: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    val selectedLabel = stringResource(R.string.geek_servers_selected_description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Geek.shapes.row)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                if (selected) contentDescription = "${server.title}, $selectedLabel"
            }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CountryBadge(code = server.countryCode ?: "··", selected = selected)
        Text(
            server.title,
            style = Geek.type.row.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold),
            color = colors.onGlass,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LatencyIndicator(delayMs = server.delayMs)
        GeekCheckbox(checked = selected, onCheckedChange = null)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = Geek.type.body,
        color = Geek.colors.onGlassMuted,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}
