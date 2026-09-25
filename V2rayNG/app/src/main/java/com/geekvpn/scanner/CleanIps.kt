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

    /** [guid] uses [ip] on this network from now on, for connecting and for the delay test. */
    fun use(context: Context, guid: String, ip: String, delayMs: Long, now: Long = System.currentTimeMillis()) {
        val profile = MmkvManager.decodeServerConfig(guid) ?: return
        if (CdnTarget.of(profile) == null) return
        store.setOverride(ProfileKey.of(profile), NetworkIdentity.current(context).key, IpOverride(ip, now, delayMs))
    }

    private fun targetOf(guid: String): CdnTarget? = MmkvManager.decodeServerConfig(guid)?.let { CdnTarget.of(it) }
}
