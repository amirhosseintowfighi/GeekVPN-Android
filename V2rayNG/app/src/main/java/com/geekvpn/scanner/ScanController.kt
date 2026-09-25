package com.geekvpn.scanner

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.geekvpn.GeekStorage
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    fun start(context: Context, guid: String, downloadTest: Boolean) {
        if (state.value.running) return
        val intent = Intent(context, ScanService::class.java)
            .setAction(ScanService.ACTION_START)
            .putExtra(ScanService.EXTRA_GUID, guid)
            .putExtra(ScanService.EXTRA_DOWNLOAD, downloadTest)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        if (!state.value.running) return
        context.startService(Intent(context, ScanService::class.java).setAction(ScanService.ACTION_STOP))
    }

    internal fun onStarted(guid: String, target: CdnTarget, network: NetworkIdentity) {
        state.update { ScanState(running = true, guid = guid, target = target, network = network, applied = it.applied, appliedVersion = it.appliedVersion) }
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
