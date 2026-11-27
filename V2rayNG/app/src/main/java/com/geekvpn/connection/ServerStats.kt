package com.geekvpn.connection

import com.geekvpn.GeekStorage
import com.tencent.mmkv.MMKV

/** One real-delay test of a server: when, and the delay (> 0 ms) or a failure (< 0). */
data class DelaySample(val at: Long, val delayMs: Long)

/** What a server's recent tests add up to («مقایسه‌ی سرورها»). */
data class ServerSummary(
    val tests: Int,
    val answered: Int,
    /** Median of the answered tests; null when none answered. */
    val medianMs: Long?,
) {
    /** 0..100. */
    val successPercent: Int get() = if (tests == 0) 0 else answered * 100 / tests
}

/**
 * Every real-delay test's result kept per server for two weeks, so servers
 * can be compared by how they did over time instead of by one test. Pure, for
 * the tests; [ServerStats] stores it.
 *
 * Keyed by [com.geekvpn.scanner.ProfileKey], not the v2rayNG GUID: a
 * subscription refresh gives every server a new GUID.
 */
object ServerStatsLog {
    const val MAX_SAMPLES = 30
    const val KEEP_MS = 14L * 24 * 60 * 60 * 1000

    /** Fewer tests than this are not enough to rank a server as stable. */
    const val MIN_TESTS = 3

    /** Adds [delayMs] unless it is 0 (not tested: cancelled or timed out). */
    fun add(samples: List<DelaySample>, at: Long, delayMs: Long): List<DelaySample> {
        val kept = samples.filter { at - it.at <= KEEP_MS }
        if (delayMs == 0L) return kept
        return (kept + DelaySample(at, delayMs)).takeLast(MAX_SAMPLES)
    }

    fun summary(samples: List<DelaySample>, now: Long): ServerSummary {
        val recent = samples.filter { now - it.at <= KEEP_MS }
        val answered = recent.map { it.delayMs }.filter { it > 0 }.sorted()
        val median = when {
            answered.isEmpty() -> null
            answered.size % 2 == 1 -> answered[answered.size / 2]
            else -> (answered[answered.size / 2 - 1] + answered[answered.size / 2]) / 2
        }
        return ServerSummary(recent.size, answered.size, median)
    }

    /**
     * «پایدارترین» order: servers with enough tests first, by how often they
     * answered, then by their median; the rest after in their own order.
     */
    fun <T> byStability(items: List<T>, summary: (T) -> ServerSummary?): List<T> {
        val (ranked, rest) = items.partition { (summary(it)?.tests ?: 0) >= MIN_TESTS }
        val sorted = ranked.sortedWith(
            compareByDescending<T> { summary(it)!!.successPercent }
                .thenBy { summary(it)!!.medianMs ?: Long.MAX_VALUE },
        )
        return sorted + rest
    }

    fun encode(samples: List<DelaySample>): String = samples.joinToString(",") { "${it.at}:${it.delayMs}" }

    fun decode(value: String?): List<DelaySample> = value.orEmpty().split(',').mapNotNull { part ->
        val at = part.substringBefore(':').toLongOrNull() ?: return@mapNotNull null
        val delay = part.substringAfter(':', "").toLongOrNull() ?: return@mapNotNull null
        DelaySample(at, delay)
    }
}

/** [ServerStatsLog] in its own MMKV file; written by the app and by the VPN process's failover. */
class ServerStats(private val storage: MMKV) {
    fun record(results: Map<String, Long>, at: Long = System.currentTimeMillis()) {
        results.forEach { (profileKey, delay) ->
            val key = KEY_PREFIX + profileKey
            val next = ServerStatsLog.add(ServerStatsLog.decode(storage.decodeString(key)), at, delay)
            if (next.isEmpty()) storage.removeValueForKey(key) else storage.encode(key, ServerStatsLog.encode(next))
        }
    }

    fun summary(profileKey: String, now: Long = System.currentTimeMillis()): ServerSummary? =
        ServerStatsLog.summary(ServerStatsLog.decode(storage.decodeString(KEY_PREFIX + profileKey)), now)
            .takeIf { it.tests > 0 }

    companion object {
        private const val STORE_ID = "GEEK_STATS"
        private const val KEY_PREFIX = "delay|"

        fun open(): ServerStats = ServerStats(GeekStorage.open(STORE_ID))
    }
}
