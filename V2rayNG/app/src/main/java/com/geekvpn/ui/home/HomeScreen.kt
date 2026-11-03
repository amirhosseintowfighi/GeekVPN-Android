package com.geekvpn.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.geekvpn.connection.ConnectionPhase
import com.geekvpn.connection.RouteMode
import com.geekvpn.connection.ServerNames
import com.geekvpn.connection.TrafficMeter
import com.geekvpn.smartconnect.SmartStage
import com.geekvpn.ui.common.Eyebrow
import com.geekvpn.ui.common.Hairline
import com.geekvpn.ui.common.IndexRow
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatGib
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekMotion
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Home-Off.dc.html and Home-On.dc.html: the status line, the glasses button
 * and the Swiss index of route, server and exit under it.
 */
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
    val on = phase == ConnectionPhase.On
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StatusText(state)
        ConnectButton(
            phase = phase,
            onClick = if (phase == ConnectionPhase.Off) onConnect else onDisconnect,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        if (state.selected == null) {
            NoServiceCard(onChooseService)
        }

        AnimatedVisibility(visible = on, enter = GeekMotion.Reveal, exit = GeekMotion.Conceal) {
            SpeedRow(state)
        }

        Column {
            Hairline()
            AnimatedVisibility(visible = !on, enter = GeekMotion.Reveal, exit = GeekMotion.Conceal) {
                Column {
                    IndexRow(
                        code = "RT",
                        title = stringResource(R.string.geek_home_route),
                        value = stringResource(state.route.label),
                        onClick = onOpenRoute,
                    )
                    Hairline()
                }
            }
            SelectedServerRow(state, onOpenServers, onAutoServerChange)
            Hairline()
            ExitIpRow(state)
            Hairline()
            if (on) {
                IndexRow(
                    code = "CN",
                    title = stringResource(R.string.geek_home_connections),
                    value = formatNumber(state.connections.toLong(), appLocale()),
                )
            } else {
                val delay = state.selected?.delayMs ?: 0
                IndexRow(
                    code = "PG",
                    title = stringResource(R.string.geek_home_ping),
                    value = if (delay > 0) "$delay ms" else "—",
                    latinValue = true,
                )
            }
            Hairline()
        }

        val service = state.activeService
        AnimatedVisibility(visible = on && service != null, enter = GeekMotion.Reveal, exit = GeekMotion.Conceal) {
            service?.let { QuotaBar(it) }
        }
    }
}

// -- the glasses button ---------------------------------------------------------

