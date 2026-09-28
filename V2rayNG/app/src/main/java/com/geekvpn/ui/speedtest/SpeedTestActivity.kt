package com.geekvpn.ui.speedtest

import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geekvpn.speedtest.SpeedPhase
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatDecimal
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import java.text.NumberFormat
import java.util.Locale

/** "تست سرعت" ([SpeedTestViewModel]). */
class SpeedTestActivity : BaseComponentActivity() {
    private val viewModel: SpeedTestViewModel by viewModels { SpeedTestViewModel.factory() }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                SpeedTestScreen(state, onToggle = viewModel::toggle, onBack = ::finish)
            }
        }
    }
}

@Composable
fun SpeedTestScreen(state: SpeedTestUiState, onToggle: () -> Unit, onBack: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    val result = state.result
    val shown = when {
        state.running -> state.liveMbps
        result?.downloadMbps != null -> result.downloadMbps
        else -> 0.0
    }
    val gauge by animateFloatAsState(shown.toFloat(), label = "speed")

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), onBack)
            Text(stringResource(R.string.geek_speed_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tileLarge, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        stringResource(
                            when (state.phase) {
                                SpeedPhase.Ping -> R.string.geek_speed_phase_ping
                                SpeedPhase.Download -> R.string.geek_speed_download
                                SpeedPhase.Upload -> R.string.geek_speed_upload
                                null -> if (result != null) R.string.geek_speed_download else R.string.geek_speed_ready
                            },
                        ),
                        style = Geek.type.label,
                        color = colors.onGlassMuted,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Text(
                        mbps(gauge.toDouble(), locale),
                        style = Geek.type.numberLarge.copy(fontSize = 56.sp),
                        maxLines = 1,
                        color = colors.onGlass,
                        textAlign = TextAlign.Center,
                    )
                    Text(stringResource(R.string.geek_speed_unit), style = Geek.type.caption, color = colors.onGlassMuted)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ResultTile(stringResource(R.string.geek_speed_ping), result?.pingMs?.let { stringResource(R.string.geek_speed_ms, NumberFormat.getIntegerInstance(locale).format(it)) }, Modifier.weight(1f))
                ResultTile(stringResource(R.string.geek_speed_download), result?.downloadMbps?.let { mbps(it, locale) }, Modifier.weight(1f))
                ResultTile(stringResource(R.string.geek_speed_upload), result?.uploadMbps?.let { mbps(it, locale) }, Modifier.weight(1f))
            }

            Text(
                stringResource(if (state.throughVpn) R.string.geek_speed_via_vpn else R.string.geek_speed_via_direct),
                style = Geek.type.caption,
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            if (state.failed) {
                Text(stringResource(R.string.geek_speed_failed), style = Geek.type.caption, color = colors.danger, modifier = Modifier.padding(horizontal = 4.dp))
            }
            GeekPrimaryButton(
                text = stringResource(if (state.running) R.string.geek_speed_stop else if (result != null) R.string.geek_speed_again else R.string.geek_speed_start),
                icon = if (state.running) GeekIcons.Close else GeekIcons.Gauge,
                onClick = onToggle,
            )
            Text(stringResource(R.string.geek_speed_note), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onBackgroundMuted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun ResultTile(label: String, value: String?, modifier: Modifier) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tile, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = Geek.type.caption, color = colors.onGlassMuted, maxLines = 1)
            Text(value ?: stringResource(R.string.geek_speed_none), style = Geek.type.numberSmall, color = colors.onGlass, maxLines = 1)
        }
    }
}

/** "۴۲٫۵" / "42.5": one decimal under 100, none above. */
private fun mbps(value: Double, locale: Locale): String = formatDecimal(value, locale, if (value < 100) 1 else 0)
