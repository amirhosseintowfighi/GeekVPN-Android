package com.geekvpn.scanner

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.tencent.mmkv.MMKV

/** One clean address, as the Go scanner reports it. */
data class CleanIp(
    @SerializedName("ip") val ip: String,
    @SerializedName("port") val port: Int = 443,
    @SerializedName("pingMs") val pingMs: Long = 0,
    @SerializedName("latencyMs") val latencyMs: Long = 0,
    @SerializedName("jitterMs") val jitterMs: Double = 0.0,
    @SerializedName("downloadKBps") val downloadKBps: Long = 0,
    @SerializedName("colo") val colo: String? = null,
)

/** The best addresses one scan found for one domain on one network. */
data class ScanRecord(val results: List<CleanIp>, val scannedAt: Long) {
    fun isStale(now: Long): Boolean = now - scannedAt > STALE_AFTER_MS

    companion object {
        /** Cloudflare's routing and the operators' filtering both move within a day. */
        const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L
    }
}

/** The address a config uses on one network instead of its own. */
data class IpOverride(val ip: String, val appliedAt: Long, val latencyMs: Long)

/**
 * The scanner's memory: results per (domain, network) and the override per
 * (config, network). Overrides live here and never in the config itself, so
 * the original address is always one tap away and a subscription refresh,
 * which rewrites the config, leaves them alone.
 */
class ScanStore(private val storage: MMKV, private val gson: Gson = Gson()) {

    fun results(target: CdnTarget, network: String): ScanRecord? = read(resultKey(target, network), ScanRecord::class.java)

    /** Keeps the best [KEEP] of the new results and the ones already known, fastest first. */
    fun saveResults(target: CdnTarget, network: String, found: List<CleanIp>, at: Long): ScanRecord {
        val record = ScanRecord(rank(found).take(KEEP), at)
        storage.encode(resultKey(target, network), gson.toJson(record))
        return record
    }

    fun override(profileKey: String, network: String): IpOverride? = read(overrideKey(profileKey, network), IpOverride::class.java)

    fun setOverride(profileKey: String, network: String, value: IpOverride) {
        storage.encode(overrideKey(profileKey, network), gson.toJson(value))
    }

    fun clearOverride(profileKey: String, network: String) {
        storage.removeValueForKey(overrideKey(profileKey, network))
    }

    /** The scanner screen's "download test" switch; off unless the customer turns it on. */
    var downloadTest: Boolean
        get() = storage.decodeBool(KEY_DOWNLOAD, false)
        set(value) {
            storage.encode(KEY_DOWNLOAD, value)
        }

    private fun <T> read(key: String, type: Class<T>): T? {
        val json = storage.decodeString(key) ?: return null
        return try {
            gson.fromJson(json, type)
        } catch (_: JsonParseException) {
            null
        }
    }

    companion object {
        const val KEEP = 5
        private const val KEY_DOWNLOAD = "pref|download_test"

        /** Lowest latency first; jitter breaks ties, since a steady link beats a lucky one. */
        fun rank(results: List<CleanIp>): List<CleanIp> =
            results.distinctBy { it.ip }.sortedWith(compareBy<CleanIp> { it.latencyMs + it.jitterMs * 2 }.thenBy { it.pingMs })

        private fun resultKey(target: CdnTarget, network: String) = "scan|${target.sni}|${target.port}|$network"
        private fun overrideKey(profileKey: String, network: String) = "override|$profileKey|$network"
    }
}
