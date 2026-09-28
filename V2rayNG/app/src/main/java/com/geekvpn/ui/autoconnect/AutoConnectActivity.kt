package com.geekvpn.ui.autoconnect

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.geekvpn.autoconnect.AutoConnect
import com.geekvpn.connection.ConnectionPrefs
import com.geekvpn.scanner.NetworkIdentity
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.SectionLabel
import com.geekvpn.ui.common.SettingRow
import com.geekvpn.ui.common.SettingsCard
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AutoConnectUiState(
    val loaded: Boolean = false,
    val startOnBoot: Boolean = false,
    val autoOnWifi: Boolean = false,
    /** The key of the Wi-Fi the phone is on now; null on mobile data or offline. */
    val currentWifi: String? = null,
    val currentTrusted: Boolean = false,
    val trustedCount: Int = 0,
    /** False on API 31+ while battery optimisation is on: a Wi-Fi only brings a prompt. */
    val canStartInBackground: Boolean = true,
)

class AutoConnectViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(AutoConnectUiState())
    val uiState: StateFlow<AutoConnectUiState> = state.asStateFlow()
    private var loadJob: Job? = null

    /** Also on every return to the screen: the battery setting and the network change outside it. */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            state.value = withContext(Dispatchers.IO) { read() }
        }
    }

    fun setStartOnBoot(on: Boolean) = write { MmkvManager.encodeStartOnBoot(on) }

    fun setAutoOnWifi(on: Boolean) = write {
        ConnectionPrefs.open().autoOnWifi = on
        AutoConnect.sync(getApplication())
    }

    fun setCurrentTrusted(trusted: Boolean) {
        val key = state.value.currentWifi ?: return
        write { ConnectionPrefs.open().setTrusted(key, trusted) }
    }

    fun clearTrusted() = write { ConnectionPrefs.open().clearTrusted() }

    private fun write(change: () -> Unit) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            state.value = withContext(Dispatchers.IO) {
                change()
                read()
            }
        }
    }

    private fun read(): AutoConnectUiState {
        val app = getApplication<Application>()
        val prefs = ConnectionPrefs.open()
        val identity = NetworkIdentity.current(app)
        val wifi = identity.key.takeIf { identity.kind == NetworkIdentity.Kind.Wifi }
        val trusted = prefs.trustedNetworks
        return AutoConnectUiState(
            loaded = true,
            startOnBoot = MmkvManager.decodeStartOnBoot(),
            autoOnWifi = prefs.autoOnWifi,
            currentWifi = wifi,
            currentTrusted = wifi != null && wifi in trusted,
            trustedCount = trusted.size,
            canStartInBackground = AutoConnect.canStartInBackground(app),
        )
    }
}

/**
 * "اتصال خودکار و Kill Switch": start on boot (v2rayNG's own setting), start
 * on an untrusted Wi-Fi ([AutoConnect]), and how to turn on Android's
 * always-on VPN with "block connections without VPN", which is the kill
 * switch; an app cannot turn that on by itself.
 */
