package com.geekvpn.scanner

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Whether a config's domain really is served by Cloudflare. A ws/grpc/xhttp
 * config with TLS and a domain only *could* be behind the CDN; if its domain
 * points straight at its own server, a Cloudflare address in its place breaks
 * it. So no scan and no override without this check passing.
 */
object CloudflareCheck {

    /** Cloudflare's IPv4 ranges as [start, end] pairs, from the bundled list. */
    @Volatile
    private var ranges: LongArray? = null

    /**
     * Resolves [domain] and says whether it lands on Cloudflare. Null when the
     * name does not resolve (offline, or blocked); not a verdict either way.
     * Blocking: call off the main thread.
     */
    fun resolvesToCloudflare(context: Context, domain: String): Boolean? {
        val addresses = try {
            InetAddress.getAllByName(domain).filterIsInstance<Inet4Address>()
        } catch (e: UnknownHostException) {
            LogUtil.w(AppConfig.TAG, "CloudflareCheck: $domain does not resolve", e)
            return null
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "CloudflareCheck: no network permission", e)
            return null
        }
        // A filtered name is often answered with a private address: no verdict.
        val public = addresses.map { toLong(it.address) }.filterNot { isPrivate(it) }
        if (public.isEmpty()) return null
        val table = ranges(context) ?: return null
        return public.any { contains(table, it) }
    }

    /** 0/8, 10/8, 100.64/10, 127/8, 169.254/16, 172.16/12, 192.168/16. */
    fun isPrivate(ip: Long): Boolean {
        val a = (ip ushr 24).toInt()
        val b = ((ip ushr 16) and 0xFF).toInt()
        return a == 0 || a == 10 || a == 127 ||
            (a == 100 && b in 64..127) ||
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168)
    }

    private fun ranges(context: Context): LongArray? {
        ranges?.let { return it }
        return try {
            val text = context.assets.open(ScanService.RANGES_ASSET).bufferedReader().use { it.readText() }
            parse(text).also { ranges = it }
        } catch (e: java.io.IOException) {
            LogUtil.e(AppConfig.TAG, "CloudflareCheck: bundled ranges missing", e)
            null
        }
    }

    /** "a.b.c.d/n" or "a.b.c.d" per line into sorted [start, end] pairs; bad lines are skipped. */
    fun parse(text: String): LongArray {
        val pairs = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val ip = line.substringBefore('/')
                val bits = line.substringAfter('/', "32").toIntOrNull()?.takeIf { it in 0..32 } ?: return@mapNotNull null
                val base = parseIpv4(ip) ?: return@mapNotNull null
                val size = 1L shl (32 - bits)
                val start = base and (size - 1).inv() and 0xFFFFFFFFL
                start to (start + size - 1)
            }
            .sortedBy { it.first }
            .toList()
        val out = LongArray(pairs.size * 2)
        pairs.forEachIndexed { i, (start, end) ->
            out[i * 2] = start
            out[i * 2 + 1] = end
        }
        return out
    }

    fun contains(table: LongArray, ip: Long): Boolean {
        // Binary search on the starts; ranges from the list do not overlap in practice,
        // and a hit in any range that starts at or before the address is checked.
        var lo = 0
        var hi = table.size / 2 - 1
        var candidate = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (table[mid * 2] <= ip) {
                candidate = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        var i = candidate
        // Walk back over earlier starts in case a wider range covers the address.
        while (i >= 0 && i >= candidate - 8) {
            if (ip <= table[i * 2 + 1]) return true
            i--
        }
        return false
    }

    fun parseIpv4(text: String): Long? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    private fun toLong(bytes: ByteArray): Long =
        bytes.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
}
