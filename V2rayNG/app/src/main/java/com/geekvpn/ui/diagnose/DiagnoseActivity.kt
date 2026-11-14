package com.geekvpn.ui.diagnose

import android.content.Intent
import androidx.activity.viewModels
import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geekvpn.diagnose.Check
import com.geekvpn.diagnose.Outcome
import com.geekvpn.diagnose.Verdict
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.support.ReportActivity
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

/** "عیب‌یاب اتصال" ([DiagnoseViewModel]). */
class DiagnoseActivity : BaseComponentActivity() {
    private val viewModel: DiagnoseViewModel by viewModels { DiagnoseViewModel.factory(application) }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                DiagnoseScreen(state, onAgain = viewModel::start, onReport = ::report, onBack = ::finish)
            }
        }
    }

    /** "گزارش مشکل" with what was found already written in. */
    private fun report() {
        val state = viewModel.uiState.value
        val verdict = state.verdict ?: return
        val text = getString(R.string.geek_diag_report_text, getString(verdictTitle(verdict))) + "\n" + state.summary.orEmpty()
        startActivity(Intent(this, ReportActivity::class.java).putExtra(ReportActivity.EXTRA_DESCRIPTION, text))
    }
}

@Composable
fun DiagnoseScreen(state: DiagnoseUiState, onAgain: () -> Unit, onReport: () -> Unit, onBack: () -> Unit) {
    val colors = Geek.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ChevronBack, stringResource(R.string.geek_servers_back_description), onBack)
            Text(stringResource(R.string.geek_diag_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
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
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    for (check in Check.entries) {
                        CheckRow(check, state.results[check], running = state.current == check)
                    }
                }
            }

            val verdict = state.verdict
            if (verdict != null) {
                GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tile, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(verdictTitle(verdict)),
                            style = Geek.type.label.copy(fontSize = 17.sp),
                            color = if (verdict == Verdict.AllGood) colors.success else colors.onGlass,
                        )
                        Text(stringResource(verdictAdvice(verdict)), style = Geek.type.body, color = colors.onGlassMuted)
                    }
                }
            } else {
                Text(
                    stringResource(R.string.geek_diag_running),
                    style = Geek.type.caption,
                    color = colors.onBackgroundMuted,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            GeekPrimaryButton(
                text = stringResource(R.string.geek_diag_again),
                icon = GeekIcons.Refresh,
                onClick = onAgain,
                enabled = !state.running,
            )
            GeekSecondaryButton(
                text = stringResource(R.string.geek_diag_report),
                icon = GeekIcons.Receipt,
                onClick = onReport,
                modifier = Modifier.fillMaxWidth(),
                enabled = verdict != null,
            )
            Text(
                stringResource(R.string.geek_diag_note),
                style = Geek.type.caption.copy(fontSize = 12.sp),
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun CheckRow(check: Check, outcome: Outcome?, running: Boolean) {
    val colors = Geek.colors
    val name = stringResource(checkName(check))
    val (status, tint) = when {
        running -> stringResource(R.string.geek_diag_state_running) to colors.onGlassMuted
        outcome == null -> stringResource(R.string.geek_diag_state_waiting) to colors.onGlassMuted
        else -> stringResource(outcomeName(outcome)) to outcomeColor(outcome)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = "$name: $status" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(name, style = Geek.type.body, color = colors.onGlass, modifier = Modifier.weight(1f))
        Text(status, style = Geek.type.caption, color = tint)
    }
}

@Composable
private fun outcomeColor(outcome: Outcome): Color = when (outcome) {
    Outcome.Ok -> Geek.colors.success
    Outcome.Skipped -> Geek.colors.onGlassMuted
    Outcome.Failed, Outcome.Filtered -> Geek.colors.danger
}

@StringRes
private fun checkName(check: Check): Int = when (check) {
    Check.Network -> R.string.geek_diag_check_network
    Check.Domestic -> R.string.geek_diag_check_domestic
    Check.International -> R.string.geek_diag_check_international
    Check.Server -> R.string.geek_diag_check_server
    Check.Tunnel -> R.string.geek_diag_check_tunnel
    Check.Api -> R.string.geek_diag_check_api
}

@StringRes
private fun outcomeName(outcome: Outcome): Int = when (outcome) {
    Outcome.Ok -> R.string.geek_diag_state_ok
    Outcome.Failed -> R.string.geek_diag_state_failed
    Outcome.Skipped -> R.string.geek_diag_state_skipped
    Outcome.Filtered -> R.string.geek_diag_state_filtered
}

@StringRes
internal fun verdictTitle(verdict: Verdict): Int = when (verdict) {
    Verdict.NoNetwork -> R.string.geek_diag_v_no_network
    Verdict.NoInternet -> R.string.geek_diag_v_no_internet
    Verdict.NoServer -> R.string.geek_diag_v_no_server
    Verdict.ServerFiltered -> R.string.geek_diag_v_server_filtered
    Verdict.InternationalDown -> R.string.geek_diag_v_international
    Verdict.ServerDown -> R.string.geek_diag_v_server_down
    Verdict.TunnelBroken -> R.string.geek_diag_v_tunnel
    Verdict.ApiDown -> R.string.geek_diag_v_api
    Verdict.AllGood -> R.string.geek_diag_v_ok
}

@StringRes
private fun verdictAdvice(verdict: Verdict): Int = when (verdict) {
    Verdict.NoNetwork -> R.string.geek_diag_a_no_network
    Verdict.NoInternet -> R.string.geek_diag_a_no_internet
    Verdict.NoServer -> R.string.geek_diag_a_no_server
    Verdict.ServerFiltered -> R.string.geek_diag_a_server_filtered
    Verdict.InternationalDown -> R.string.geek_diag_a_international
    Verdict.ServerDown -> R.string.geek_diag_a_server_down
    Verdict.TunnelBroken -> R.string.geek_diag_a_tunnel
    Verdict.ApiDown -> R.string.geek_diag_a_api
    Verdict.AllGood -> R.string.geek_diag_a_ok
}
