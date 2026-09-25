package com.geekvpn.connection

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.IDN

/** The address the internet sees, and its country when we could tell. */
data class ExitIp(val ip: String, val countryCode: String?)

/**
 * Asks check-host.net who we are.
 *
 * This app's own traffic is excluded from the VPN, so a plain request shows
 * the phone's real address. While connected the request goes through the
 * core's local HTTP proxy instead, and shows the server's exit address.
 */
object ExitIpLookup {
    private const val IP_URL = "https://check-host.net/ip"
    private const val INFO_URL = "https://check-host.net/ip-info?host="

    suspend fun lookup(throughProxy: Boolean): ExitIp? = withContext(Dispatchers.IO) {
        val ip = fetch(IP_URL, throughProxy)?.let(::parseIp) ?: return@withContext null
        // The country is a nicety: the IP alone is still worth showing.
        val country = fetch(INFO_URL + IDN.toASCII(ip), throughProxy)?.let(::parseCountry)
        ExitIp(ip, country)
    }

    private fun fetch(url: String, throughProxy: Boolean): String? = try {
        HttpUtil.getUrlContentWithUserAgent(
            UrlContentRequest(
                url = url,
                timeout = 8_000,
                httpPort = if (throughProxy) SettingsManager.getHttpPort() else 0,
                proxyUsername = if (throughProxy) SettingsManager.getSocksUsername() else null,
                proxyPassword = if (throughProxy) SettingsManager.getSocksPassword() else null,
            )
        ).takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        // HttpUtil reports every failure as an Exception of some kind; none is fatal here.
        LogUtil.w(AppConfig.TAG, "ExitIp: lookup failed (proxy=$throughProxy)", e)
        null
    }

    private val IPV4 = Regex("""\b((?:25[0-5]|2[0-4]\d|1?\d?\d)(?:\.(?:25[0-5]|2[0-4]\d|1?\d?\d)){3})\b""")
    private val IPV6 = Regex("""\b([0-9a-fA-F]{0,4}(?::[0-9a-fA-F]{0,4}){2,7})\b""")

    /** The first address in the body; the endpoint answers with the bare IP. */
    fun parseIp(body: String): String? {
        val text = body.trim()
        IPV4.find(text)?.let { return it.groupValues[1] }
        return IPV6.find(text)?.groupValues?.get(1)?.takeIf { it.count { c -> c == ':' } >= 2 }
    }

    private val FLAG_IMAGE = Regex("""flags?/(?:\d+/)?([a-zA-Z]{2})\.(?:png|svg|gif)""")
    private val COUNTRY_FIELD = Regex(""""country_?code"\s*:\s*"([A-Za-z]{2})"""")

    /** The ISO code from check-host's ip-info page: its flag image, or a JSON field. */
    fun parseCountry(body: String): String? {
        val match = COUNTRY_FIELD.find(body) ?: FLAG_IMAGE.find(body) ?: return null
        return match.groupValues[1].uppercase(java.util.Locale.ROOT)
    }
}
