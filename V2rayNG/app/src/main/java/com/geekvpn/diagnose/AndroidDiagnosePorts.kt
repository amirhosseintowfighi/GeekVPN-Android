package com.geekvpn.diagnose

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.geekvpn.GeekGraph
import com.geekvpn.api.ApiException
import com.geekvpn.scanner.CloudflareCheck
import com.geekvpn.scanner.IpOverrides
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * The checks done on this phone. The app's own package is excluded from the
 * VPN, so every direct check sees the phone's own network even while
 * connected; only [tunnel] goes through the core's local proxy. Nothing here
 * logs an address.
 */
class AndroidDiagnosePorts(private val context: Context) : DiagnosePorts {
    override val connected: Boolean get() = GeekGraph.connectionPrefs.connectedSince > 0

    private val direct: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    override suspend fun network(): Outcome = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork)
        if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) Outcome.Ok else Outcome.Failed
    }

    override suspend fun domestic(): Outcome = anyAnswers(direct, DOMESTIC, "domestic")

    override suspend fun international(): Outcome = anyAnswers(direct, INTERNATIONAL, "international")

    override suspend fun server(): Outcome = withContext(Dispatchers.IO) {
        val guid = MmkvManager.getSelectServer() ?: return@withContext Outcome.Skipped
        val stored = MmkvManager.decodeServerConfig(guid) ?: return@withContext Outcome.Skipped
        // The address a connection would really use: a clean IP from the scanner if one applies.
        val profile = IpOverrides.apply(context, stored)
        val host = profile.server?.trim()?.removePrefix("[")?.removeSuffix("]")
        if (host.isNullOrEmpty()) return@withContext Outcome.Skipped
        val port = profile.serverPort?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }

        val addresses = try {
            InetAddress.getAllByName(host).toList()
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "Diagnose: the selected server's name does not resolve", e)
            return@withContext Outcome.Failed
        }
        val v4 = addresses.filterIsInstance<Inet4Address>()
        val public = addresses.filterNot { it is Inet4Address && CloudflareCheck.isPrivate(toLong(it)) }
        if (v4.isNotEmpty() && public.isEmpty()) return@withContext Outcome.Filtered
        // UDP protocols do not accept TCP; a name that resolves is all we can tell.
        if (port == null || stored.configType in UDP_TYPES) return@withContext Outcome.Ok

        for (address in public.take(2)) {
            try {
                Socket().use { it.connect(InetSocketAddress(address, port), (TIMEOUT_S * 1000).toInt()) }
                return@withContext Outcome.Ok
            } catch (e: IOException) {
                LogUtil.w(AppConfig.TAG, "Diagnose: the selected server's port does not answer", e)
            }
        }
        Outcome.Failed
    }

    override suspend fun tunnel(): Outcome {
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", SettingsManager.getHttpPort())))
            .proxyAuthenticator(GeekGraph.coreProxyAuthenticator())
            .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
            .build()
        return anyAnswers(client, TUNNEL, "tunnel")
    }

    override suspend fun api(): Outcome = try {
        GeekGraph.api.promo()
        Outcome.Ok
    } catch (e: ApiException) {
        // An error the API itself sent still means it is reachable.
        if (e.isNetwork) {
            LogUtil.w(AppConfig.TAG, "Diagnose: the backend is out of reach", e)
            Outcome.Failed
        } else {
            Outcome.Ok
        }
    }

    /** Ok when any of [urls] gives an HTTP answer at all. */
    private suspend fun anyAnswers(client: OkHttpClient, urls: List<String>, what: String): Outcome =
        withContext(Dispatchers.IO) {
            for (url in urls) {
                try {
                    client.newCall(Request.Builder().url(url).head().build()).execute().use { return@withContext Outcome.Ok }
                } catch (e: IOException) {
                    LogUtil.w(AppConfig.TAG, "Diagnose: $what probe failed", e)
                }
            }
            Outcome.Failed
        }

    private fun toLong(address: Inet4Address): Long =
        address.address.fold(0L) { acc, byte -> (acc shl 8) or (byte.toLong() and 0xFF) }

    private companion object {
        const val TIMEOUT_S = 6L
        val DOMESTIC = listOf("https://www.aparat.com/", "https://www.digikala.com/")
        val INTERNATIONAL = listOf("https://www.cloudflare.com/cdn-cgi/trace", "https://check-host.net/")
        val TUNNEL = listOf("https://www.gstatic.com/generate_204", "https://www.cloudflare.com/cdn-cgi/trace")
        val UDP_TYPES = setOf(EConfigType.WIREGUARD, EConfigType.HYSTERIA2, EConfigType.HYSTERIA)
    }
}
