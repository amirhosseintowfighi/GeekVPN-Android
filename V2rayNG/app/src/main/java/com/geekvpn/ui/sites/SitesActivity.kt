package com.geekvpn.ui.sites

import androidx.activity.viewModels
import androidx.annotation.StringRes
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

/** "سایت‌های استثنا" ([SitesViewModel]). */
class SitesActivity : BaseComponentActivity() {
    private val viewModel: SitesViewModel by viewModels { SitesViewModel.factory() }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                SitesScreen(state, SitesActions(viewModel::type, viewModel::add, viewModel::remove, ::finish))
            }
        }
    }
}

class SitesActions(
    val onType: (String) -> Unit,
    val onAdd: (direct: Boolean) -> Unit,
    val onRemove: (String) -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun SitesScreen(state: SitesUiState, actions: SitesActions) {
    val colors = Geek.colors
    val label = stringResource(R.string.geek_sites_input)
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ChevronBack, stringResource(R.string.geek_servers_back_description), actions.onBack)
            Text(stringResource(R.string.geek_sites_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.geek_sites_intro), style = Geek.type.body, color = colors.onBackgroundMuted)

            Text(label, style = Geek.type.label, color = colors.onBackground, modifier = Modifier.padding(horizontal = 4.dp))
            GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
                    BasicTextField(
                        value = state.input,
                        onValueChange = actions.onType,
                        singleLine = true,
                        textStyle = Geek.type.body.copy(color = colors.onGlass, textDirection = TextDirection.Ltr),
                        cursorBrush = SolidColor(colors.action),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                    )
                }
            }
            if (state.invalid) {
                Text(stringResource(R.string.geek_sites_invalid), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.danger, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GeekSecondaryButton(
                    text = stringResource(R.string.geek_sites_add_direct),
                    onClick = { actions.onAdd(true) },
                    modifier = Modifier.weight(1f),
                    enabled = state.loaded && state.input.isNotBlank(),
                )
                GeekSecondaryButton(
                    text = stringResource(R.string.geek_sites_add_proxy),
                    onClick = { actions.onAdd(false) },
                    modifier = Modifier.weight(1f),
                    enabled = state.loaded && state.input.isNotBlank(),
                )
            }

            SiteList(R.string.geek_sites_direct_title, R.string.geek_sites_direct_empty, state.lists.direct, actions.onRemove)
            SiteList(R.string.geek_sites_proxy_title, R.string.geek_sites_proxy_empty, state.lists.proxy, actions.onRemove)

            if (state.changed) {
                Text(stringResource(R.string.geek_sites_applied), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onBackgroundMuted, modifier = Modifier.padding(horizontal = 4.dp))
            }
            Text(stringResource(R.string.geek_sites_note), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onBackgroundMuted, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun SiteList(@StringRes title: Int, @StringRes empty: Int, sites: List<String>, onRemove: (String) -> Unit) {
    val colors = Geek.colors
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tile, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(stringResource(title), style = Geek.type.label, color = colors.onGlass, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (sites.isEmpty()) {
                Text(stringResource(empty), style = Geek.type.caption, color = colors.onGlassMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            for (site in sites) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        site,
                        style = Geek.type.body.copy(textDirection = TextDirection.Ltr),
                        color = colors.onGlass,
                        modifier = Modifier.weight(1f),
                    )
                    GlassIconButton(GeekIcons.Close, stringResource(R.string.geek_sites_remove, site), onClick = { onRemove(site) })
                }
            }
        }
    }
}
