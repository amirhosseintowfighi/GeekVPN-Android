package com.geekvpn.smartconnect

import android.app.Service
import android.os.PowerManager
import com.geekvpn.connection.ConnectionPrefs
import com.geekvpn.scanner.CleanIps
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.service.RealPingWorkerService
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Failover while connected (spec §3.5): runs in the VPN process for exactly
 * as long as the core does, checks the running connection's real delay, and
 * when it turns bad (see [FailoverPolicy]) retests the service's configs in
 * the background with v2rayNG's real-delay test and moves to the best one.
 *
 * Lives in the VPN process because the app's own process may be gone while
 * the VPN runs. Owned by `CoreServiceManager`, which starts it after the core
 * starts and stops it before the core stops; [probe] and [reload] are the
 * core's own delay check and in-place reload.
 */
class FailoverMonitor(
    private val service: Service,
    /** Delay through the running core in milliseconds, < 0 failed; null when the core is busy. */
    private val probe: () -> Long?,
    /** Rebuild the core from the selected server, keeping the tunnel up. */
    private val reload: () -> Boolean,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("geek-failover"))
    private val prefs = ConnectionPrefs.open()

    fun start() {
        scope.launch { watch() }
    }

    fun stop() {
        scope.cancel()
    }

    private suspend fun watch() {
        var threshold = FailoverThreshold.of(null)
        var policy = FailoverPolicy(threshold)
        delay(FIRST_CHECK_MS)
        while (scope.isActive) {
            val wanted = if (prefs.autoServer) prefs.failoverThreshold else FailoverThreshold.Off
            if (wanted != threshold) {
                threshold = wanted
                policy = FailoverPolicy(threshold)
            }
            if (threshold != FailoverThreshold.Off) {
                val delayMs = check()
                if (delayMs != null && policy.onCheck(delayMs, System.currentTimeMillis()) == FailoverPolicy.Action.Failover) {
                    failover()
                    policy.onFailover(System.currentTimeMillis())
                }
            }
            delay(if (interactive()) CHECK_INTERVAL_MS else CHECK_INTERVAL_SCREEN_OFF_MS)
        }
    }

    private fun check(): Long? = try {
        probe()
    } catch (e: Exception) {
        // libv2ray reports a failed request as an exception.
        LogUtil.d(AppConfig.TAG, "Failover: delay check failed: ${e.message}")
        -1L
    }

    private suspend fun failover() {
        val current = MmkvManager.getSelectServer() ?: return
        val groupId = MmkvManager.decodeServerConfig(current)?.subscriptionId ?: return
        LogUtil.i(AppConfig.TAG, "Failover: connection bad, retesting group $groupId")
        val ranked = SmartConnectUseCase(ports).rank(ports.servers(groupId))
        val best = ranked.firstOrNull()
        if (best == null) {
            // Nothing answers: the phone's own network is the likely problem; stay put.
            LogUtil.i(AppConfig.TAG, "Failover: no config answered, keeping $current")
            return
        }
        best.choice.ip?.let { CleanIps.use(service, best.choice.guid, it, best.delayMs) }
        MmkvManager.setSelectServer(best.choice.guid)
        if (reload()) {
            LogUtil.i(AppConfig.TAG, "Failover: moved from $current to ${best.choice.guid} (${best.delayMs} ms)")
            // The app's screens refresh the selected server on this.
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_RUNNING, "")
        }
    }

    private val ports = object : SmartConnectPorts {
        override fun servers(groupId: String): List<String> = MmkvManager.decodeServerList(groupId)

        // The account tier is not known in this process; a config the scanner
        // ran for on this network is one it applies to.
        override fun scannable(guid: String): Boolean = CleanIps.scanned(service, guid)

        override fun freshIps(guid: String): List<String> = CleanIps.fresh(service, guid)

        // No scanning in the background: it costs data and is the customer's call.
        override suspend fun quickScan(guid: String): List<String> = emptyList()

        override fun useIp(guid: String, ip: String, delayMs: Long) = CleanIps.use(service, guid, ip, delayMs)

        override suspend fun measure(guids: List<String>): Map<String, Long> {
            val results = ConcurrentHashMap<String, Long>()
            val done = CompletableDeferred<Unit>()
            val worker = RealPingWorkerService(service, guids) { event ->
                when (event) {
                    is RealPingEvent.Result -> results[event.guid] = event.delayMillis
                    is RealPingEvent.Finish -> done.complete(Unit)
                    is RealPingEvent.Progress -> Unit
                }
            }
            worker.start()
            try {
                withTimeoutOrNull(TEST_TIMEOUT_MS) { done.await() }
            } catch (e: CancellationException) {
                worker.cancel()
                throw e
            }
            worker.cancel()
            return results
        }

        // Not used by rank(); failover() connects itself.
        override suspend fun connect(guid: String): Boolean = false
    }

    private fun interactive(): Boolean = service.getSystemService(PowerManager::class.java)?.isInteractive != false

    private companion object {
        /** Let a fresh connection settle before judging it. */
        const val FIRST_CHECK_MS = 20_000L
        const val CHECK_INTERVAL_MS = 30_000L

        /** Fewer checks with the screen off: Doze stretches them anyway, and they cost battery. */
        const val CHECK_INTERVAL_SCREEN_OFF_MS = 120_000L
        const val TEST_TIMEOUT_MS = 45_000L
    }
}
