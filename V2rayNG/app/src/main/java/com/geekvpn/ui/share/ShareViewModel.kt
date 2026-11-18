package com.geekvpn.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.GeekGraph
import com.geekvpn.share.LanAddress
import com.geekvpn.share.LanShare
import com.geekvpn.ui.advanced.MmkvSettingsStore
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShareUiState(
    val loaded: Boolean = false,
    val sharing: Boolean = false,
    val connected: Boolean = false,
    val port: Int = 0,
    /** The SOCKS user set in advanced settings; other devices need it and its password. */
    val user: String? = null,
    val addresses: List<LanAddress> = emptyList(),
)

/** "اشتراک VPN با دستگاه دیگر": v2rayNG's proxy-sharing setting, with the addresses to use. */
class ShareViewModel(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val state = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = state.asStateFlow()

    /** Reads everything again; on open and on return, since Wi-Fi or the hotspot may have changed. */
    fun refresh() {
        viewModelScope.launch {
            state.value = withContext(Dispatchers.IO) {
                val user = SettingsManager.getSocksUsername()?.takeIf { it.isNotBlank() && !SettingsManager.getSocksPassword().isNullOrBlank() }
                ShareUiState(
                    loaded = true,
                    sharing = MmkvSettingsStore.bool(AppConfig.PREF_PROXY_SHARING, false),
                    connected = GeekGraph.connectionPrefs.connectedSince > 0,
                    port = SettingsManager.getSocksPort(),
                    user = user,
                    addresses = LanShare.current(),
                )
            }
        }
    }

    /** Through the same store as advanced settings, so a running connection is rebuilt with it. */
    fun setSharing(on: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { MmkvSettingsStore.put(AppConfig.PREF_PROXY_SHARING, on) }
            refresh()
        }
    }

    companion object {
        fun factory() = viewModelFactory { initializer { ShareViewModel() } }
    }
}