@Composable
private fun ConnectButton(phase: ConnectionPhase, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
    // Turning on fills the inner disc white and lets the column of light rise
    // under the button; then one soft ring pulses out. A press sinks the button a little.
    val fill = remember { Animatable(if (on) 1f else 0f) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(on) {
        if (on) {
            if (fill.value < 1f) {
                fill.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
            }
        } else {
            pulse.snapTo(1f)
            fill.animateTo(0f, tween(250))
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "angle",
    )
    Box(modifier.size(214.dp), contentAlignment = Alignment.Center) {
        // Home-On.dc.html: a column of blue discs sinking from the button, fainter as they go.
        Canvas(Modifier.matchParentSize()) {
            val light = fill.value
            if (light > 0f) {
                val r = 105.dp.toPx()
                for (i in 16 downTo 0) {
                    val t = i / 16f
                    drawCircle(
                        color = colors.backdropGlow,
                        radius = r,
                        center = center + Offset(0f, (i * 23).dp.toPx() * light),
                        alpha = (0.56f - 0.5f * t * (2f - t)) * light,
                    )
                }
            }
            val radius = 95.dp.toPx() + 8.dp.toPx()
            val stroke = 2.dp.toPx()
            if (busy) {
                drawArc(
                    color = colors.onBackground,
                    startAngle = spin,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = center - Offset(radius, radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(stroke * 1.5f, cap = StrokeCap.Round),
                )
            }
            if (pulse.value < 1f) {
                drawCircle(
                    color = colors.onBackground.copy(alpha = 0.5f * (1f - pulse.value)),
                    radius = 95.dp.toPx() + 14.dp.toPx() * pulse.value,
                    style = Stroke(stroke),
                )
            }
        }
        GlassSurface(
            kind = GlassKind.Milk,
            shape = CircleShape,
            modifier = Modifier
                .size(190.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = description,
                    onClick = onClick,
                )
                .semantics { contentDescription = description },
        ) {
            // The inner disc: faint glass when off, solid white once connected.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(114.dp)
                    .clip(CircleShape)
                    .background(lerp(colors.onBackground.copy(alpha = 0.10f), colors.action, fill.value))
                    .border(1.dp, colors.onBackground.copy(alpha = 0.3f * (1f - fill.value)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_geek_logo),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(lerp(colors.onBackground.copy(alpha = 0.7f), colors.onAction, fill.value)),
                    modifier = Modifier.size(width = 77.dp, height = 38.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusText(state: HomeUiState) {
    val colors = Geek.colors
    // Busy phases share one layout, so a new smart-connect step only swaps its text.
    val look = when (state.phase) {
        ConnectionPhase.On, ConnectionPhase.Off -> state.phase
        else -> ConnectionPhase.Connecting
    }
    AnimatedContent(
        targetState = look,
        transitionSpec = {
            (fadeIn(tween(GeekMotion.PAGE_MS)) + slideInVertically(tween(GeekMotion.PAGE_MS)) { it / 4 }) togetherWith
                fadeOut(tween(GeekMotion.FADE_OUT_MS))
        },
        label = "status",
    ) { shownLook ->
        Row(
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (shownLook) {
                    ConnectionPhase.On -> {
                        Eyebrow(stringResource(R.string.geek_eyebrow_status_on))
                        Text(
                            text = accented(stringResource(R.string.geek_home_on_title), stringResource(R.string.geek_home_on_accent)),
                            style = Geek.type.pageTitle.copy(fontSize = 38.sp, lineHeight = 46.sp),
                            color = colors.onBackground,
                        )
                    }
                    ConnectionPhase.Off -> {
                        Eyebrow(stringResource(R.string.geek_eyebrow_status_off))
                        Text(stringResource(R.string.geek_home_off_title), style = Geek.type.pageTitle.copy(fontSize = 40.sp, lineHeight = 48.sp), color = colors.onBackground)
                        Text(stringResource(R.string.geek_home_off_hint), style = Geek.type.body, color = colors.onBackgroundMuted)
                    }
                    else -> {
                        Eyebrow(stringResource(R.string.geek_eyebrow_status_busy))
                        Text(busyText(state.phase, state.stage), style = Geek.type.pageTitle.copy(fontSize = 26.sp, lineHeight = 34.sp), color = colors.onBackground)
                        if (state.phase != ConnectionPhase.Stopping) {
                            Text(stringResource(R.string.geek_smart_cancel_hint), style = Geek.type.body, color = colors.onBackgroundMuted)
                        }
                    }
                }
            }
            if (shownLook == ConnectionPhase.On) {
                ConnectionTimer(state.connectedSince, Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

/** [text] with [accent] in the design's text blue, where it occurs ("متصل و «امن»"). */
@Composable
private fun accented(text: String, accent: String) = buildAnnotatedString {
    val at = if (accent.isEmpty()) -1 else text.indexOf(accent)
    if (at < 0) {
        append(text)
    } else {
        append(text.substring(0, at))
        withStyle(SpanStyle(color = Geek.colors.logoBlue)) { append(accent) }
        append(text.substring(at + accent.length))
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
private fun ConnectionTimer(since: Long, modifier: Modifier = Modifier) {
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
        style = Geek.type.numberLarge,
        color = Geek.colors.onBackground,
        modifier = modifier,
    )
}

// -- rows -------------------------------------------------------------------------

@Composable
private fun NoServiceCard(onChoose: () -> Unit) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Clear, shape = Geek.shapes.row, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.geek_home_no_service_title), style = Geek.type.row.copy(fontWeight = FontWeight.ExtraBold), color = colors.onGlass)
                Text(stringResource(R.string.geek_home_no_service_hint), style = Geek.type.caption, color = colors.onGlassMuted)
            }
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(Geek.shapes.pill)
                    .background(colors.action)
                    .clickable(role = Role.Button, onClick = onChoose)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.geek_home_choose), style = Geek.type.label.copy(fontSize = 13.sp), color = colors.onAction)
            }
        }
    }
}

/** The server in use: its country code, name, latency; the auto switch while disconnected. */
@Composable
private fun SelectedServerRow(state: HomeUiState, onOpen: () -> Unit, onAutoServerChange: (Boolean) -> Unit) {
    val server = state.selected
    val on = state.phase == ConnectionPhase.On
    val title = server?.title ?: stringResource(R.string.geek_home_server)
    val description = stringResource(R.string.geek_home_auto_description)
    val autoSwitch: (@Composable RowScope.() -> Unit)? = if (on) {
        null
    } else {
        {
            GeekSwitch(
                checked = state.autoServer,
                onCheckedChange = onAutoServerChange,
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
    IndexRow(
        code = server?.countryCode ?: "SV",
        title = if (on || server != null) title else stringResource(R.string.geek_home_server),
        hint = if (server != null) stringResource(R.string.geek_home_server) else null,
        value = when {
            on -> server?.delayMs?.takeIf { it > 0 }?.let { "$it ms" }
            else -> stringResource(if (state.autoServer) R.string.geek_home_server_auto else R.string.geek_home_server_manual)
        },
        latinValue = on,
        onClick = onOpen,
        trailing = autoSwitch,
    )
}

@Composable
private fun ExitIpRow(state: HomeUiState) {
    val locale = appLocale()
    val on = state.phase == ConnectionPhase.On
    val ip = state.exitIp
    val country = ip?.countryCode
    val text = when {
        ip != null && on && country != null -> ServerNames.countryName(country, locale)
        ip != null -> ip.ip
        state.exitIpLoading -> stringResource(R.string.geek_home_exit_ip_checking)
        else -> stringResource(R.string.geek_home_exit_ip_unknown)
    }
    IndexRow(code = "IP", title = stringResource(R.string.geek_home_exit_ip), value = text, latinValue = ip != null && !(on && country != null))
}

/** Home-On.dc.html: down, up and ping between two hairlines, in Space Grotesk. */
@Composable
private fun SpeedRow(state: HomeUiState) {
    val speed = state.speed ?: TrafficMeter.Speed(0, 0)
    val delay = state.selected?.delayMs ?: 0
    Column {
        Hairline()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatCell("Down", stringResource(R.string.geek_home_download), TrafficMeter.format(speed.downBytesPerSecond), Modifier.weight(1f))
            StatDivider()
            StatCell("Up", stringResource(R.string.geek_home_upload), TrafficMeter.format(speed.upBytesPerSecond), Modifier.weight(1f))
            StatDivider()
            StatCell("Ping", stringResource(R.string.geek_home_ping), (if (delay > 0) delay.toString() else "—") to (if (delay > 0) "ms" else ""), Modifier.weight(1f))
        }
        Hairline()
    }
}

@Composable
private fun StatCell(eyebrow: String, label: String, value: Pair<String, String>, modifier: Modifier) {
    val colors = Geek.colors
    Column(
        modifier
            .padding(vertical = 10.dp)
            .clearAndSetSemantics { contentDescription = "$label ${value.first} ${value.second}" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(eyebrow.uppercase(Locale.ROOT), style = Geek.type.eyebrow.copy(fontSize = 9.sp, letterSpacing = 0.2.em), color = colors.onBackgroundMuted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value.first, style = Geek.type.numberMedium, color = colors.onBackground, maxLines = 1)
            if (value.second.isNotEmpty()) {
                Text(value.second, style = Geek.type.numberSmall.copy(fontSize = 11.sp), color = colors.onBackgroundMuted, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Geek.colors.hairline))
}

/** "۱۲٫۴ از ۴۰ گیگ · ۲۸ روز مانده" over a thin bar of what is used. */
@Composable
private fun QuotaBar(service: com.geekvpn.connection.ServiceStatus) {
    val colors = Geek.colors
    val locale = appLocale()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = service.quotaGib?.let { stringResource(R.string.geek_home_quota, formatGib(service.usedGib, locale), formatGib(it, locale)) }
                    ?: stringResource(R.string.geek_home_unlimited),
                style = Geek.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            service.daysLeft?.let {
                Text(
                    pluralStringResource(R.plurals.geek_days_left, it, it),
                    style = Geek.type.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = colors.onBackgroundMuted,
                )
            }
        }
        val used = if (service.quotaGib == null) 0f else (1f - service.remainingFraction).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.track)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(used).background(colors.onBackground))
        }
    }
}

/** The label of each route mode, shared with the route sheet. */
val RouteMode.label: Int
    get() = when (this) {
        RouteMode.Smart -> R.string.geek_route_smart
        RouteMode.Global -> R.string.geek_route_global
        RouteMode.Direct -> R.string.geek_route_direct
    }
