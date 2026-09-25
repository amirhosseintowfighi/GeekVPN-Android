package com.geekvpn.ui.scanner

import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.scanner.CleanIp
import com.geekvpn.scanner.NetworkIdentity
import com.geekvpn.scanner.ScanConfig
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.components.LatencyIndicator
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.SpaceGrotesk
import com.v2ray.ang.R
import java.util.Locale

interface ScannerActions {
    fun onBack()
    fun onStart()
    fun onStop()
    fun onDownloadTest(enabled: Boolean)
    fun onUse(ip: CleanIp)
    fun onRevert()
}

/**
 * The clean-IP scanner, opened from Servers (and Account) for a CDN-fronted
 * direct config. Not in the design set; it follows Servers.html's layout: a
 * glass header on the backdrop and one milk sheet below.
 */
@Composable
fun ScannerScreen(state: ScannerUiState, actions: ScannerActions, now: Long = System.currentTimeMillis()) {
    val colors = Geek.colors
    val target = state.target ?: return
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), actions::onBack)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.geek_scan_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
                Text(target.title, style = Geek.type.caption, color = colors.onBackgroundMuted, maxLines = 1)
            }
        }
        GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.sheet, modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                item(key = "info") { InfoCard(state, actions) }
                item(key = "download") { DownloadRow(state.downloadTest, enabled = !state.running, onChange = actions::onDownloadTest) }
                item(key = "action") { ScanControls(state, actions) }
                item(key = "results-title") { ResultsTitle(state, now) }
                if (state.shown.isEmpty() && !state.running) {
                    item(key = "empty") {
                        Text(stringResource(R.string.geek_scan_empty), style = Geek.type.caption, color = colors.onGlassMuted)
                    }
                }
                items(state.shown, key = { it.ip }) { ip ->
                    ResultRow(ip, inUse = ip.ip == state.override?.ip, onUse = { actions.onUse(ip) })
                }
            }
        }
    }
}

@Composable
private fun InfoCard(state: ScannerUiState, actions: ScannerActions) {
    val colors = Geek.colors
    val target = state.target ?: return
    Column(
        modifier = Modifier.fillMaxWidth().clip(Geek.shapes.tileLarge).background(colors.soft).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InfoLine(stringResource(R.string.geek_scan_domain), target.target.sni, ltr = true)
        InfoLine(stringResource(R.string.geek_scan_network), networkLabel(state.network), ltr = false)
        val override = state.override
        InfoLine(
            stringResource(R.string.geek_scan_in_use),
            override?.ip ?: stringResource(R.string.geek_scan_original),
            ltr = override != null,
        )
        if (override != null) {
            GeekSecondaryButton(
                text = stringResource(R.string.geek_scan_revert),
                icon = GeekIcons.Refresh,
                onClick = actions::onRevert,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.running,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, ltr: Boolean) {
    val colors = Geek.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Geek.type.caption, color = colors.onGlassMuted, modifier = Modifier.weight(1f))
        Text(
            value,
            style = if (ltr) Geek.type.numberSmall.copy(fontSize = 14.sp, textDirection = TextDirection.Ltr) else Geek.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.onGlass,
            maxLines = 1,
        )
    }
}

@Composable
private fun networkLabel(network: NetworkIdentity?): String = when {
    network == null -> stringResource(R.string.geek_scan_network_unknown)
    network.operatorLabel != null -> stringResource(network.operatorLabel)
    network.operatorName != null -> network.operatorName
    network.kind == NetworkIdentity.Kind.Wifi -> stringResource(R.string.geek_scan_network_wifi)
    network.kind == NetworkIdentity.Kind.Mobile -> stringResource(R.string.geek_scan_network_mobile)
    else -> stringResource(R.string.geek_scan_network_unknown)
}

@Composable
private fun DownloadRow(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Geek.shapes.tileLarge)
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(GeekIcons.Download, contentDescription = null, tint = colors.logoBlue, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.geek_scan_download), style = Geek.type.row, color = colors.onGlass)
            Text(stringResource(R.string.geek_scan_download_hint), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
        }
        GeekSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun ScanControls(state: ScannerUiState, actions: ScannerActions) {
    val colors = Geek.colors
    val locale = appLocale()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.running) {
            GeekSecondaryButton(
                text = stringResource(R.string.geek_scan_stop),
                icon = GeekIcons.Close,
                onClick = actions::onStop,
                modifier = Modifier.fillMaxWidth(),
            )
            val total = state.scan.total.coerceAtLeast(1)
            LinearProgressIndicator(
                progress = { (state.scan.tested.toFloat() / total).coerceIn(0f, 1f) },
                color = colors.logoBlue,
                trackColor = colors.track,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Text(
                stringResource(
                    R.string.geek_scan_progress,
                    formatNumber(state.scan.tested, locale),
                    formatNumber(state.scan.total, locale),
                    formatNumber(state.scan.results.size.toLong(), locale),
                ),
                style = Geek.type.caption,
                color = colors.onGlassMuted,
            )
        } else {
            GeekPrimaryButton(
                text = stringResource(if (state.shown.isEmpty()) R.string.geek_scan_start else R.string.geek_scan_again),
                icon = GeekIcons.Search,
                onClick = actions::onStart,
            )
            Text(
                stringResource(R.string.geek_scan_budget, formatNumber(ScanConfig.MAX_IPS.toLong(), locale), formatNumber(ScanConfig.STOP_AFTER.toLong(), locale)),
                style = Geek.type.caption.copy(fontSize = 12.sp),
                color = colors.onGlassMuted,
            )
        }
        val error = state.scan.error
        if (error != null && !state.running && state.scan.guid == state.target?.guid) {
            Text(error, style = Geek.type.caption, color = colors.danger)
        }
    }
}