class AutoConnectActivity : BaseComponentActivity() {
    private val viewModel: AutoConnectViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.load()
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.load()
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeekBackdrop {
                AutoConnectScreen(
                    state = state,
                    actions = object : AutoConnectActions {
                        override fun onBack() = finish()
                        override fun onStartOnBoot(on: Boolean) = viewModel.setStartOnBoot(on)
                        override fun onAutoOnWifi(on: Boolean) = viewModel.setAutoOnWifi(on)
                        override fun onTrustCurrent(trusted: Boolean) = viewModel.setCurrentTrusted(trusted)
                        override fun onClearTrusted() = viewModel.clearTrusted()
                        override fun onBatterySettings() = openSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        override fun onVpnSettings() = openSettings(Settings.ACTION_VPN_SETTINGS)
                    },
                )
            }
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: ActivityNotFoundException) {
            LogUtil.e(AppConfig.TAG, "AutoConnect: no settings page for $action", e)
            Toast.makeText(this, R.string.toast_system_vpn_settings_unavailable, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            LogUtil.e(AppConfig.TAG, "AutoConnect: settings page $action refused", e)
            Toast.makeText(this, R.string.toast_system_vpn_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}

interface AutoConnectActions {
    fun onBack()
    fun onStartOnBoot(on: Boolean)
    fun onAutoOnWifi(on: Boolean)
    fun onTrustCurrent(trusted: Boolean)
    fun onClearTrusted()
    fun onBatterySettings()
    fun onVpnSettings()
}

@Composable
fun AutoConnectScreen(state: AutoConnectUiState, actions: AutoConnectActions) {
    val colors = Geek.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), actions::onBack)
            Text(stringResource(R.string.geek_auto_title), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel(stringResource(R.string.geek_auto_section_start))
            SettingsCard {
                SettingRow(
                    icon = GeekIcons.Bolt,
                    title = stringResource(R.string.geek_auto_boot),
                    hint = stringResource(R.string.geek_auto_boot_hint),
                    onClick = { actions.onStartOnBoot(!state.startOnBoot) },
                    role = Role.Switch,
                ) { GeekSwitch(checked = state.startOnBoot, onCheckedChange = null) }
                SettingsDivider()
                SettingRow(
                    icon = GeekIcons.Globe,
                    title = stringResource(R.string.geek_auto_wifi),
                    hint = stringResource(R.string.geek_auto_wifi_hint),
                    onClick = { actions.onAutoOnWifi(!state.autoOnWifi) },
                    role = Role.Switch,
                ) { GeekSwitch(checked = state.autoOnWifi, onCheckedChange = null) }
                if (state.autoOnWifi && state.currentWifi != null) {
                    SettingsDivider()
                    SettingRow(
                        icon = GeekIcons.Check,
                        title = stringResource(R.string.geek_auto_trust_current),
                        hint = stringResource(R.string.geek_auto_trust_current_hint),
                        onClick = { actions.onTrustCurrent(!state.currentTrusted) },
                        role = Role.Switch,
                    ) { GeekSwitch(checked = state.currentTrusted, onCheckedChange = null) }
                }
                if (state.autoOnWifi && state.trustedCount > 0) {
                    SettingsDivider()
                    SettingRow(
                        icon = GeekIcons.Close,
                        title = stringResource(R.string.geek_auto_trust_clear),
                        hint = stringResource(R.string.geek_auto_trust_count, state.trustedCount),
                        onClick = actions::onClearTrusted,
                    )
                }
            }
            if (state.autoOnWifi && state.currentWifi == null) {
                Note(stringResource(R.string.geek_auto_trust_offwifi))
            }
            if (state.autoOnWifi && !state.canStartInBackground) {
                Note(stringResource(R.string.geek_auto_battery_note))
                GeekSecondaryButton(
                    stringResource(R.string.geek_auto_battery_open),
                    actions::onBatterySettings,
                    Modifier.fillMaxWidth(),
                    icon = GeekIcons.Sliders,
                )
            }

            SectionLabel(stringResource(R.string.geek_auto_section_kill))
            GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.geek_auto_kill_intro), style = Geek.type.body, color = colors.onGlass)
                    listOf(R.string.geek_auto_kill_step1, R.string.geek_auto_kill_step2, R.string.geek_auto_kill_step3).forEach {
                        Text(stringResource(it), style = Geek.type.caption, color = colors.onGlassMuted)
                    }
                    Text(stringResource(R.string.geek_auto_kill_warning), style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.onGlassMuted)
                    GeekSecondaryButton(
                        stringResource(R.string.geek_auto_kill_open),
                        actions::onVpnSettings,
                        Modifier.fillMaxWidth(),
                        icon = GeekIcons.Shield,
                    )
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = Geek.type.caption.copy(fontSize = 12.sp),
        color = Geek.colors.onBackgroundMuted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}
