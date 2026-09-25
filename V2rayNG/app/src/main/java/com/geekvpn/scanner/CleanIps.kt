package com.geekvpn.scanner

import android.content.Context
import com.v2ray.ang.handler.MmkvManager

/**
 * The scanner's results as smart connect and the failover monitor use them:
 * per config GUID, on the network the phone is on now.
 */
object CleanIps {
    private val store get() = ScanController.store

    /** Fresh clean addresses for [guid]'s domain on this network, best first; empty when none. */
    fun fresh(context: Context, guid: String, now: Long = System.currentTimeMillis()): List<String> {
        val target = targetOf(guid) ?: return emptyList()
        val record = store.results(target, NetworkIdentity.current(context).key) ?: return emptyList()
        return if (record.isStale(now)) emptyList() else record.results.map { it.ip }
    }

    /** Whether [guid]'s domain was ever scanned on this network (fresh or not). */
    fun scanned(context: Context, guid: String): Boolean {
        val target = targetOf(guid) ?: return false
        return store.results(target, NetworkIdentity.current(context).key) != null
    }

    /**
     * [guid] uses [ip] on this network from now on, for connecting and for the
     * delay test; null puts it back on its own address.
     */
    fun use(context: Context, guid: String, ip: String?, delayMs: Long, now: Long = System.currentTimeMillis()) {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return
        if (CdnTarget.of(profile) == null) return
        val network = NetworkIdentity.current(context).key
        if (ip == null) {
            store.clearOverride(ProfileKey.of(profile), network)
        } else {
            store.setOverride(ProfileKey.of(profile), network, IpOverride(ip, now, delayMs))
        }
    }

    /**
     * Whether [guid]'s SNI and Host domains resolve to Cloudflare, checking again
     * after a day. True or false is stored and gates every override; null means
     * the names did not resolve to anything usable (offline, or a filtered name
     * answered with a private address), which is no verdict. Blocking.
     */
    fun verify(context: Context, guid: String, now: Long = System.currentTimeMillis()): Boolean? {
        val target = targetOf(guid) ?: return false
        store.cdnVerdict(target)?.let { if (now - it.checkedAt < VERDICT_MAX_AGE_MS) return it.behind }
        val answers = setOf(target.sni, target.host).map { CloudflareCheck.resolvesToCloudflare(context, it) }
        val verdict = when {
            answers.any { it == false } -> false
            answers.all { it == true } -> true
            else -> null
        }
        if (verdict != null) store.setCdnVerdict(target, CdnVerdict(verdict, now))
        return verdict
    }

    /** The stored verdict for [guid]'s domain, without touching the network. */
    fun knownBehindCloudflare(guid: String): Boolean? = targetOf(guid)?.let { store.cdnVerdict(it)?.behind }

    /** A scan the customer asked for found clean addresses: take the domain as Cloudflare's. */
    fun trustAfterScan(guid: String, now: Long = System.currentTimeMillis()) {
        val target = targetOf(guid) ?: return
        if (store.cdnVerdict(target) == null) store.setCdnVerdict(target, CdnVerdict(true, now))
    }

    private const val VERDICT_MAX_AGE_MS = 24 * 60 * 60 * 1000L

    private fun targetOf(guid: String): CdnTarget? = MmkvManager.decodeServerConfig(guid)?.let { CdnTarget.of(it) }
}
