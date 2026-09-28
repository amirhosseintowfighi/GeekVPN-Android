package com.geekvpn.ui.speedtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.GeekGraph
import com.geekvpn.speedtest.CloudflareSpeedProbe
import com.geekvpn.speedtest.SpeedPhase
import com.geekvpn.speedtest.SpeedProbe
import com.geekvpn.speedtest.SpeedResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SpeedTestUiState(
    /** Measured through the VPN (connected) or the phone's own network. */
    val throughVpn: Boolean = false,
    val running: Boolean = false,
    val phase: SpeedPhase? = null,
    /** The speed so far in the running phase, for the gauge. */
    val liveMbps: Double = 0.0,
    val result: SpeedResult? = null,
    /** Finished with nothing measured: no network, or the endpoint is blocked. */
    val failed: Boolean = false,
)

/** "تست سرعت": ping, download and upload, through the VPN when it is on. */
class SpeedTestViewModel(
    private val probe: SpeedProbe,
    private val connected: () -> Boolean,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val state = MutableStateFlow(SpeedTestUiState(throughVpn = connected()))
    val uiState: StateFlow<SpeedTestUiState> = state.asStateFlow()
    private var job: Job? = null

    /** Starts a test, or stops the running one. */
    fun toggle() {
        if (job?.isActive == true) {
            job?.cancel()
            state.update { it.copy(running = false, phase = null, liveMbps = 0.0) }
            return
        }
        val vpn = connected()
        state.value = SpeedTestUiState(throughVpn = vpn, running = true, phase = SpeedPhase.Ping)
        job = viewModelScope.launch {
            val result = probe.run(vpn) { phase, mbps ->
                state.update { if (it.running) it.copy(phase = phase, liveMbps = mbps) else it }
            }
            val nothing = result.pingMs == null && result.downloadMbps == null && result.uploadMbps == null
            state.update { it.copy(running = false, phase = null, liveMbps = 0.0, result = result, failed = nothing) }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                SpeedTestViewModel(CloudflareSpeedProbe(), connected = { GeekGraph.connectionPrefs.connectedSince > 0 })
            }
        }
    }
}
