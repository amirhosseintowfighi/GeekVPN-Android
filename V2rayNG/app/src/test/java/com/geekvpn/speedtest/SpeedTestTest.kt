package com.geekvpn.speedtest

import com.geekvpn.ui.speedtest.SpeedTestViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestTest {

    @Test
    fun megabits_come_from_bytes_and_time() {
        // 12.5 MB in two seconds is 50 Mbps.
        assertEquals(50.0, SpeedMath.mbps(12_500_000, 2_000_000_000), 0.001)
        assertEquals(0.0, SpeedMath.mbps(0, 1_000), 0.0)
        assertEquals(0.0, SpeedMath.mbps(1_000, 0), 0.0)
    }

    @Test
    fun ping_is_the_median() {
        assertEquals(120L, SpeedMath.median(listOf(900, 110, 120, 130, 115)))
        assertNull(SpeedMath.median(emptyList()))
    }

    private class FakeProbe : SpeedProbe {
        val gate = CompletableDeferred<SpeedResult>()
        var throughVpn: Boolean? = null
        override suspend fun run(throughVpn: Boolean, progress: SpeedProgress): SpeedResult {
            this.throughVpn = throughVpn
            progress.onProgress(SpeedPhase.Download, 12.0)
            return gate.await()
        }
    }

    @Test
    fun the_test_runs_through_the_vpn_when_connected_and_reports_progress() = runTest {
        val probe = FakeProbe()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = SpeedTestViewModel(probe, connected = { true }, CoroutineScope(SupervisorJob() + dispatcher))
        vm.toggle()
        testScheduler.advanceUntilIdle()
        assertEquals(true, probe.throughVpn)
        assertEquals(SpeedPhase.Download, vm.uiState.value.phase)
        assertEquals(12.0, vm.uiState.value.liveMbps, 0.0)

        probe.gate.complete(SpeedResult(80, 40.0, 10.0))
        testScheduler.advanceUntilIdle()
        assertFalse(vm.uiState.value.running)
        assertEquals(40.0, vm.uiState.value.result!!.downloadMbps!!, 0.0)
        assertFalse(vm.uiState.value.failed)
    }

    @Test
    fun nothing_measured_is_a_failure_and_a_second_tap_stops() = runTest {
        val probe = FakeProbe()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = SpeedTestViewModel(probe, connected = { false }, CoroutineScope(SupervisorJob() + dispatcher))
        vm.toggle()
        testScheduler.advanceUntilIdle()
        vm.toggle()
        assertFalse(vm.uiState.value.running)

        val second = FakeProbe()
        val vm2 = SpeedTestViewModel(second, connected = { false }, CoroutineScope(SupervisorJob() + dispatcher))
        vm2.toggle()
        second.gate.complete(SpeedResult(null, null, null))
        testScheduler.advanceUntilIdle()
        assertTrue(vm2.uiState.value.failed)
    }
}