@Composable
private fun ResultsTitle(state: ScannerUiState, now: Long) {
    val colors = Geek.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.geek_scan_results), style = Geek.type.label, color = colors.onGlass, modifier = Modifier.weight(1f))
        val record = state.record
        if (record != null && !state.running) {
            val ago = DateUtils.getRelativeTimeSpanString(record.scannedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
            Text(
                stringResource(if (record.isStale(now)) R.string.geek_scan_stale else R.string.geek_scan_when, ago),
                style = Geek.type.micro,
                color = if (record.isStale(now)) colors.warning else colors.onGlassMuted,
            )
        }
    }
}

@Composable
private fun ResultRow(ip: CleanIp, inUse: Boolean, onUse: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    val inUseLabel = stringResource(R.string.geek_scan_in_use_badge)
    val shape = Geek.shapes.row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (inUse) colors.soft else colors.chip)
            .border(if (inUse) 2.dp else 1.dp, if (inUse) colors.link else colors.track, shape)
            .clickable(role = Role.RadioButton, onClick = onUse)
            .semantics(mergeDescendants = true) {
                selected = inUse
                if (inUse) contentDescription = "${ip.ip}, $inUseLabel"
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    ip.ip,
                    style = Geek.type.body.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr),
                    color = colors.onGlass,
                )
                ip.colo?.takeIf { it.isNotBlank() }?.let { colo ->
                    Text(
                        colo,
                        style = Geek.type.micro.copy(fontWeight = FontWeight.Bold),
                        color = colors.link,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.soft).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (inUse) {
                    Icon(GeekIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(16.dp))
                }
            }
            Text(details(ip, locale), style = Geek.type.micro, color = colors.onGlassMuted)
        }
        Box(contentAlignment = Alignment.Center) {
            LatencyIndicator(ip.latencyMs)
        }
    }
}

/** "نوسان ۶ms · پینگ ۳۸ms · ۳٫۲ MB/s"; each part only when it was measured. */
@Composable
private fun details(ip: CleanIp, locale: Locale): String {
    // The latency itself is beside the signal bars.
    val parts = mutableListOf<String>()
    if (ip.jitterMs > 0) parts += stringResource(R.string.geek_scan_jitter, formatNumber(Math.round(ip.jitterMs), locale))
    if (ip.pingMs > 0) parts += stringResource(R.string.geek_scan_ping, formatNumber(ip.pingMs, locale))
    if (ip.downloadKBps > 0) parts += stringResource(R.string.geek_scan_speed, String.format(locale, "%.1f", ip.downloadKBps / 1000.0))
    return parts.joinToString(" · ")
}
