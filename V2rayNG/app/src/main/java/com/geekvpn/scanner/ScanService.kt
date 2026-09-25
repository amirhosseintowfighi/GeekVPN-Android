package com.geekvpn.scanner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A short foreground service for one clean-IP scan, so a scan the customer
 * started keeps going when they switch apps and can be stopped from its
 * notification. It lives only as long as the scan: at most [ScanConfig.MAX_IPS]
 * addresses, stopping early at [ScanConfig.STOP_AFTER] clean ones.
 *
 * The scanner's sockets need no `VpnService.protect`: v2rayNG's VPN always
 * leaves this app's own package out of the tunnel (`CoreVpnService`), so they
 * go straight out on the phone's network, which is the network being scanned.
 */
class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                if (CfScanNative.isRunning()) {
                    // OnFinish follows from Go and stops the service.
                    CfScanNative.stop()
                } else {
                    stopSelf()
                }
            }
            ACTION_START -> {
                goForeground(0, 0)
                val guid = intent.getStringExtra(EXTRA_GUID)
                val download = intent.getBooleanExtra(EXTRA_DOWNLOAD, false)
                val timeoutMs = intent.getLongExtra(EXTRA_TIMEOUT_MS, 0L)
                if (guid == null || CfScanNative.isRunning()) {
                    if (!CfScanNative.isRunning()) stopSelf()
                } else {
                    scope.launch { begin(guid, download, timeoutMs) }
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(guid: String, download: Boolean, timeoutMs: Long) {
        val profile = MmkvManager.decodeServerConfig(guid)
        val target = profile?.let { CdnTarget.of(it) }
        // Checked before scanning: /cdn-cgi/trace answers on any Cloudflare
        // address whatever the domain, so the scan itself cannot tell.
        if (profile == null || target == null || CleanIps.verify(this, guid) == false) {
            finish(getString(R.string.geek_scan_err_not_cdn), null)
            return
        }
        val network = NetworkIdentity.current(this)
        val store = ScanController.store
        val known = store.results(target, network.key)?.results.orEmpty().map { it.ip }
        val ranges = try {
            assets.open(RANGES_ASSET).bufferedReader().use { it.readText() }
        } catch (e: java.io.IOException) {
            LogUtil.e(AppConfig.TAG, "Scan: bundled ranges missing", e)
            finish(getString(R.string.geek_scan_err_generic), null)
            return
        }
        ScanController.onStarted(guid, target, network)
        val found = mutableListOf<CleanIp>()
        try {
            CfScanNative.start(
                ScanConfig.json(target, known, download),
                ranges,
                object : CfScanNative.Events {
                    override fun onResult(json: String) {
                        val ip = try {
                            gson.fromJson(json, CleanIp::class.java)
                        } catch (e: JsonParseException) {
                            LogUtil.w(AppConfig.TAG, "Scan: unreadable result", e)
                            null
                        } ?: return
                        synchronized(found) { found += ip }
                        ScanController.onResult(ip)
                    }

                    override fun onProgress(tested: Long, total: Long, found: Long) {
                        ScanController.onProgress(tested, total)
                        // A notification per address would be throttled by the system anyway.
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastNotified > NOTIFY_INTERVAL_MS || tested == total) {
                            lastNotified = now
                            goForeground(tested, total)
                        }
                    }

                    override fun onFinish(error: String?) {
                        val results = synchronized(found) { found.toList() }
                        val applied = if (results.isNotEmpty()) save(guid, profile, target, network, results) else null
                        finish(error?.let { getString(R.string.geek_scan_err_generic) }, applied)
                        if (error != null) LogUtil.w(AppConfig.TAG, "Scan: finished with error: $error")
                    }
                },
            )
            if (timeoutMs > 0) {
                // Smart connect's short scan: stop where it is and keep what it found.
                scope.launch {
                    delay(timeoutMs)
                    if (CfScanNative.isRunning()) CfScanNative.stop()
                }
            }
        } catch (e: Exception) {
            // gomobile surfaces Go errors as Exception: bad config or a scan already running.
            LogUtil.e(AppConfig.TAG, "Scan: could not start", e)
            finish(getString(R.string.geek_scan_err_generic), null)
        }
    }

    /** Keeps the best addresses for this network and puts the best one in place. */
    private fun save(
        guid: String,
        profile: ProfileItem,
        target: CdnTarget,
        network: NetworkIdentity,
        found: List<CleanIp>,
    ): CleanIp? {
        val store = ScanController.store
        val now = System.currentTimeMillis()
        val record = store.saveResults(target, network.key, found, now)
        val best = record.results.firstOrNull() ?: return null
        // The customer ran this scan; where DNS gave no verdict, take the domain as Cloudflare's.
        CleanIps.trustAfterScan(guid)
        store.setOverride(ProfileKey.of(profile), network.key, IpOverride(best.ip, now, best.latencyMs))
        return best
    }

    private fun finish(error: String?, applied: CleanIp?) {
        ScanController.onFinished(error, applied)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(tested: Long, total: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager?.getNotificationChannel(CHANNEL) == null) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.geek_scan_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, ScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.geek_scan_notification))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total.toInt(), tested.toInt(), total == 0L)
            .addAction(0, getString(R.string.geek_scan_stop), stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        if (CfScanNative.isRunning()) CfScanNative.stop()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.geekvpn.scanner.START"
        const val ACTION_STOP = "com.geekvpn.scanner.STOP"
        const val EXTRA_GUID = "guid"
        const val EXTRA_DOWNLOAD = "download"

        /** Stop the scan after this long (smart connect); 0 runs it to the end. */
        const val EXTRA_TIMEOUT_MS = "timeout_ms"

        /** cf-scanner's Cloudflare IPv4 list (MIT), shipped with the app. */
        const val RANGES_ASSET = "cfscan/ipv4.txt"
        private const val CHANNEL = "geek_scan"
        private const val NOTIFICATION_ID = 4107
        private const val NOTIFY_INTERVAL_MS = 1_000L
    }
}
