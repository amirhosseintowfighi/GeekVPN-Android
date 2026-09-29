package com.geekvpn.account

import com.geekvpn.api.AutoRenewResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRenewViewModelTest {
    private class FakePort : AutoRenewPort {
        val server = mutableMapOf("a" to AutoRenewResponse(false, true, null, null), "b" to AutoRenewResponse(false, false, null, null))
        val sets = mutableListOf<Pair<String, Boolean>>()

        override suspend fun get(subscriptionId: String) = server.getValue(subscriptionId)

        override suspend fun set(subscriptionId: String, enabled: Boolean): AutoRenewResponse {
            sets += subscriptionId to enabled
            return server.getValue(subscriptionId).copy(enabled = enabled).also { server[subscriptionId] = it }
        }
    }

    @Test
    fun switches_load_and_turn_on() = runTest {
        val port = FakePort()
        val vm = AutoRenewViewModel(port, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        vm.load(listOf("a", "b"))
        testScheduler.advanceUntilIdle()
        assertEquals(AutoRenewState(enabled = false, available = true), vm.switches.value["a"])

        vm.set("a", true)
        // Flips at once, before the server answers.
        assertEquals(AutoRenewState(enabled = true, available = true, busy = true), vm.switches.value["a"])
        testScheduler.advanceUntilIdle()
        assertEquals(AutoRenewState(enabled = true, available = true), vm.switches.value["a"])
        assertEquals(listOf("a" to true), port.sets)
    }

    @Test
    fun a_service_that_cannot_renew_is_never_switched() = runTest {
        val port = FakePort()
        val vm = AutoRenewViewModel(port, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        vm.load(listOf("b"))
        testScheduler.advanceUntilIdle()
        vm.set("b", true)
        vm.set("unknown", true)
        testScheduler.advanceUntilIdle()
        assertTrue(port.sets.isEmpty())
    }

    @Test
    fun the_last_result_comes_through() = runTest {
        val port = FakePort()
        port.server["a"] = AutoRenewResponse(true, true, "insufficient_funds", "2026-09-29T10:00:00Z")
        val vm = AutoRenewViewModel(port, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        vm.load(listOf("a"))
        testScheduler.advanceUntilIdle()
        assertEquals("insufficient_funds", vm.switches.value["a"]?.lastResult)
    }
}
