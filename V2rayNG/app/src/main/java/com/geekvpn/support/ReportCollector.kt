package com.geekvpn.support

import android.content.Context
import android.os.Build
import com.geekvpn.GeekGraph
import com.geekvpn.account.SubscriptionPlan
import com.geekvpn.connection.RouteMode
import com.geekvpn.scanner.NetworkIdentity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gathers [ReportFacts] and the end of the app's log on this phone. Blocking
 * (the core's version loads its native library, logcat is a process): call
 * off the main thread.
 */
object ReportCollector {
    fun facts(context: Context, now: Long = System.currentTimeMillis()): ReportFacts {
        val prefs = GeekGraph.connectionPrefs
        val network = NetworkIdentity.current(context)
        val failure = prefs.lastFailure
        return ReportFacts(
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            android = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            network = when (network.kind) {
                NetworkIdentity.Kind.Wifi -> "wifi"
                NetworkIdentity.Kind.Mobile -> "mobile " + network.key.removePrefix("mobile:")
                NetworkIdentity.Kind.Other -> "none/other"
            },
            mode = when {
                SettingsManager.isRootMode() -> "root"
                SettingsManager.isVpnMode() -> "vpn"
                else -> "proxy only"
            },
            route = (prefs.routeMode ?: RouteMode.Smart).key,
            autoServer = prefs.autoServer,
            connected = prefs.connectedSince > 0,
            core = try {
                CoreNativeManager.getLibVersion()
            } catch (e: UnsatisfiedLinkError) {
                LogUtil.w(AppConfig.TAG, "Report: core version unavailable", e)
                "unavailable"
            },
            config = selectedConfig(),
            lastFailure = failure?.first,
            lastFailureMinutesAgo = failure?.second?.takeIf { it > 0 }?.let { TimeUnit.MILLISECONDS.toMinutes(now - it) },
        )
    }

    /** `vless ws tls :443 (tunnel)`; the address never leaves the phone. */
    private fun selectedConfig(): String? {
        val guid = MmkvManager.getSelectServer() ?: return null
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        val source = when {
            !SubscriptionPlan.isAccountGuid(profile.subscriptionId) -> "manual link"
            else -> GeekGraph.accountStore.serviceFor(profile.subscriptionId)?.tier ?: "account service"
        }
        return listOfNotNull(
            profile.configType.name.lowercase(),
            profile.network,
            profile.security?.takeIf { it.isNotBlank() } ?: "none",
            profile.serverPort?.let { ":$it" },
        ).joinToString(" ") + " ($source)"
    }

    /**
     * The newest lines of this app's own log (every process of the app shares
     * its UID, so the VPN process's lines are there too). Warnings and errors
     * from anywhere, plus everything the app and the core log under their tags.
     */
    fun log(maxLines: Int = LOG_LINES): List<String> {
        val lines = try {
            val process = ProcessBuilder("logcat", "-d", "-t", "1000", "-v", "tag").redirectErrorStream(true).start()
            val read = process.inputStream.bufferedReader().use { it.readLines() }
            process.destroy()
            read
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "Report: logcat unavailable", e)
            return emptyList()
        }
        return lines.filter { line ->
            val level = line.firstOrNull()
            level == 'W' || level == 'E' || level == 'F' || OWN_TAGS.any { line.contains("/$it") }
        }.takeLast(maxLines)
    }

    private const val LOG_LINES = 80
    private val OWN_TAGS = listOf(AppConfig.TAG, "GoLog", "libv2ray")
}
