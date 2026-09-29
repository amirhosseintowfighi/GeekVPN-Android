package com.geekvpn.ui.referral

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.GeekGraph
import com.geekvpn.account.Referral
import com.geekvpn.api.ApiException
import com.geekvpn.api.ReferralSummary
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat

data class ReferralUiState(
    val summary: ReferralSummary? = null,
    val failed: Boolean = false,
)

class ReferralViewModel(
    private val load: suspend () -> ReferralSummary,
    private val logFailure: (ApiException) -> Unit,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val state = MutableStateFlow(ReferralUiState())
    val uiState: StateFlow<ReferralUiState> = state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        state.value = state.value.copy(failed = false)
        viewModelScope.launch {
            state.value = try {
                ReferralUiState(summary = load())
            } catch (e: ApiException) {
                logFailure(e)
                state.value.copy(failed = true)
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                ReferralViewModel(
                    load = { GeekGraph.api.referral() },
                    logFailure = { LogUtil.w(AppConfig.TAG, "Referral: loading failed (${it.status})", it) },
                )
            }
        }
    }
}

/** "دعوت از دوستان": the invite link, what it earns, and what it has earned. */
class ReferralActivity : BaseComponentActivity() {
    private val viewModel: ReferralViewModel by viewModels { ReferralViewModel.factory() }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                ReferralScreen(state, onCopy = ::copy, onShare = ::share, onRetry = viewModel::refresh, onBack = ::finish)
            }
        }
    }

    private fun copy(link: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.geek_referral_title), link))
        Toast.makeText(this, R.string.geek_referral_copied, Toast.LENGTH_SHORT).show()
    }

    private fun share(link: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, getString(R.string.geek_referral_share_text, link))
        try {
            startActivity(Intent.createChooser(send, getString(R.string.geek_referral_share)))
        } catch (e: ActivityNotFoundException) {
            LogUtil.w(AppConfig.TAG, "Referral: no app to share with", e)
            copy(link)
        }
    }
}

@Composable
fun ReferralScreen(
    state: ReferralUiState,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = Geek.colors
    val locale = appLocale()
    val summary = state.summary
    val percent = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), onBack)
            Text(stringResource(R.string.geek_referral_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                summary == null && state.failed -> {
                    Text(stringResource(R.string.geek_referral_failed), style = Geek.type.body, color = colors.onBackgroundMuted)
                    GeekSecondaryButton(stringResource(R.string.geek_tickets_retry), onRetry, Modifier.fillMaxWidth(), icon = GeekIcons.Refresh)
                }
                summary?.code.isNullOrBlank() -> Text(
                    stringResource(if (summary == null) R.string.geek_tickets_loading else R.string.geek_referral_failed),
                    style = Geek.type.body,
                    color = colors.onBackgroundMuted,
                )
                else -> {
                    val link = Referral.link(BuildConfig.BOT_USERNAME, summary!!.code!!)
                    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tileLarge, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.geek_referral_intro), style = Geek.type.body, color = colors.onGlass)
                            Terms(summary, percent, locale)
                            Text(stringResource(R.string.geek_referral_your_link), style = Geek.type.label, color = colors.onGlassMuted)
                            Text(
                                link,
                                style = Geek.type.body.copy(textDirection = TextDirection.Ltr),
                                color = colors.link,
                                textAlign = TextAlign.Start,
                            )
                            GeekPrimaryButton(stringResource(R.string.geek_referral_share), GeekIcons.Link, { onShare(link) })
                            GeekSecondaryButton(stringResource(R.string.geek_referral_copy), { onCopy(link) }, Modifier.fillMaxWidth(), icon = GeekIcons.Copy)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(stringResource(R.string.geek_referral_invited), formatNumber((summary.invitedCount ?: 0).toLong(), locale), Modifier.weight(1f))
                        Stat(stringResource(R.string.geek_referral_converted), formatNumber((summary.convertedCount ?: 0).toLong(), locale), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(
                            stringResource(R.string.geek_referral_earned),
                            stringResource(R.string.geek_referral_toman, formatNumber(summary.totalEarned ?: 0, locale)),
                            Modifier.weight(1f),
                        )
                        Stat(
                            stringResource(R.string.geek_referral_pending),
                            stringResource(R.string.geek_referral_toman, formatNumber(summary.pendingEarned ?: 0, locale)),
                            Modifier.weight(1f),
                        )
                    }
                    Text(stringResource(R.string.geek_referral_wallet_note), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onBackgroundMuted)
                }
            }
        }
    }
}

@Composable
private fun Terms(summary: ReferralSummary, percent: NumberFormat, locale: java.util.Locale) {
    val colors = Geek.colors
    (summary.inviteeBonus ?: 0).takeIf { it > 0 }?.let {
        Text(stringResource(R.string.geek_referral_term_bonus, formatNumber(it, locale)), style = Geek.type.caption, color = colors.onGlassMuted)
    }
    (summary.firstPurchaseBps ?: 0).takeIf { it > 0 }?.let {
        Text(stringResource(R.string.geek_referral_term_first, percent.format(Referral.percent(it))), style = Geek.type.caption, color = colors.onGlassMuted)
    }
    (summary.recurringBps ?: 0).takeIf { it > 0 }?.let {
        Text(stringResource(R.string.geek_referral_term_recurring, percent.format(Referral.percent(it))), style = Geek.type.caption, color = colors.onGlassMuted)
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tile, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = Geek.type.caption, color = colors.onGlassMuted, maxLines = 1)
            // The text face: the number face draws the Persian thousands separator as an apostrophe.
            Text(value, style = Geek.type.row, color = colors.onGlass, maxLines = 1)
        }
    }
}
