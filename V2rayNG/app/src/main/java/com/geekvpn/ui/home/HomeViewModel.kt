package com.geekvpn.ui.home

import android.app.Application
import android.net.TrafficStats
import android.os.Process
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geekvpn.GeekGraph
import com.geekvpn.account.SubscriptionPlan
import com.geekvpn.connection.ConnectionLogic
import com.geekvpn.connection.ConnectionPhase
import com.geekvpn.connection.ExitIp
import com.geekvpn.connection.ExitIpLookup
import com.geekvpn.connection.RouteMode
import com.geekvpn.connection.ServerNames
import com.geekvpn.connection.ServiceSignal
import com.geekvpn.connection.ServiceStatus
import com.geekvpn.connection.TrafficMeter
import com.geekvpn.scanner.CleanIpTarget
import com.geekvpn.scanner.CleanIps
import com.geekvpn.scanner.ScanController
import com.geekvpn.smartconnect.FailoverThreshold
import com.geekvpn.smartconnect.SmartConnectPorts
import com.geekvpn.smartconnect.SmartConnectUseCase
import com.geekvpn.smartconnect.SmartResult
import com.geekvpn.smartconnect.SmartStage
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.TestServiceMessage
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.main.MainRepository
import com.v2ray.ang.ui.main.MainServiceEvent
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID

/** One config of the active service, as Servers.html lists it. */
data class ServerRow(
    val guid: String,
    val title: String,
    val countryCode: String?,
    /** > 0 milliseconds, 0 untested, < 0 failed. */
    val delayMs: Long,
)

/** A subscription the user added by hand (Services.html, "لینک‌های دستی"). */
data class ManualGroup(
    val guid: String,
    /** Null for v2rayNG's default group, which holds configs imported one by one. */
    val name: String?,
    val servers: Int,
)

data class HomeUiState(
    val phase: ConnectionPhase = ConnectionPhase.Off,
    /** Wall-clock millis the connection started; 0 when unknown or off. */
    val connectedSince: Long = 0,
    val services: List<ServiceStatus> = emptyList(),
    /** The account service the selected server belongs to; null for manual links. */
    val activeService: ServiceStatus? = null,
    /** v2rayNG subscription GUID of the server list shown. */
    val groupId: String? = null,
    val servers: List<ServerRow> = emptyList(),
    val selected: ServerRow? = null,
    val autoServer: Boolean = true,
    val route: RouteMode = RouteMode.Smart,
    val balance: Long? = null,
    val exitIp: ExitIp? = null,
    val exitIpLoading: Boolean = false,
    val speed: TrafficMeter.Speed? = null,
    val testing: Boolean = false,
    val manualGroups: List<ManualGroup> = emptyList(),
    /** An account sync or import is running. */
    val updating: Boolean = false,
    /** The selected config, when the clean-IP scanner applies to it. */
    val cleanIp: CleanIpTarget? = null,
    /** Smart connect's step, while it runs; null otherwise. */
    val stage: SmartStage? = null,
    val failover: FailoverThreshold = FailoverThreshold.DEFAULT,
)

sealed interface HomeEvent {
    data class Message(@param:StringRes val text: Int) : HomeEvent

    /** The daemon's own words, when it gave a reason for failing to start. */
    data class Text(val text: String) : HomeEvent

    /** Smart connect gave up after [attempts] servers. */
    data class Failed(val attempts: Int) : HomeEvent
}

