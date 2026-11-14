package com.geekvpn.diagnose

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DiagnosisTest {

    private fun results(vararg pairs: Pair<Check, Outcome>): Map<Check, Outcome> =
        Check.entries.associateWith { Outcome.Ok } + pairs

    @Test
    fun the_first_thing_that_matters_is_the_verdict() {
        assertEquals(Verdict.AllGood, Diagnosis.verdict(results()))
        assertEquals(Verdict.NoNetwork, Diagnosis.verdict(results(Check.Network to Outcome.Failed, Check.Server to Outcome.Failed)))
        assertEquals(
            Verdict.NoInternet,
            Diagnosis.verdict(results(Check.Domestic to Outcome.Failed, Check.International to Outcome.Failed)),
        )
        assertEquals(Verdict.NoServer, Diagnosis.verdict(results(Check.Server to Outcome.Skipped)))
        assertEquals(Verdict.ServerFiltered, Diagnosis.verdict(results(Check.Server to Outcome.Filtered)))
        assertEquals(Verdict.ServerDown, Diagnosis.verdict(results(Check.Server to Outcome.Failed)))
        assertEquals(Verdict.TunnelBroken, Diagnosis.verdict(results(Check.Tunnel to Outcome.Failed)))
        assertEquals(Verdict.ApiDown, Diagnosis.verdict(results(Check.Api to Outcome.Failed)))
    }

    @Test
    fun a_dead_server_with_the_world_cut_off_points_at_a_tunnel_service() {
        assertEquals(
            Verdict.InternationalDown,
            Diagnosis.verdict(results(Check.International to Outcome.Failed, Check.Server to Outcome.Failed)),
        )
        // The server still answers: the world being slow is the only thing to say.
        assertEquals(Verdict.InternationalDown, Diagnosis.verdict(results(Check.International to Outcome.Failed)))
    }

    @Test
    fun the_ticket_line_names_outcomes_only() {
        val line = Diagnosis.summary(results(Check.Server to Outcome.Filtered))
        assertEquals(
            "diagnosis: server_filtered (network=ok domestic=ok international=ok server=filtered tunnel=ok api=ok)",
            line,
        )
    }

    private open class FakePorts(override val connected: Boolean = false) : DiagnosePorts {
        val asked = mutableListOf<Check>()
        override suspend fun network() = Outcome.Ok.also { asked += Check.Network }
        override suspend fun domestic() = Outcome.Ok.also { asked += Check.Domestic }
        override suspend fun international() = Outcome.Ok.also { asked += Check.International }
        override suspend fun server(): Outcome {
            asked += Check.Server
            throw IllegalStateException("boom")
        }
        override suspend fun tunnel() = Outcome.Ok.also { asked += Check.Tunnel }
        override suspend fun api() = Outcome.Ok.also { asked += Check.Api }
    }

    @Test
    fun every_check_runs_in_order_and_a_crash_counts_as_failed() = runTest {
        val ports = FakePorts(connected = false)
        val seen = mutableListOf<Pair<Check, Outcome?>>()
        val results = Diagnosis.run(ports) { check, outcome -> seen += check to outcome }

        assertEquals(Outcome.Failed, results[Check.Server])
        // Not connected: the tunnel is not tried.
        assertEquals(Outcome.Skipped, results[Check.Tunnel])
        assertFalse(Check.Tunnel in ports.asked)
        assertEquals(Check.entries, results.keys.toList())
        assertEquals(Check.entries.size * 2, seen.size)
        assertEquals(Check.Network to null, seen.first())
    }
}
