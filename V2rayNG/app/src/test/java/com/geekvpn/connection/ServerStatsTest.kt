package com.geekvpn.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerStatsTest {
    private val t0 = 1_800_000_000_000L

    @Test
    fun untested_results_are_skipped_and_old_or_extra_samples_dropped() {
        var samples = ServerStatsLog.add(emptyList(), t0, 0)
        assertEquals(emptyList<DelaySample>(), samples)
        repeat(ServerStatsLog.MAX_SAMPLES + 3) { i -> samples = ServerStatsLog.add(samples, t0 + i, 100L + i) }
        assertEquals(ServerStatsLog.MAX_SAMPLES, samples.size)
        assertEquals(103L, samples.first().delayMs)
        val later = ServerStatsLog.add(samples, t0 + ServerStatsLog.KEEP_MS + 1_000, -1)
        assertEquals(listOf(DelaySample(t0 + ServerStatsLog.KEEP_MS + 1_000, -1)), later)
    }

    @Test
    fun the_summary_has_the_median_of_answers_and_the_success_rate() {
        val samples = listOf(300L, -1L, 100L, 200L, -1L).mapIndexed { i, d -> DelaySample(t0 + i, d) }
        val summary = ServerStatsLog.summary(samples, t0 + 10)
        assertEquals(ServerSummary(5, 3, 200), summary)
        assertEquals(60, summary.successPercent)
        assertEquals(150L, ServerStatsLog.summary(listOf(DelaySample(t0, 100), DelaySample(t0, 200)), t0).medianMs)
        assertNull(ServerStatsLog.summary(listOf(DelaySample(t0, -1)), t0).medianMs)
        assertEquals(0, ServerStatsLog.summary(emptyList(), t0).successPercent)
    }

    @Test
    fun stability_ranks_answer_rate_then_median_and_puts_the_little_tested_last() {
        val stats = mapOf(
            "fast-flaky" to ServerSummary(10, 5, 80),
            "slow-solid" to ServerSummary(10, 10, 400),
            "quick-solid" to ServerSummary(10, 10, 150),
            "new" to ServerSummary(1, 1, 50),
            "never" to null,
        )
        val order = ServerStatsLog.byStability(listOf("new", "fast-flaky", "never", "slow-solid", "quick-solid")) { stats[it] }
        assertEquals(listOf("quick-solid", "slow-solid", "fast-flaky", "new", "never"), order)
    }

    @Test
    fun samples_survive_encoding_and_junk_is_ignored() {
        val samples = listOf(DelaySample(t0, 120), DelaySample(t0 + 5, -1))
        assertEquals(samples, ServerStatsLog.decode(ServerStatsLog.encode(samples)))
        assertEquals(emptyList<DelaySample>(), ServerStatsLog.decode(null))
        assertEquals(listOf(DelaySample(1, 2)), ServerStatsLog.decode("x:y,1:2,3"))
    }
}
