package com.geekvpn.ui.dns

import android.app.Application
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geekvpn.routing.DnsPresets
import com.geekvpn.ui.advanced.MmkvSettingsStore
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.SettingsCard
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DnsUiState(
    /** The stored remote DNS; null until read. */
    val stored: String? = null,
) {
    val preset: DnsPresets.Preset? get() = stored?.let { DnsPresets.of(it) }
}

class DnsViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(DnsUiState())
    val uiState: StateFlow<DnsUiState> = state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { MmkvSettingsStore.string(AppConfig.PREF_REMOTE_DNS, "") }
            state.value = DnsUiState(stored)
        }
    }

    /**
     * Through [MmkvSettingsStore], as «تنظیمات پیشرفته»: a running connection
     * is rebuilt with it when Home comes back.
     */
    fun select(preset: DnsPresets.Preset) {
        state.value = DnsUiState(preset.servers)
        viewModelScope.launch(Dispatchers.IO) { MmkvSettingsStore.put(AppConfig.PREF_REMOTE_DNS, preset.servers) }
    }
}

/** «DNS» ([DnsPresets]). */
class DnsActivity : BaseComponentActivity() {
    private val viewModel: DnsViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        // «تنظیمات پیشرفته» may have changed it meanwhile.
        viewModel.load()
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                DnsScreen(state, onSelect = viewModel::select, onBack = ::finish)
            }
        }
    }
}

@Composable
fun DnsScreen(state: DnsUiState, onSelect: (DnsPresets.Preset) -> Unit, onBack: () -> Unit) {
    val colors = Geek.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ChevronBack, stringResource(R.string.geek_servers_back_description), onBack)
            Text(stringResource(R.string.geek_dns_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.geek_dns_intro), style = Geek.type.caption, color = colors.onBackgroundMuted)
            SettingsCard(Modifier.selectableGroup()) {
                DnsPresets.Preset.entries.forEachIndexed { index, preset ->
                    if (index > 0) SettingsDivider()
                    PresetRow(preset, selected = state.preset == preset, onClick = { onSelect(preset) })
                }
            }
            if (state.stored != null && state.preset == null) {
                Text(
                    stringResource(R.string.geek_dns_custom, state.stored),
                    style = Geek.type.caption.copy(textDirection = TextDirection.Content),
                    color = colors.onBackground,
                )
            }
            Text(
                stringResource(R.string.geek_dns_note),
                style = Geek.type.caption.copy(fontSize = 12.sp),
                color = colors.onBackgroundMuted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun PresetRow(preset: DnsPresets.Preset, selected: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    val (title, hint) = when (preset) {
        DnsPresets.Preset.Default -> R.string.geek_dns_default to R.string.geek_dns_default_hint
        DnsPresets.Preset.Cloudflare -> R.string.geek_dns_cloudflare to R.string.geek_dns_cloudflare_hint
        DnsPresets.Preset.Google -> R.string.geek_dns_google to R.string.geek_dns_google_hint
        DnsPresets.Preset.Quad9 -> R.string.geek_dns_quad9 to R.string.geek_dns_quad9_hint
        DnsPresets.Preset.AdGuard -> R.string.geek_dns_adguard to R.string.geek_dns_adguard_hint
        DnsPresets.Preset.Family -> R.string.geek_dns_family to R.string.geek_dns_family_hint
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = Geek.type.row, color = colors.onGlass)
            Text(stringResource(hint), style = Geek.type.caption, color = colors.onGlassMuted, maxLines = 2)
        }
        if (selected) {
            Icon(GeekIcons.Check, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(20.dp))
        }
    }
}
