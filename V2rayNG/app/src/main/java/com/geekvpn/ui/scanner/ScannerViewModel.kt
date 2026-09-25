package com.geekvpn.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geekvpn.scanner.CleanIp
import com.geekvpn.scanner.CleanIpTarget
import com.geekvpn.scanner.IpOverride
import com.geekvpn.scanner.NetworkIdentity
import com.geekvpn.scanner.ProfileKey
import com.geekvpn.scanner.ScanController
import com.geekvpn.scanner.ScanRecord
import com.geekvpn.scanner.ScanState
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScannerUiState(
    val target: CleanIpTarget? = null,
    val network: NetworkIdentity? = null,
    /** What the last finished scan on this network kept. */
    val record: ScanRecord? = null,
    /** The address in use on this network instead of the config's own. */
    val override: IpOverride? = null,
    val scan: ScanState = ScanState(),
    val downloadTest: Boolean = false,
) {
    /** The live scan's results while it is for this config, else the kept ones. */
    val shown: List<CleanIp>
        get() = if (scan.guid == target?.guid && (scan.running || scan.results.isNotEmpty())) scan.results else record?.results.orEmpty()

    val running: Boolean get() = scan.running && scan.guid == target?.guid
}

sealed interface ScannerEvent {
    /** The address the connection uses changed; reconnect if connected. */
    data object AddressChanged : ScannerEvent
}

/**
 * The scanner screen for one config: its kept results and override on the
 * current network, and the scan `ScanController` runs.
 */
class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val store get() = ScanController.store

    private val state = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = state.asStateFlow()

    private val eventChannel = Channel<ScannerEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            var seenVersion = ScanController.scan.value.appliedVersion
            ScanController.scan.collect { scan ->
                state.update { it.copy(scan = scan) }
                if (scan.appliedVersion != seenVersion) {
                    seenVersion = scan.appliedVersion
                    if (scan.guid == state.value.target?.guid) {
                        reload()
                        eventChannel.send(ScannerEvent.AddressChanged)
                    }
                }
            }
        }
    }

    fun open(target: CleanIpTarget) {
        state.update { it.copy(target = target) }
        viewModelScope.launch { reload() }
    }

    fun start() {
        val target = state.value.target ?: return
        ScanController.start(app, target.guid, state.value.downloadTest)
    }

    fun stop() = ScanController.stop(app)

    fun setDownloadTest(enabled: Boolean) {
        store.downloadTest = enabled
        state.update { it.copy(downloadTest = enabled) }
    }

    /** A result tapped: use it on this network from now on. */
    fun use(ip: CleanIp) {
        val current = state.value
        val network = current.network ?: return
        viewModelScope.launch {
            val key = profileKey() ?: return@launch
            withContext(Dispatchers.IO) {
                store.setOverride(key, network.key, IpOverride(ip.ip, System.currentTimeMillis(), ip.latencyMs))
            }
            reload()
            eventChannel.send(ScannerEvent.AddressChanged)
        }
    }

    /** "بازگشت به IP اصلی". */
    fun revert() {
        val network = state.value.network ?: return
        viewModelScope.launch {
            val key = profileKey() ?: return@launch
            withContext(Dispatchers.IO) { store.clearOverride(key, network.key) }
            reload()
            eventChannel.send(ScannerEvent.AddressChanged)
        }
    }

    private suspend fun profileKey(): String? {
        val guid = state.value.target?.guid ?: return null
        return withContext(Dispatchers.IO) { MmkvManager.decodeServerConfig(guid)?.let { ProfileKey.of(it) } }
    }

    private suspend fun reload() {
        val target = state.value.target ?: return
        val loaded = withContext(Dispatchers.IO) {
            val network = NetworkIdentity.current(app)
            val key = MmkvManager.decodeServerConfig(target.guid)?.let { ProfileKey.of(it) }
            Triple(network, store.results(target.target, network.key), key?.let { store.override(it, network.key) })
        }
        state.update {
            it.copy(network = loaded.first, record = loaded.second, override = loaded.third, downloadTest = store.downloadTest)
        }
    }
}