/**
 * The connection, the active service and its servers, for the Home and
 * Servers screens. Starts and stops go through v2rayNG's `LauncherManager`
 * and the daemon's broadcasts arrive through its `MainRepository`, so the
 * VPN service itself is untouched.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AngApplication
    private val repository = MainRepository(app)
    private val prefs = GeekGraph.connectionPrefs
    private val store = GeekGraph.accountStore

    private val state = MutableStateFlow(
        HomeUiState(
            autoServer = prefs.autoServer,
            route = prefs.routeMode ?: RouteMode.Smart,
            connectedSince = prefs.connectedSince,
            failover = prefs.failoverThreshold,
        )
    )
    val uiState: StateFlow<HomeUiState> = state.asStateFlow()

    private val eventChannel = Channel<HomeEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val meter = TrafficMeter()
    private var foreground = false
    private var speedJob: Job? = null
    private var exitIpJob: Job? = null
    private var exitIpCheckedAt = 0L
    private var testJob: Job? = null
    private var pendingTest: Pair<String, CompletableDeferred<Unit>>? = null
    private var smartJob: Job? = null

    /**
     * What v2rayNG's own main screen does on start and GeekVPN must do in its
     * place: copy the geosite/geoip files the routing rules need (without them
     * Xray fails to build "geosite:ir" and refuses to start) and schedule the
     * periodic subscription refresh. Every connect waits for it.
     */
    private val prepared: Job = viewModelScope.launch(Dispatchers.IO) {
        repository.initAssets()
        repository.syncSubscriptions()
    }

    /** While smart connect runs, the daemon's start/stop reports go to it instead of the phase. */
    private var smartSignals: Channel<ServiceSignal>? = null
    private var pendingProbe: Pair<String, CompletableDeferred<Long>>? = null

    init {
        viewModelScope.launch { repository.mainServiceEvent.collect { onServiceEvent(it) } }
        viewModelScope.launch {
            combine(store.services, store.balance) { services, balance -> services to balance }
                .collect { (_, balance) ->
                    state.update { it.copy(balance = balance) }
                    reloadServers()
                }
        }
        if (prefs.routeMode == null) {
            // First run: Route.html's default. Also replaces whatever preset
            // v2rayNG picked from the phone's locale.
            viewModelScope.launch(Dispatchers.IO) {
                RouteMode.Smart.apply(app)
                prefs.routeMode = RouteMode.Smart
            }
        }
    }

    // -- screen lifecycle ----------------------------------------------------

    /** Called from the activity's onStart/onStop: speed sampling and IP checks run only while seen. */
    fun onForeground(visible: Boolean) {
        foreground = visible
        if (visible) {
            reloadServers()
            updateSpeedSampler()
            if (System.currentTimeMillis() - exitIpCheckedAt > EXIT_IP_MAX_AGE_MS) refreshExitIp()
        } else {
            speedJob?.cancel()
            speedJob = null
        }
    }

    // -- actions -------------------------------------------------------------

    /** The glasses button, after the activity has the VPN permission. */
    fun connect() {
        val current = state.value
        if (current.phase != ConnectionPhase.Off) return
        val groupId = current.groupId
        if (groupId == null || current.servers.isEmpty()) {
            eventChannel.trySend(HomeEvent.Message(R.string.geek_home_err_no_service))
            return
        }
        if (current.activeService?.active == false) {
            eventChannel.trySend(HomeEvent.Message(R.string.geek_home_err_service_inactive))
            return
        }
        if (!current.autoServer) {
            // The customer's own pick: connect with it as it stands.
            state.update { it.copy(phase = ConnectionPhase.Connecting) }
            viewModelScope.launch {
                prepared.join()
                LauncherManager.startService(app)
            }
            return
        }
        smartJob = viewModelScope.launch {
            prepared.join()
            smartConnect(groupId, current)
        }
    }

    /**
     * Spec §3.6 through [SmartConnectUseCase]: clean addresses, the delay
     * test, then up to three connection attempts, each shown on Home.
     */
    private suspend fun smartConnect(groupId: String, current: HomeUiState) {
        val signals = Channel<ServiceSignal>(Channel.UNLIMITED)
        smartSignals = signals
        val ports = HomePorts(signals, current)
        state.update { it.copy(phase = ConnectionPhase.Testing, stage = SmartStage.Testing) }
        try {
            val result = SmartConnectUseCase(ports).run(groupId, current.selected?.guid) { stage ->
                val phase = if (stage is SmartStage.Connecting) ConnectionPhase.Connecting else ConnectionPhase.Testing
                state.update { it.copy(phase = phase, stage = stage) }
            }
            smartSignals = null
            ports.keepBestDelays()
            when (result) {
                is SmartResult.Connected -> {
                    if (result.untested) eventChannel.send(HomeEvent.Message(R.string.geek_home_err_no_server_answered))
                    val since = System.currentTimeMillis()
                    prefs.connectedSince = since
                    state.update { it.copy(phase = ConnectionPhase.On, stage = null, connectedSince = since) }
                    onConnectionChanged()
                }
                SmartResult.NoServers -> {
                    state.update { it.copy(phase = ConnectionPhase.Off, stage = null) }
                    eventChannel.send(HomeEvent.Message(R.string.geek_home_err_no_service))
                }
                is SmartResult.Failed -> {
                    stopAfterSmart(ports.started)
                    eventChannel.send(HomeEvent.Failed(result.attempts))
                }
            }
            reloadServers()
        } catch (e: CancellationException) {
            smartSignals = null
            repository.cancelAllPing()
            stopAfterSmart(ports.started)
            throw e
        } finally {
            smartSignals = null
            pendingProbe = null
        }
    }

    private fun stopAfterSmart(started: Boolean) {
        if (!started) {
            state.update { it.copy(phase = ConnectionPhase.Off, stage = null) }
            return
        }
        state.update { it.copy(phase = ConnectionPhase.Stopping, stage = null) }
        LauncherManager.stopService(app)
        // The last attempt may have left no daemon to answer the stop. Then
        // settle on Off and ask; a daemon that is still up answers "running".
        viewModelScope.launch {
            delay(STOP_SETTLE_MS)
            if (state.value.phase == ConnectionPhase.Stopping) {
                state.update { it.copy(phase = ConnectionPhase.Off) }
                repository.sendMsg2Service(AppConfig.MSG_REGISTER_CLIENT, "")
            }
        }
    }

    fun disconnect() {
        when (state.value.phase) {
            ConnectionPhase.Testing, ConnectionPhase.Connecting -> {
                val smart = smartJob
                when {
                    smart?.isActive == true -> smart.cancel()
                    state.value.phase == ConnectionPhase.Testing -> cancelTest()
                    else -> {
                        state.update { it.copy(phase = ConnectionPhase.Stopping) }
                        LauncherManager.stopService(app)
                    }
                }
            }
            ConnectionPhase.On -> {
                state.update { it.copy(phase = ConnectionPhase.Stopping) }
                LauncherManager.stopService(app)
            }
            ConnectionPhase.Off, ConnectionPhase.Stopping -> Unit
        }
    }

    fun setFailover(threshold: FailoverThreshold) {
        prefs.failoverThreshold = threshold
        state.update { it.copy(failover = threshold) }
    }

    fun setAutoServer(enabled: Boolean) {
        prefs.autoServer = enabled
        state.update { it.copy(autoServer = enabled) }
    }

    fun selectServer(guid: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { MmkvManager.setSelectServer(guid) }
            reloadServers()
            restartIfRunning()
        }
    }

    /** Make an account service the one Home connects with. */
    fun selectService(subscriptionId: String) = selectGroup(SubscriptionPlan.guidOf(subscriptionId))

    /** Make a v2rayNG subscription the active one: its fastest known server, else its first. */
    fun selectGroup(groupId: String) {
        viewModelScope.launch {
            val chosen = withContext(Dispatchers.IO) {
                val guids = MmkvManager.decodeServerList(groupId)
                val delays = guids.map { ConnectionLogic.Delay(it, delayOf(it)) }
                (ConnectionLogic.best(delays) ?: guids.firstOrNull())?.also {
                    MmkvManager.setSelectServer(it)
                    MmkvManager.encodeSettings(AppConfig.CACHE_SUBSCRIPTION_ID, groupId)
                }
            }
            if (chosen == null) {
                eventChannel.send(HomeEvent.Message(R.string.geek_home_err_no_servers_yet))
                return@launch
            }
            reloadServers()
            restartIfRunning()
        }
    }

    /**
     * Services' refresh: services from the server, then their servers.
     * [announce] false for a refresh the customer did not ask for (after a purchase).
     */
    fun refreshAccount(announce: Boolean = true) {
        if (state.value.updating || GeekGraph.session.session.value !is com.geekvpn.auth.Session.SignedIn) return
        viewModelScope.launch {
            state.update { it.copy(updating = true) }
            val message = try {
                GeekGraph.accountSync.sync()
                R.string.geek_services_synced
            } catch (e: java.io.IOException) {
                LogUtil.w(AppConfig.TAG, "Home: account refresh failed", e)
                R.string.geek_services_sync_failed
            }
            state.update { it.copy(updating = false) }
            reloadServers()
            if (announce || message == R.string.geek_services_sync_failed) eventChannel.send(HomeEvent.Message(message))
        }
    }

    /** Services' "import from clipboard": a config, a batch of them, or a subscription link. */
    fun importConfigs(text: String) {
        if (text.isBlank() || state.value.updating) {
            eventChannel.trySend(HomeEvent.Message(R.string.geek_services_import_failed))
            return
        }
        viewModelScope.launch {
            state.update { it.copy(updating = true) }
            val imported = withContext(Dispatchers.IO) {
                val (configs, subscriptions) = AngConfigManager.importBatchConfig(text, AppConfig.DEFAULT_SUBSCRIPTION_ID, true)
                if (subscriptions > 0) {
                    // A new subscription link: fetch its servers now, like any manual subscription.
                    MmkvManager.decodeSubscriptions()
                        .filter { !SubscriptionPlan.isAccountGuid(it.guid) && it.subscription.lastUpdated <= 0 }
                        .forEach { AngConfigManager.updateConfigViaSub(it) }
                }
                configs + subscriptions
            }
            state.update { it.copy(updating = false) }
            reloadServers()
            eventChannel.send(
                HomeEvent.Message(if (imported > 0) R.string.geek_services_import_done else R.string.geek_services_import_failed)
            )
        }
    }

    /** Real-delay test of every server of the active service (Servers' refresh). */
    fun testServers() {
        val groupId = state.value.groupId ?: return
        if (state.value.testing) return
        testJob = viewModelScope.launch { runTest(groupId) }
    }

    fun setRoute(mode: RouteMode) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mode.apply(app)
                prefs.routeMode = mode
            }
            state.update { it.copy(route = mode) }
            restartIfRunning()
        }
    }

    fun refreshExitIp() {
        exitIpJob?.cancel()
        exitIpJob = viewModelScope.launch {
            state.update { it.copy(exitIpLoading = true) }
            // Give a fresh core a moment before sending the probe through it.
            if (state.value.phase == ConnectionPhase.On) delay(1_500)
            val result = ExitIpLookup.lookup(throughProxy = state.value.phase == ConnectionPhase.On)
            exitIpCheckedAt = System.currentTimeMillis()
            state.update { it.copy(exitIp = result, exitIpLoading = false) }
        }
    }

    // -- daemon events ---------------------------------------------------------

    private fun onServiceEvent(event: MainServiceEvent) {
        val smart = smartSignals
        if (smart != null) {
            val forwarded = when (event) {
                MainServiceEvent.StateStartSuccess -> ServiceSignal.StartSuccess
                is MainServiceEvent.StateStartFailure -> ServiceSignal.StartFailure
                MainServiceEvent.StateStopSuccess -> ServiceSignal.StopSuccess
                else -> null
            }
            if (forwarded != null) {
                smart.trySend(forwarded)
                return
            }
            if (event == MainServiceEvent.StateRunning || event == MainServiceEvent.StateNotRunning) return
        }
        val signal = when (event) {
            MainServiceEvent.StateRunning -> ServiceSignal.Running
            MainServiceEvent.StateNotRunning -> ServiceSignal.NotRunning
            MainServiceEvent.StateStartSuccess -> ServiceSignal.StartSuccess
            is MainServiceEvent.StateStartFailure -> {
                val text = event.message
                eventChannel.trySend(
                    if (text.isNullOrBlank()) HomeEvent.Message(R.string.geek_home_err_start) else HomeEvent.Text(text)
                )
                ServiceSignal.StartFailure
            }
            MainServiceEvent.StateStopSuccess -> ServiceSignal.StopSuccess
            is MainServiceEvent.MeasureConfigSuccess -> {
                val result = event.result
                state.update { current ->
                    current.copy(
                        servers = current.servers.map { if (it.guid == result.guid) it.copy(delayMs = result.delayMillis) else it },
                        selected = current.selected?.let { if (it.guid == result.guid) it.copy(delayMs = result.delayMillis) else it },
                    )
                }
                return
            }
            is MainServiceEvent.MeasureConfigFinish -> {
                finishTest(event.requestId)
                return
            }
            is MainServiceEvent.MeasureConfigCancelled -> {
                finishTest(event.requestId)
                return
            }
            is MainServiceEvent.MeasureDelayResult -> {
                pendingProbe?.takeIf { it.first == event.requestId }?.second?.complete(event.result.delayMillis)
                return
            }
            is MainServiceEvent.MeasureDelayCancelled -> {
                pendingProbe?.takeIf { it.first == event.requestId }?.second?.complete(-1L)
                return
            }
            else -> return
        }
        val before = state.value.phase
        val after = ConnectionLogic.next(before, signal)
        val since = when {
            after != ConnectionPhase.On -> 0L
            signal == ServiceSignal.StartSuccess || prefs.connectedSince == 0L -> System.currentTimeMillis()
            else -> prefs.connectedSince
        }
        prefs.connectedSince = since
        state.update { it.copy(phase = after, connectedSince = since) }
        if (before != after && (after == ConnectionPhase.On || after == ConnectionPhase.Off)) onConnectionChanged()
        // The VPN process's failover monitor reports a server switch as "running".
        if (signal == ServiceSignal.Running && before == ConnectionPhase.On) reloadServers()
    }

    private fun onConnectionChanged() {
        meter.reset()
        state.update { it.copy(speed = null) }
        updateSpeedSampler()
        refreshExitIp()
    }

    // -- internals ---------------------------------------------------------------

    /** Runs the daemon's real-delay test on [groupId] and returns the fastest server. */
    private suspend fun runTest(groupId: String): String? {
        val guids = state.value.servers.map { it.guid }
        val delays = measureDelays(guids, TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, subscriptionId = groupId))
        return ConnectionLogic.best(guids.map { ConnectionLogic.Delay(it, delays[it] ?: 0) })
    }

    /** v2rayNG's real-delay test through its test service; results by GUID, as it stored them. */
    private suspend fun measureDelays(guids: List<String>, message: TestServiceMessage): Map<String, Long> {
        val requestId = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        pendingTest = requestId to done
        state.update { current ->
            current.copy(testing = true, servers = current.servers.map { if (it.guid in guids) it.copy(delayMs = 0) else it })
        }
        try {
            withContext(Dispatchers.IO) { repository.clearAllTestDelayResults(guids) }
            repository.sendMsg2TestService(message, requestId)
            if (withTimeoutOrNull(TEST_TIMEOUT_MS) { done.await() } == null) {
                LogUtil.w(AppConfig.TAG, "Home: delay test of ${guids.size} servers timed out")
                repository.cancelAllPing()
            }
        } finally {
            pendingTest = null
            state.update { it.copy(testing = false) }
        }
        return withContext(Dispatchers.IO) { guids.associateWith { delayOf(it) } }
    }

    /** Smart connect on this screen: v2rayNG's test service, `LauncherManager` and the daemon's reports. */
    private inner class HomePorts(
        private val signals: Channel<ServiceSignal>,
        private val snapshot: HomeUiState,
    ) : SmartConnectPorts {
        /** A start was sent, so a failure or cancel must stop the daemon. */
        var started = false
            private set

        /** Each config's best delay over all rounds, for the list once the test is over. */
        private val bestSeen = mutableMapOf<String, Long>()

        override fun servers(groupId: String): List<String> = MmkvManager.decodeServerList(groupId)

        override suspend fun scannable(guid: String): Boolean = withContext(Dispatchers.IO) {
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@withContext false
            val isAccount = snapshot.groupId?.let { SubscriptionPlan.isAccountGuid(it) } == true
            CleanIpTarget.of(guid, "", profile, snapshot.activeService?.tier, isAccount) != null &&
                // Never scanned for automatically unless the domain is known to be Cloudflare's.
                CleanIps.verify(app, guid) == true
        }

        override fun freshIps(guid: String): List<String> = CleanIps.fresh(app, guid)

        override suspend fun quickScan(guid: String): List<String> = ScanController.quickScan(app, guid)

        override fun useIp(guid: String, ip: String?, delayMs: Long) = CleanIps.use(app, guid, ip, delayMs)

        override suspend fun measure(guids: List<String>): Map<String, Long> {
            val delays = measureDelays(guids, TestServiceMessage(key = AppConfig.MSG_MEASURE_CONFIG_START, serverGuids = guids))
            delays.forEach { (guid, delay) ->
                if (delay > 0 && (bestSeen[guid] ?: Long.MAX_VALUE) > delay) bestSeen[guid] = delay
            }
            return delays
        }

        override suspend fun connect(guid: String): Boolean {
            withContext(Dispatchers.IO) { MmkvManager.setSelectServer(guid) }
            reloadServers()
            while (signals.tryReceive().isSuccess) Unit // reports of an earlier attempt
            if (!started) {
                LauncherManager.startService(app)
            } else {
                // Restart when the daemon is up, start again when the last attempt left it stopped.
                LauncherManager.restartServiceOrStart(app) { LauncherManager.startService(app) }
            }
            started = true
            val up = withTimeoutOrNull(START_TIMEOUT_MS) {
                var result: Boolean? = null
                while (result == null) {
                    result = when (signals.receive()) {
                        ServiceSignal.StartSuccess -> true
                        ServiceSignal.StartFailure -> false
                        else -> null
                    }
                }
                result
            } ?: false
            if (!up) {
                LogUtil.w(AppConfig.TAG, "Home: smart connect could not start $guid")
                return false
            }
            val delay = probe()
            LogUtil.i(AppConfig.TAG, "Home: smart connect $guid carries traffic: ${delay > 0} ($delay ms)")
            if (delay > 0) {
                state.update { current ->
                    current.copy(servers = current.servers.map { if (it.guid == guid) it.copy(delayMs = delay) else it })
                }
            }
            return delay > 0
        }

        /** Keeps the list honest after rounds with different addresses: each config's best. */
        suspend fun keepBestDelays() {
            if (bestSeen.isEmpty()) return
            withContext(Dispatchers.IO) { bestSeen.forEach { (guid, delay) -> MmkvManager.encodeServerTestDelayMillis(guid, delay) } }
        }

        /** The daemon's own delay check through the running connection. */
        private suspend fun probe(): Long {
            val requestId = UUID.randomUUID().toString()
            val result = CompletableDeferred<Long>()
            pendingProbe = requestId to result
            repository.testCurrentServerRealPing(requestId)
            return try {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) { result.await() } ?: -1L
            } finally {
                pendingProbe = null
            }
        }
    }

    private fun finishTest(requestId: String) {
        val pending = pendingTest ?: return
        if (pending.first == requestId) pending.second.complete(Unit)
    }

    private fun cancelTest() {
        testJob?.cancel()
        repository.cancelAllPing()
        pendingTest = null
        state.update { it.copy(testing = false, phase = ConnectionPhase.Off) }
    }

    /** The config changed underneath the connection (a clean IP applied): reconnect to use it. */
    fun reconnectIfRunning() = restartIfRunning()

    private fun restartIfRunning() {
        if (state.value.phase == ConnectionPhase.On) LauncherManager.restartService(app)
    }

    private fun updateSpeedSampler() {
        val wanted = foreground && state.value.phase == ConnectionPhase.On
        if (!wanted) {
            speedJob?.cancel()
            speedJob = null
            return
        }
        if (speedJob?.isActive == true) return
        speedJob = viewModelScope.launch {
            val uid = Process.myUid()
            while (isActive) {
                val speed = meter.sample(
                    TrafficStats.getUidRxBytes(uid),
                    TrafficStats.getUidTxBytes(uid),
                    System.currentTimeMillis(),
                )
                if (speed != null) state.update { it.copy(speed = speed) }
                delay(SPEED_INTERVAL_MS)
            }
        }
    }

    private fun reloadServers() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { loadServers() }
            state.update {
                it.copy(
                    services = snapshot.services,
                    activeService = snapshot.active,
                    groupId = snapshot.groupId,
                    servers = snapshot.servers,
                    selected = snapshot.selected,
                    manualGroups = snapshot.manual,
                    cleanIp = snapshot.cleanIp,
                )
            }
        }
    }

    private data class Snapshot(
        val services: List<ServiceStatus>,
        val active: ServiceStatus?,
        val groupId: String?,
        val servers: List<ServerRow>,
        val selected: ServerRow?,
        val manual: List<ManualGroup>,
        val cleanIp: CleanIpTarget?,
    )

    private fun loadServers(): Snapshot {
        val now = Instant.now()
        val services = store.services.value.mapNotNull { card ->
            ServiceStatus.of(card, now, SubscriptionPlan.remarksOf(card))
        }
        val selectedGuid = MmkvManager.getSelectServer()
        val selectedGroup = selectedGuid?.let { MmkvManager.decodeServerConfig(it)?.subscriptionId }
        // The selected server's group, else the first active account service
        // that has servers, else any group with servers (manual links).
        val groupId = selectedGroup?.takeIf { MmkvManager.decodeServerList(it).isNotEmpty() }
            ?: services.filter { it.active }
                .map { SubscriptionPlan.guidOf(it.subscriptionId) }
                .firstOrNull { MmkvManager.decodeServerList(it).isNotEmpty() }
            ?: MmkvManager.decodeSubsList().firstOrNull { MmkvManager.decodeServerList(it).isNotEmpty() }
        val rows = groupId?.let { group ->
            MmkvManager.decodeServerList(group).mapNotNull { guid ->
                val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
                ServerRow(
                    guid = guid,
                    title = ServerNames.title(profile.remarks),
                    countryCode = ServerNames.countryCode(profile.remarks),
                    delayMs = delayOf(guid),
                )
            }
        }.orEmpty()
        var selected = rows.firstOrNull { it.guid == selectedGuid }
        if (selected == null && rows.isNotEmpty()) {
            // Nothing selected in this group yet: v2rayNG needs one to start.
            MmkvManager.setSelectServer(rows.first().guid)
            selected = rows.first()
        }
        val active = groupId?.let { group -> services.firstOrNull { SubscriptionPlan.guidOf(it.subscriptionId) == group } }
        val manual = MmkvManager.decodeSubscriptions()
            .filter { !SubscriptionPlan.isAccountGuid(it.guid) }
            .mapNotNull { sub ->
                val count = MmkvManager.decodeServerList(sub.guid).size
                if (count == 0 && sub.subscription.url.isBlank()) null
                else ManualGroup(sub.guid, sub.subscription.remarks.ifBlank { sub.guid.take(8) }, count)
            }
            .plus(
                MmkvManager.decodeServerList(AppConfig.DEFAULT_SUBSCRIPTION_ID).size
                    .takeIf { it > 0 && MmkvManager.decodeSubscriptions().none { sub -> sub.guid == AppConfig.DEFAULT_SUBSCRIPTION_ID } }
                    ?.let { listOf(ManualGroup(AppConfig.DEFAULT_SUBSCRIPTION_ID, null, it)) }
                    .orEmpty()
            )
        val cleanIp = selected?.let { row ->
            val profile = MmkvManager.decodeServerConfig(row.guid) ?: return@let null
            val isAccount = groupId?.let { SubscriptionPlan.isAccountGuid(it) } == true
            CleanIpTarget.of(row.guid, row.title, profile, active?.tier, isAccount)
                ?.takeIf { CleanIps.knownBehindCloudflare(row.guid) != false }
        }
        return Snapshot(services, active, groupId, rows, selected, manual, cleanIp)
    }

    private fun delayOf(guid: String): Long = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    private companion object {
        const val SPEED_INTERVAL_MS = 1_000L
        const val TEST_TIMEOUT_MS = 45_000L
        const val START_TIMEOUT_MS = 20_000L
        const val STOP_SETTLE_MS = 8_000L

        /** v2rayNG's check tries two URLs, then looks up the exit address. */
        const val PROBE_TIMEOUT_MS = 20_000L
        const val EXIT_IP_MAX_AGE_MS = 60_000L
    }
}
