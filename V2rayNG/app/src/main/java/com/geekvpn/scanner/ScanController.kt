package com.geekvpn.scanner

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.geekvpn.GeekStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** One scan's progress, as the scanner screen shows it. */
data class ScanState(
    val running: Boolean = false,
    /** The config being scanned for, by v2rayNG GUID. */
    val guid: String? = null,
    val target: CdnTarget? = null,
    val network: NetworkIdentity? = null,
    val tested: Long = 0,
    val total: Long = 0,
    val results: List<CleanIp> = emptyList(),
    /** Why the last scan could not run; null when it did. */
    val error: String? = null,
    /** The address put in place by the last scan, for the connection to pick up. */
    val applied: CleanIp? = null,
    /** Bumped when [applied] changes, so the same address applied twice is still news. */
    val appliedVersion: Int = 0,
    /** Scans finished since the process started, for a caller waiting on one. */
    val finished: Int = 0,
)

/**
 * The scan in progress, for the whole app process. `ScanService` runs it and
 * writes here; the scanner screen reads here, so leaving the screen does not
 * lose the scan and coming back shows where it is.
 */
object ScanController {
    private val state = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = state.asStateFlow()

    val store: ScanStore by lazy { ScanStore(GeekStorage.open(IpOverrides.STORE_ID)) }

    fun start(context: Context, guid: String, downloadTest: Boolean, timeoutMs: Long = 0) {
        if (state.value.running) return
        val intent = Intent(context, ScanService::class.java)
            .setAction(ScanService.ACTION_START)
            .putExtra(ScanService.EXTRA_GUID, guid)
            .putExtra(ScanService.EXTRA_DOWNLOAD, downloadTest)
            .putExtra(ScanService.EXTRA_TIMEOUT_MS, timeoutMs)
        ContextCompat.startForegroundService(context, intent)
    }

    /**
     * Smart connect's short scan (spec §3.6): at most [QUICK_SCAN_MS], then
     * whatever it found, best first. Waits for a scan the customer already
     * started instead of starting another. Cancelling the caller stops the scan.
     */
    suspend fun quickScan(context: Context, guid: String): List<String> {
        val before = state.value.finished
        if (!state.value.running) start(context, guid, downloadTest = false, timeoutMs = QUICK_SCAN_MS)
        try {
            // The service's own timer stops the scan; this bound only covers a service that never started.
            withTimeoutOrNull(QUICK_SCAN_MS + QUICK_SCAN_GRACE_MS) { state.first { it.finished > before } }
                ?: stop(context)
        } catch (e: CancellationException) {
            stop(context)
            throw e
        }
        return CleanIps.fresh(context, guid)
    }

    fun stop(context: Context) {
        if (!state.value.running) return
        context.startService(Intent(context, ScanService::class.java).setAction(ScanService.ACTION_STOP))
    }

    const val QUICK_SCAN_MS = 20_000L
    private const val QUICK_SCAN_GRACE_MS = 10_000L

    internal fun onStarted(guid: String, target: CdnTarget, network: NetworkIdentity) {
        state.update {
            ScanState(
                running = true,
                guid = guid,
                target = target,
                network = network,
                applied = it.applied,
                appliedVersion = it.appliedVersion,
                finished = it.finished,
            )
        }
    }

    internal fun onResult(ip: CleanIp) {
        state.update { it.copy(results = ScanStore.rank(it.results + ip)) }
    }

    internal fun onProgress(tested: Long, total: Long) {
        state.update { it.copy(tested = tested, total = total) }
    }

    internal fun onFinished(error: String?, applied: CleanIp?) {
        state.update {
            it.copy(
                running = false,
                error = error,
                applied = applied ?: it.applied,
                appliedVersion = if (applied != null) it.appliedVersion + 1 else it.appliedVersion,
                finished = it.finished + 1,
            )
        }
    }
}

/** The JSON the Go scanner takes (cfscan's config), from what the app knows. */
object ScanConfig {
    const val MAX_IPS = 300
    const val STOP_AFTER = 5

    fun json(target: CdnTarget, preferIps: List<String>, downloadTest: Boolean, gson: Gson = Gson()): String {
        val root = JsonObject()
        root.addProperty("sni", target.sni)
        root.addProperty("host", target.host)
        root.addProperty("port", target.port)
        root.addProperty("maxIps", MAX_IPS)
        root.addProperty("stopAfter", STOP_AFTER)
        root.addProperty("fingerprint", "chrome")
        root.add("preferIps", gson.toJsonTree(preferIps))
        root.add(
            "jitter",
            JsonObject().apply {
                addProperty("enable", true)
                addProperty("maxMs", 50)
                addProperty("samples", 5)
                addProperty("intervalMs", 200)
            },
        )
        root.add("download", JsonObject().apply { addProperty("enable", downloadTest) })
        return gson.toJson(root)
    }
}
