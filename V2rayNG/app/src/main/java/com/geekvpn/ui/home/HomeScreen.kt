package com.geekvpn.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.connection.ConnectionPhase
import com.geekvpn.connection.RouteMode
import com.geekvpn.connection.ServerNames
import com.geekvpn.connection.TrafficMeter
import com.geekvpn.smartconnect.SmartStage
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatGib
import com.geekvpn.ui.components.CountryBadge
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.components.LatencyIndicator
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.SpaceGrotesk
import com.v2ray.ang.R
import kotlinx.coroutines.delay
import java.util.Locale

/** Home-Off.html and Home-On.html: the connect button and what surrounds it. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenRoute: () -> Unit,
    onAutoServerChange: (Boolean) -> Unit,
    onChooseService: () -> Unit,
) {
    val phase = state.phase
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectButton(
                phase = phase,
                onClick = if (phase == ConnectionPhase.Off) onConnect else onDisconnect,
            )
            StatusText(state)
        }

        if (state.selected == null) {
            NoServiceCard(onChooseService)
        } else {
            ServerCard(state, onOpenServers)
        }

        if (phase != ConnectionPhase.On) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RouteTile(state.route, onOpenRoute, Modifier.weight(1f))
                AutoServerTile(state.autoServer, onAutoServerChange, Modifier.weight(1f))
            }
        }

        StatsRow(state)

        if (phase == ConnectionPhase.On) {
            state.activeService?.let { QuotaBar(it) }
        }
    }
}

// -- the glasses button ---------------------------------------------------------

@Composable
private fun ConnectButton(phase: ConnectionPhase, onClick: () -> Unit) {
    val colors = Geek.colors
    val on = phase == ConnectionPhase.On
    val busy = phase == ConnectionPhase.Testing || phase == ConnectionPhase.Connecting || phase == ConnectionPhase.Stopping
    val description = stringResource(
        when {
            on -> R.string.geek_home_disconnect_description
            busy -> R.string.geek_home_cancel_description
            else -> R.string.geek_home_connect_description
        }
    )
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "angle",
    )
    Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(236.dp)) {
            val stroke = 2.dp.toPx()
            val radius = size.minDimension / 2 - 12.dp.toPx()
            when {
                on -> {
                    drawCircle(colors.onBackground.copy(alpha = 0.22f), radius = radius, style = Stroke(stroke * 2))
                    drawArc(
                        color = colors.onBackground,
                        startAngle = 110f,
                        sweepAngle = 300f,
                        useCenter = false,
                        topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                        style = Stroke(stroke * 2, cap = StrokeCap.Round),
                    )
                }
                busy -> drawArc(
                    color = colors.onBackground,
                    startAngle = spin,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(stroke * 2, cap = StrokeCap.Round),
                )
                else -> drawCircle(
                    color = colors.onBackground.copy(alpha = 0.45f),
                    radius = radius,
                    style = Stroke(
                        stroke,
                        cap = StrokeCap.Round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(2.dp.toPx(), 9.dp.toPx())
                        ),
                    ),
                )
            }
        }
        GlassSurface(
            kind = if (on) GlassKind.Milk else GlassKind.Clear,
            shape = CircleShape,
            modifier = Modifier
                .size(192.dp)
                .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
                .semantics { contentDescription = description },
        ) {
            Image(
                painter = painterResource(R.drawable.ic_geek_logo),
                contentDescription = null,
                // White on clear glass; on milk glass the logo takes the action colour.
                colorFilter = if (on) ColorFilter.tint(colors.action) else null,
                modifier = Modifier.align(Alignment.Center).size(107.dp),
            )
        }
    }
}

@Composable
private fun StatusText(state: HomeUiState) {
    val colors = Geek.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        when (state.phase) {
            ConnectionPhase.On -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(colors.successBright))
                    Text(stringResource(R.string.geek_home_on_title), style = Geek.type.row, color = colors.onBackground)
                }
                ConnectionTimer(state.connectedSince)
            }
            ConnectionPhase.Off -> {
                Text(
                    stringResource(R.string.geek_home_off_title),
                    style = Geek.type.pageTitle,
                    color = colors.onBackground,
                )
                Text(
                    stringResource(R.string.geek_home_off_hint),
                    style = Geek.type.caption,
                    color = colors.onBackgroundMuted,
                )
            }
            else -> {
                Text(
                    busyText(state.phase, state.stage),
                    style = Geek.type.sectionTitle,
                    color = colors.onBackground,
                )
                if (state.phase != ConnectionPhase.Stopping) {
                    Text(
                        stringResource(R.string.geek_smart_cancel_hint),
                        style = Geek.type.caption,
                        color = colors.onBackgroundMuted,
                    )
                }
            }
        }
    }
}

/** What the connection is doing: smart connect's step (spec §3.6), else the phase. */
@Composable
private fun busyText(phase: ConnectionPhase, stage: SmartStage?): String = when {
    phase == ConnectionPhase.Stopping -> stringResource(R.string.geek_home_stopping)
    stage == SmartStage.FindingIp -> stringResource(R.string.geek_smart_finding_ip)
    stage == SmartStage.Testing -> stringResource(R.string.geek_smart_testing)
    stage is SmartStage.Connecting && stage.attempt > 1 ->
        stringResource(R.string.geek_smart_connecting_attempt, stage.attempt, stage.of)
    phase == ConnectionPhase.Testing -> stringResource(R.string.geek_home_testing)
    else -> stringResource(R.string.geek_home_connecting)
}

