package com.geekvpn.smartconnect

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartConnectTest {

    /**
     * A service in memory. [delay] gives a config's delay with the address it
     * uses now (null: its own); [carries] says which configs pass traffic once connected.
     */
    private class FakePorts(
        val guids: List<String>,
        val cdn: Set<String> = emptySet(),
        var fresh: List<String> = emptyList(),
        val scanFinds: List<String> = emptyList(),
        val delay: (guid: String, ip: String?) -> Long,
        val carries: (guid: String, ip: String?) -> Boolean = { _, _ -> true },
    ) : SmartConnectPorts {
        val ipOf = mutableMapOf<String, String>()
        val scans = mutableListOf<String>()
        val rounds = mutableListOf<List<String>>()
        val connects = mutableListOf<Choice>()

        override fun servers(groupId: String) = guids
        override fun scannable(guid: String) = guid in cdn
        override fun freshIps(guid: String) = if (guid in cdn) fresh else emptyList()
        override suspend fun quickScan(guid: String): List<String> {
            scans += guid
            return scanFinds
        }

        override fun useIp(guid: String, ip: String, delayMs: Long) {
            ipOf[guid] = ip
        }

        override suspend fun measure(guids: List<String>): Map<String, Long> {
            rounds += guids
            return guids.associateWith { delay(it, ipOf[it]) }
        }

        override suspend fun connect(guid: String): Boolean {
            val choice = Choice(guid, ipOf[guid])
            connects += choice
            return carries(guid, choice.ip)
        }
    }

    private fun stages() = mutableListOf<SmartStage>()

    @Test
    fun tunnel_service_tests_configs_only_and_connects_the_fastest() = runTest {
        val ports = FakePorts(listOf("a", "b", "c"), delay = { g, _ -> mapOf("a" to 400L, "b" to 150L, "c" to -1L).getValue(g) })
        val seen = stages()
        val result = SmartConnectUseCase(ports).run("g", fallback = "a") { seen += it }

        assertEquals(SmartResult.Connected(Choice("b"), 150), result)
        assertEquals(listOf(listOf("a", "b", "c")), ports.rounds)
        assertTrue(ports.scans.isEmpty())
        assertEquals(listOf(SmartStage.Testing, SmartStage.Connecting(1, 2)), seen)
    }

    @Test
    fun a_failed_connection_moves_to_the_next_best_three_at_most() = runTest {
        val delays = mapOf("a" to 100L, "b" to 200L, "c" to 300L, "d" to 400L)
        val ports = FakePorts(delays.keys.toList(), delay = { g, _ -> delays.getValue(g) }, carries = { _, _ -> false })
        val seen = stages()
        val result = SmartConnectUseCase(ports).run("g", fallback = null) { seen += it }

        assertEquals(SmartResult.Failed(3), result)
        assertEquals(listOf("a", "b", "c"), ports.connects.map { it.guid })
        assertEquals((1..3).map { SmartStage.Connecting(it, 3) }, seen.filterIsInstance<SmartStage.Connecting>())
    }

    @Test
    fun second_choice_wins_when_the_best_does_not_carry_traffic() = runTest {
        val ports = FakePorts(listOf("a", "b"), delay = { g, _ -> if (g == "a") 90L else 180L }, carries = { g, _ -> g == "b" })
        val result = SmartConnectUseCase(ports).run("g", fallback = null) {}
        assertEquals(SmartResult.Connected(Choice("b"), 180), result)
    }

    @Test
    fun cdn_config_is_tested_with_each_fresh_address_and_uses_the_best() = runTest {
        val ports = FakePorts(
            guids = listOf("cdn", "plain"),
            cdn = setOf("cdn"),
            fresh = listOf("1.1.1.1", "2.2.2.2"),
            delay = { g, ip ->
                when {
                    g == "plain" -> 500L
                    ip == "1.1.1.1" -> 300L
                    ip == "2.2.2.2" -> 120L
                    else -> -1L
                }
            },
        )
        val seen = stages()
        val result = SmartConnectUseCase(ports).run("g", fallback = null) { seen += it }

        assertEquals(SmartResult.Connected(Choice("cdn", "2.2.2.2"), 120), result)
        // The plain config is tested once, with the first round; the CDN one per address.
        assertEquals(listOf(listOf("cdn", "plain"), listOf("cdn")), ports.rounds)
        assertEquals(Choice("cdn", "2.2.2.2"), ports.connects.single())
        assertTrue(ports.scans.isEmpty())
        assertFalse(SmartStage.FindingIp in seen)
    }

    @Test
    fun no_fresh_address_means_a_short_scan_first() = runTest {
        val ports = FakePorts(
            guids = listOf("cdn"),
            cdn = setOf("cdn"),
            scanFinds = listOf("3.3.3.3"),
            delay = { _, ip -> if (ip == "3.3.3.3") 200L else -1L },
        )
        val seen = stages()
        val result = SmartConnectUseCase(ports).run("g", fallback = null) { seen += it }

        assertEquals(listOf("cdn"), ports.scans)
        assertEquals(SmartStage.FindingIp, seen.first())
        assertEquals(SmartResult.Connected(Choice("cdn", "3.3.3.3"), 200), result)
    }

    @Test
    fun a_scan_that_finds_nothing_still_tests_the_configs_as_they_are() = runTest {
        val ports = FakePorts(listOf("cdn"), cdn = setOf("cdn"), delay = { _, ip -> if (ip == null) 250L else -1L })
        val result = SmartConnectUseCase(ports).run("g", fallback = null) {}
        assertEquals(SmartResult.Connected(Choice("cdn"), 250), result)
        assertEquals(listOf(listOf("cdn")), ports.rounds)
    }

    @Test
    fun only_three_addresses_are_tried() = runTest {
        val ports = FakePorts(listOf("cdn"), cdn = setOf("cdn"), fresh = listOf("1", "2", "3", "4", "5"), delay = { _, _ -> 100L })
        SmartConnectUseCase(ports).rank(listOf("cdn"))
        assertEquals(SmartConnectUseCase.MAX_IPS, ports.rounds.size)
    }

    @Test
    fun each_cdn_config_is_left_on_its_own_best_address() = runTest {
        val ports = FakePorts(
            guids = listOf("x", "y"),
            cdn = setOf("x", "y"),
            fresh = listOf("1.1.1.1", "2.2.2.2"),
            delay = { g, ip -> if ((g == "x") == (ip == "1.1.1.1")) 100L else 400L },
        )
        SmartConnectUseCase(ports).rank(ports.guids)
        assertEquals(mapOf("x" to "1.1.1.1", "y" to "2.2.2.2"), ports.ipOf)
    }

    @Test
    fun nothing_answering_tries_the_selected_config_once() = runTest {
        val ports = FakePorts(listOf("a", "b"), delay = { _, _ -> -1L })
        val result = SmartConnectUseCase(ports).run("g", fallback = "b") {}
        assertEquals(SmartResult.Connected(Choice("b"), 0, untested = true), result)
        assertEquals(listOf(Choice("b")), ports.connects)
    }

    @Test
    fun an_empty_service_has_nothing_to_connect() = runTest {
        val ports = FakePorts(emptyList(), delay = { _, _ -> 100L })
        assertEquals(SmartResult.NoServers, SmartConnectUseCase(ports).run("g", fallback = null) {})
    }

    @Test
    fun ties_keep_list_order() {
        val measured = listOf(Measured(Choice("a"), 100), Measured(Choice("b"), 100), Measured(Choice("c"), 0))
        assertEquals(listOf("a", "b"), SmartConnectUseCase.best(measured).map { it.choice.guid })
    }
}

