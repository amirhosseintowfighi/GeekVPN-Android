package com.geekvpn.autoconnect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.geekvpn.connection.ConnectionPrefs
import com.geekvpn.quick.QuickConnect
import com.geekvpn.scanner.NetworkIdentity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "اتصال خودکار روی وای‌فای ناشناس": when the phone joins a Wi-Fi the user
 * has not marked as trusted, the VPN starts by itself.
 *
 * The system delivers the "Wi-Fi available" callback to [WifiJoinReceiver]
 * through a PendingIntent, so no service has to stay alive for it. The
 * receiver runs in the VPN process, where the core's running state is known.
 * Where Android forbids starting a foreground service from the background
 * (API 31+, unless battery optimisation is off for the app) it posts a
 * notification that starts the connection with one tap instead.
 *
 * Networks are told apart like the scanner's ([NetworkIdentity]): by what
 * DHCP handed out, since the SSID needs the location permission. Two
 * networks with the same router defaults can therefore look alike.
 */
object AutoConnect {
    enum class Decision { Nothing, Start, Ask }

    /** What to do when a Wi-Fi comes up; pure, for the tests. */
    fun decide(
        enabled: Boolean,
        identity: NetworkIdentity,
        trusted: Set<String>,
        running: Boolean,
        hasServer: Boolean,
        canStart: Boolean,
    ): Decision = when {
        !enabled || running || !hasServer -> Decision.Nothing
        identity.kind != NetworkIdentity.Kind.Wifi -> Decision.Nothing
        identity.key in trusted -> Decision.Nothing
        canStart -> Decision.Start
        else -> Decision.Ask
    }

    /** Registers or drops the Wi-Fi callback to match the setting; call after changing it and at boot. */
    fun sync(context: Context) {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        val pending = callbackIntent(context)
        try {
            connectivity.unregisterNetworkCallback(pending)
        } catch (e: IllegalArgumentException) {
            // Nothing was registered with it.
            LogUtil.d(AppConfig.TAG, "AutoConnect: no Wi-Fi callback to drop (${e.message})")
        }
        if (!ConnectionPrefs.open().autoOnWifi) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivity.registerNetworkCallback(request, pending)
        } catch (e: RuntimeException) {
            // SecurityException or TooManyRequestsException: the setting stays, the trigger does not.
            LogUtil.e(AppConfig.TAG, "AutoConnect: registering the Wi-Fi callback failed", e)
        }
    }

    /**
     * Whether a foreground service may be started from a background broadcast:
     * always below API 31, and from 31 on only while battery optimisation is
     * off for the app (one of Android's exemptions).
     */
    fun canStartInBackground(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    // The system fills in the network, so the intent must stay mutable (FLAG_MUTABLE from API 31).
    private fun callbackIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, WifiJoinReceiver::class.java).setAction(ACTION_WIFI),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0),
        )

    /** Runs in the VPN process: decides, then starts the core or asks with a notification. */
    internal suspend fun onWifi(context: Context, network: Network?) {
        val prefs = ConnectionPrefs.open()
        if (!prefs.autoOnWifi) return
        // DHCP's DNS servers can arrive a moment after the network does; the identity needs them.
        val identity = withTimeoutOrNull(LINK_WAIT_MS) {
            var id = identify(context, network)
            while (id.kind == NetworkIdentity.Kind.Wifi && !hasDns(context, network)) {
                delay(LINK_POLL_MS)
                id = identify(context, network)
            }
            id
        } ?: identify(context, network)
        val vpnReady = !SettingsManager.isVpnMode() || VpnService.prepare(context) == null
        val decision = decide(
            enabled = true,
            identity = identity,
            trusted = prefs.trustedNetworks,
            running = CoreServiceManager.isRunning(),
            hasServer = !MmkvManager.getSelectServer().isNullOrEmpty(),
            canStart = vpnReady && canStartInBackground(context),
        )
        LogUtil.i(AppConfig.TAG, "AutoConnect: Wi-Fi ${identity.key} -> $decision")
        when (decision) {
            Decision.Nothing -> Unit
            Decision.Start -> LauncherManager.startService(context)
            Decision.Ask -> ask(context)
        }
    }

    private fun identify(context: Context, network: Network?): NetworkIdentity =
        if (network != null) NetworkIdentity.of(context, network) else NetworkIdentity.current(context)

    private fun hasDns(context: Context, network: Network?): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val target = network ?: connectivity.activeNetwork ?: return true
        return connectivity.getLinkProperties(target)?.dnsServers?.isNotEmpty() != false
    }

    private fun ask(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            LogUtil.i(AppConfig.TAG, "AutoConnect: notifications not allowed, prompt skipped")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.geek_auto_channel), NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val open = QuickConnect.pendingIntent(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.geek_auto_wifi_title))
            .setContentText(context.getString(R.string.geek_auto_wifi_text))
            .setContentIntent(open)
            .addAction(0, context.getString(R.string.geek_auto_wifi_connect), open)
            .setAutoCancel(true)
            .setTimeoutAfter(PROMPT_TIMEOUT_MS)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "AutoConnect: posting the prompt refused", e)
        }
    }

    /** Owns the receivers' short async work; each piece is bounded by the broadcast's own time limit. */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal const val ACTION_WIFI = "com.geekvpn.action.WIFI_AVAILABLE"
    private const val REQUEST_CODE = 0x6e6c
    private const val CHANNEL_ID = "geek_auto_connect"
    private const val NOTIFICATION_ID = 0x6e6c
    private const val LINK_WAIT_MS = 4_000L
    private const val LINK_POLL_MS = 500L
    private const val PROMPT_TIMEOUT_MS = 10 * 60_000L
}

/** The system's "a Wi-Fi is available" callback (see [AutoConnect]); runs in `:daemon`. */
class WifiJoinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoConnect.ACTION_WIFI) return
        val network = IntentCompat.getParcelableExtra(intent, ConnectivityManager.EXTRA_NETWORK, Network::class.java)
        val result = goAsync()
        AutoConnect.scope.launch {
            try {
                withTimeoutOrNull(RECEIVER_BUDGET_MS) { AutoConnect.onWifi(context.applicationContext, network) }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        /** goAsync allows about 10 s. */
        const val RECEIVER_BUDGET_MS = 8_000L
    }
}

/** A registered network callback does not survive a reboot or an app update: register again. */
class AutoConnectBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> AutoConnect.sync(context.applicationContext)
        }
    }
}