/** "00:12:48", Latin digits as in the design, ticking once a second. */
@Composable
private fun ConnectionTimer(since: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L - (now % 1_000L))
        }
    }
    val seconds = if (since > 0) ((now - since) / 1_000).coerceAtLeast(0) else 0
    Text(
        text = String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60),
        style = Geek.type.numberLarge.copy(fontSize = 34.sp, letterSpacing = 1.sp),
        color = Geek.colors.onBackground,
    )
}

// -- cards ------------------------------------------------------------------------

@Composable
private fun NoServiceCard(onChoose: () -> Unit) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.row, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.warningSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GeekIcons.Link, contentDescription = null, tint = colors.warning, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.geek_home_no_service_title), style = Geek.type.row.copy(fontWeight = FontWeight.ExtraBold), color = colors.onGlass)
                Text(stringResource(R.string.geek_home_no_service_hint), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
            }
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.action)
                    .clickable(role = Role.Button, onClick = onChoose)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.geek_home_choose), style = Geek.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.onAction)
            }
        }
    }
}

@Composable
private fun ServerCard(state: HomeUiState, onOpen: () -> Unit) {
    val colors = Geek.colors
    val server = state.selected ?: return
    GlassSurface(
        kind = GlassKind.Milk,
        shape = Geek.shapes.row,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CountryBadge(code = server.countryCode ?: "··", selected = true, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.geek_home_server), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
                Text(server.title, style = Geek.type.row.copy(fontWeight = FontWeight.ExtraBold), color = colors.onGlass, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LatencyIndicator(delayMs = server.delayMs)
            Icon(GeekIcons.ChevronStart, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RouteTile(route: RouteMode, onClick: () -> Unit, modifier: Modifier) {
    GlassTile(icon = GeekIcons.Route, label = stringResource(R.string.geek_home_route), value = stringResource(route.label), modifier = modifier.clickable(role = Role.Button, onClick = onClick)) {
        Icon(GeekIcons.ChevronStart, contentDescription = null, tint = Geek.colors.onBackground, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AutoServerTile(auto: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    val description = stringResource(R.string.geek_home_auto_description)
    GlassTile(
        icon = GeekIcons.Bolt,
        label = stringResource(R.string.geek_home_server),
        value = stringResource(if (auto) R.string.geek_home_server_auto else R.string.geek_home_server_manual),
        modifier = modifier
            .clickable(role = Role.Switch, onClickLabel = description) { onChange(!auto) }
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        GeekSwitch(checked = auto, onCheckedChange = null)
    }
}

@Composable
private fun GlassTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier,
    trailing: @Composable RowScope.() -> Unit,
) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Clear, shape = RoundedCornerShape(18.dp), modifier = modifier.height(56.dp)) {
        Row(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = Geek.type.micro.copy(fontWeight = FontWeight.Normal), color = colors.onBackgroundMuted)
                Text(value, style = Geek.type.row.copy(fontSize = 14.sp), color = colors.onBackground, maxLines = 1)
            }
            trailing()
        }
    }
}

// -- stats ------------------------------------------------------------------------

@Composable
private fun StatsRow(state: HomeUiState) {
    val colors = Geek.colors
    val on = state.phase == ConnectionPhase.On
    val locale = appLocale()
    val content: @Composable RowScope.() -> Unit = {
        if (on) {
            val speed = state.speed ?: TrafficMeter.Speed(0, 0)
            StatCell(GeekIcons.ArrowDown, stringResource(R.string.geek_home_download), Modifier.weight(1f)) { NumberWithUnit(TrafficMeter.format(speed.downBytesPerSecond)) }
            StatDivider()
            StatCell(GeekIcons.ArrowUp, stringResource(R.string.geek_home_upload), Modifier.weight(1f)) { NumberWithUnit(TrafficMeter.format(speed.upBytesPerSecond)) }
            StatDivider()
        }
        StatCell(GeekIcons.Search, stringResource(R.string.geek_home_exit_ip), Modifier.weight(if (on) 1f else 1.4f)) {
            val ip = state.exitIp
            val country = ip?.countryCode
            val text = when {
                ip != null && on && country != null -> ServerNames.countryName(country, locale)
                ip != null -> ip.ip
                state.exitIpLoading -> stringResource(R.string.geek_home_exit_ip_checking)
                else -> stringResource(R.string.geek_home_exit_ip_unknown)
            }
            Text(
                text = text,
                style = Geek.type.row.copy(fontWeight = FontWeight.ExtraBold, textDirection = TextDirection.Content),
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatDivider()
        StatCell(GeekIcons.Gauge, stringResource(R.string.geek_home_ping), Modifier.weight(1f)) {
            val delay = state.selected?.delayMs ?: 0
            NumberWithUnit(if (delay > 0) delay.toString() to "ms" else "—" to "")
        }
    }
    val rowModifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 10.dp, vertical = 12.dp)
    if (on) {
        GlassSurface(kind = GlassKind.Clear, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Row(rowModifier, content = content)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.onBackground.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                .then(rowModifier),
            content = content,
        )
    }
}

@Composable
private fun StatCell(icon: ImageVector, label: String, modifier: Modifier, value: @Composable () -> Unit) {
    val colors = Geek.colors
    Column(modifier.padding(horizontal = 4.dp).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = colors.onBackgroundMuted, modifier = Modifier.size(12.dp))
            Text(label, style = Geek.type.micro.copy(fontWeight = FontWeight.Normal), color = colors.onBackgroundMuted, maxLines = 1)
        }
        value()
    }
}

@Composable
private fun NumberWithUnit(value: Pair<String, String>) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value.first, style = Geek.type.numberMedium.copy(fontSize = 16.sp), color = Geek.colors.onBackground)
        if (value.second.isNotEmpty()) {
            Text(value.second, style = Geek.type.numberSmall.copy(fontSize = 11.sp), color = Geek.colors.onBackgroundMuted)
        }
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Geek.colors.onBackground.copy(alpha = 0.25f)))
}

