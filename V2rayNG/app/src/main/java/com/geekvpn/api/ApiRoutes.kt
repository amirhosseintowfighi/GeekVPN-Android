package com.geekvpn.api

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * The backend's addresses: the main one first, then the mirrors the build was
 * given. Requests are always built against [primary]; [ApiFailover] moves them
 * to another address when one cannot be reached. The last address that
 * answered is tried first, for this process's lifetime.
 */
class ApiHosts(bases: List<String>) {
    val all: List<HttpUrl> = bases.map { it.trim().trimEnd('/') }
        .filter { it.isNotEmpty() }
        .distinct()
        .map { it.toHttpUrl() }
        .also { require(it.isNotEmpty()) { "no API address" } }

    val primary: HttpUrl get() = all.first()

    @Volatile
    private var preferred = 0

    /** Every address once, starting with the last one that answered. */
    fun order(): List<HttpUrl> = all.indices.map { all[(preferred + it) % all.size] }

    fun answered(base: HttpUrl) {
        all.indexOf(base).takeIf { it >= 0 }?.let { preferred = it }
    }

    /** [url], built on [primary], moved onto [base]; null when it is not an API URL. */
    fun move(url: HttpUrl, base: HttpUrl): HttpUrl? {
        val from = primary.encodedPath.trimEnd('/')
        val path = url.encodedPath
        if (url.host != primary.host || url.port != primary.port || !path.startsWith(from)) return null
        val rest = path.removePrefix(from)
        return url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(base.encodedPath.trimEnd('/') + rest)
            .build()
    }

    companion object {
        /** [main] plus a comma-separated list, as BuildConfig carries them. */
        fun of(main: String, fallbacks: String) = ApiHosts(listOf(main) + fallbacks.split(','))
    }
}

/**
 * Whether a failed attempt may be repeated on another address.
 *
 * A GET is always safe. Anything else may have changed something on the
 * server (a checkout, a refresh token that rotates on use), so it moves only
 * when the request provably never reached the backend: the connection or TLS
 * handshake failed, or something else answered in its place below a 5xx (a
 * captive portal, a filtering page). A CDN's 5xx may come after the origin
 * already acted, so it is not repeated.
 */
object ApiFailoverPolicy {
    fun retryAfterError(method: String, error: IOException): Boolean =
        method == "GET" || neverSent(error)

    fun retryAfterForeignAnswer(method: String, code: Int): Boolean =
        method == "GET" || code < 500

    /** The API answers JSON; 204 and 304 carry no body at all. */
    fun isApiAnswer(code: Int, contentType: String?): Boolean =
        code == 204 || code == 304 || contentType?.contains("json", ignoreCase = true) == true

    private fun neverSent(error: IOException): Boolean = when (error) {
        is UnknownHostException, is ConnectException, is NoRouteToHostException,
        is SSLHandshakeException, is SSLPeerUnverifiedException -> true
        is SocketTimeoutException -> error.message?.contains("connect", ignoreCase = true) == true
        // OkHttp, when an HTTP proxy refuses the tunnel.
        else -> error.message?.startsWith("Unexpected response code for CONNECT") == true
    }
}

/** Tries each of [hosts] in turn, as far as [ApiFailoverPolicy] allows. */
class ApiFailover(private val hosts: ApiHosts) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val candidates = hosts.order()
        var lastError: IOException? = null
        for ((index, base) in candidates.withIndex()) {
            val last = index == candidates.lastIndex
            val url = hosts.move(original.url, base) ?: return chain.proceed(original)
            val response = try {
                chain.proceed(original.newBuilder().url(url).build())
            } catch (e: IOException) {
                if (chain.call().isCanceled() || last || !ApiFailoverPolicy.retryAfterError(original.method, e)) throw e
                lastError = e
                continue
            }
            val api = ApiFailoverPolicy.isApiAnswer(response.code, response.header("Content-Type"))
            if (api || last || !ApiFailoverPolicy.retryAfterForeignAnswer(original.method, response.code)) {
                if (api) hosts.answered(base)
                return response
            }
            response.close()
        }
        throw lastError ?: IOException("no API address answered")
    }
}

/**
 * Sends API traffic through the running core's local HTTP proxy, and straight
 * out when there is none.
 *
 * The app's own package is excluded from the VPN, so without this the API
 * goes out on the phone's network even while connected, and a filtered API
 * domain would lock a connected user out of sign-in, the shop and sync. When
 * the core is up its proxy comes first, then the direct route: OkHttp moves to
 * the next proxy only when connecting fails, so a broken tunnel still falls
 * back. Whether the core is up is checked by connecting to its port (on
 * localhost, so it is refused at once when nothing listens) and remembered
 * for a few seconds; this process has no other view of the VPN process.
 */
class CoreProxySelector(
    private val port: () -> Int,
    private val listening: (SocketAddress) -> Boolean = ::probe,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProxySelector() {
    @Volatile
    private var checked: Pair<Long, Boolean>? = null

    override fun select(uri: URI?): List<Proxy> {
        val port = port().takeIf { it in 1..65535 } ?: return listOf(Proxy.NO_PROXY)
        val address = InetSocketAddress("127.0.0.1", port)
        val now = clock()
        val up = checked?.takeIf { now - it.first < REMEMBER_MS }?.second
            ?: listening(address).also { checked = now to it }
        return if (up) listOf(Proxy(Proxy.Type.HTTP, address), Proxy.NO_PROXY) else listOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        // The core went away: look again on the next connection.
        if (sa is InetSocketAddress && sa.address?.isLoopbackAddress == true) checked = null
    }

    private companion object {
        const val REMEMBER_MS = 3_000L

        fun probe(address: SocketAddress): Boolean = try {
            Socket().use { it.connect(address, 200) }
            true
        } catch (_: IOException) {
            false
        }
    }
}