class FailoverPolicyTest {
    private val minute = 60_000L

    @Test
    fun one_bad_check_is_not_enough() {
        val policy = FailoverPolicy(FailoverThreshold.Ms2000)
        assertEquals(FailoverPolicy.Action.Keep, policy.onCheck(-1, 0))
        assertEquals(FailoverPolicy.Action.Keep, policy.onCheck(300, 1))
        assertEquals(FailoverPolicy.Action.Keep, policy.onCheck(-1, 2))
        assertEquals(FailoverPolicy.Action.Failover, policy.onCheck(-1, 3))
    }

    @Test
    fun slow_counts_as_bad_only_above_the_threshold() {
        val policy = FailoverPolicy(FailoverThreshold.Ms1000)
        assertEquals(FailoverPolicy.Action.Keep, policy.onCheck(900, 0))
        assertEquals(FailoverPolicy.Action.Keep, policy.onCheck(900, 1))
        policy.onCheck(1_500, 2)
        assertEquals(FailoverPolicy.Action.Failover, policy.onCheck(1_500, 3))
    }

    @Test
    fun lost_ignores_slowness() {
        val policy = FailoverPolicy(FailoverThreshold.Lost)
        repeat(5) { assertEquals(FailoverPolicy.Action.Keep, policy.onCheck(9_000, it.toLong())) }
        policy.onCheck(-1, 10)
        assertEquals(FailoverPolicy.Action.Failover, policy.onCheck(0, 11))
    }

    @Test
    fun off_never_fails_over() {
        val policy = FailoverPolicy(FailoverThreshold.Off)
        repeat(5) { assertEquals(FailoverPolicy.Action.Keep, policy.onCheck(-1, it.toLong())) }
    }

    @Test
    fun cooldown_doubles_while_the_connection_stays_bad_and_resets_when_good() {
        val policy = FailoverPolicy(FailoverThreshold.Ms2000)
        policy.onCheck(-1, 0)
        assertEquals(FailoverPolicy.Action.Failover, policy.onCheck(-1, 0))
        policy.onFailover(0)

        // Within the first cooldown (2 min): keep.
        policy.onCheck(-1, 1 * minute)
        assertEquals(FailoverPolicy.Action.Keep, policy.onCheck(-1, 1 * minute + 1))
        assertEquals(FailoverPolicy.Action.Failover, policy.onCheck(-1, 2 * minute))
        policy.onFailover(2 * minute)

        // Second cooldown is 4 min.
        policy.onCheck(-1, 5 * minute)
        assertEquals(FailoverPolicy.Action.Keep, policy.onCheck(-1, 5 * minute + 1))
        policy.onCheck(-1, 6 * minute)
        assertEquals(FailoverPolicy.Action.Failover, policy.onCheck(-1, 6 * minute + 1))
        policy.onFailover(6 * minute + 1)

        // A good check resets the doubling, not the cooldown already running.
        policy.onCheck(200, 7 * minute)
        policy.onCheck(-1, 15 * minute)
        assertEquals(FailoverPolicy.Action.Failover, policy.onCheck(-1, 15 * minute + 1))
        policy.onFailover(15 * minute + 1)
        policy.onCheck(-1, 17 * minute + 2)
        assertEquals(FailoverPolicy.Action.Failover, policy.onCheck(-1, 17 * minute + 3))
    }

    @Test
    fun unknown_stored_threshold_falls_back_to_the_default() {
        assertEquals(FailoverThreshold.DEFAULT, FailoverThreshold.of("nonsense"))
        assertEquals(FailoverThreshold.Lost, FailoverThreshold.of("lost"))
    }
}