/** "۱۲٫۴ از ۴۰ گیگ · ۲۸ روز مانده" over twenty segments of what is left. */
@Composable
private fun QuotaBar(service: com.geekvpn.connection.ServiceStatus) {
    val colors = Geek.colors
    val locale = appLocale()
    Column(Modifier.padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = service.quotaGib?.let { stringResource(R.string.geek_home_quota, formatGib(service.usedGib, locale), formatGib(it, locale)) }
                    ?: stringResource(R.string.geek_home_unlimited),
                style = Geek.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            service.daysLeft?.let {
                Text(
                    pluralStringResource(R.plurals.geek_days_left, it, it),
                    style = Geek.type.caption.copy(fontSize = 12.sp),
                    color = colors.onBackgroundMuted,
                )
            }
        }
        val lit = if (service.quotaGib == null) SEGMENTS else ((1f - service.remainingFraction) * SEGMENTS).toInt().coerceIn(0, SEGMENTS)
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(SEGMENTS) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (index < lit) colors.onBackground else colors.onBackground.copy(alpha = 0.22f)),
                )
            }
        }
    }
}

private const val SEGMENTS = 20

/** The label of each route mode, shared with the route sheet. */
val RouteMode.label: Int
    get() = when (this) {
        RouteMode.Smart -> R.string.geek_route_smart
        RouteMode.Global -> R.string.geek_route_global
        RouteMode.Direct -> R.string.geek_route_direct
    }
