package com.geekvpn.ui.support

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geekvpn.auth.TelegramLink
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekMotion
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
import kotlinx.coroutines.launch

/**
 * "گزارش مشکل": the customer describes the problem, the app adds a
 * technical report with nothing that identifies a server, and it goes to
 * support as a ticket the operator answers in the bot. Signed out, there is
 * no account to open a ticket for, so the report is copied for the bot chat.
 */
class ReportActivity : BaseComponentActivity() {
    private val viewModel: ReportViewModel by viewModels { ReportViewModel.factory(application) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ReportEvent.Sent -> {
                            val text = event.reference?.let { getString(R.string.geek_report_sent_ref, it) }
                                ?: getString(R.string.geek_report_sent)
                            Toast.makeText(this@ReportActivity, text, Toast.LENGTH_LONG).show()
                            finish()
                        }
                        is ReportEvent.Failed -> Toast.makeText(
                            this@ReportActivity,
                            event.serverText ?: getString(event.message),
                            Toast.LENGTH_LONG,
                        ).show()
                        is ReportEvent.Copy -> copyAndOpenBot(event.text)
                    }
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                ReportScreen(state, viewModel::describe, viewModel::send, ::finish)
            }
        }
    }

    private fun copyAndOpenBot(text: String) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.geek_report_title), text))
        Toast.makeText(this, R.string.geek_report_copied, Toast.LENGTH_LONG).show()
        if (BuildConfig.BOT_USERNAME.isEmpty()) return
        val link = "https://t.me/${BuildConfig.BOT_USERNAME}"
        for (uri in listOfNotNull(TelegramLink.appUri(link), link)) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
                return
            } catch (e: ActivityNotFoundException) {
                LogUtil.i(AppConfig.TAG, "Report: no app for ${uri.substringBefore(':')} links", e)
            }
        }
    }
}

@Composable
fun ReportScreen(state: ReportUiState, onDescribe: (String) -> Unit, onSend: () -> Unit, onBack: () -> Unit) {
    val colors = Geek.colors
    var showTechnical by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.geek_report_describe)

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), onBack)
            Text(
                stringResource(R.string.geek_report_title),
                style = Geek.type.pageTitle.copy(fontSize = 22.sp),
                color = colors.onBackground,
            )
        }
        GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.sheet, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.geek_report_intro), style = Geek.type.body, color = colors.onGlassMuted)

                Text(label, style = Geek.type.label, color = colors.onGlass, modifier = Modifier.padding(horizontal = 4.dp))
                GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                    Box(Modifier.fillMaxWidth().padding(14.dp)) {
                        BasicTextField(
                            value = state.description,
                            onValueChange = onDescribe,
                            textStyle = Geek.type.body.copy(color = colors.onGlass, textDirection = TextDirection.Content),
                            cursorBrush = SolidColor(colors.action),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp).semantics { contentDescription = label },
                        )
                    }
                }
                if (state.tooShort) {
                    Text(stringResource(R.string.geek_report_too_short), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.danger)
                }

                Text(stringResource(R.string.geek_report_privacy), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
                GeekSecondaryButton(
                    text = stringResource(if (showTechnical) R.string.geek_report_hide_technical else R.string.geek_report_show_technical),
                    onClick = { showTechnical = !showTechnical },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.technical != null,
                )
                AnimatedVisibility(visible = showTechnical && state.technical != null, enter = GeekMotion.Reveal, exit = GeekMotion.Conceal) {
                    GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            state.technical.orEmpty(),
                            style = Geek.type.caption.copy(fontSize = 11.sp, textDirection = TextDirection.Ltr),
                            color = colors.onGlassMuted,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (!state.signedIn) {
                    Text(stringResource(R.string.geek_report_guest), style = Geek.type.caption, color = colors.onGlassMuted)
                }
                GeekPrimaryButton(
                    text = stringResource(if (state.signedIn) R.string.geek_report_send else R.string.geek_report_copy),
                    icon = if (state.signedIn) GeekIcons.Check else GeekIcons.Copy,
                    onClick = onSend,
                    enabled = !state.sending,
                )
            }
        }
    }
}
