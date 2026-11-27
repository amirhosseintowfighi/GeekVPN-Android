package com.geekvpn.ui.devices

import com.geekvpn.api.ApiException
import com.geekvpn.api.DeviceSession
import com.v2ray.ang.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DevicesViewModelTest {
    private fun device(id: String, current: Boolean = false) =
        DeviceSession(id, "Pixel $id", "android", "1.4.0", "2026-11-01T10:00:00Z", "2026-11-26T10:00:00Z", current)

    private class FakePort(var devices: List<DeviceSession>) : DevicesPort {
        val revoked = mutableListOf<String>()
        var signedOutHere = 0
        var failWith: IOException? = null

        override suspend fun list() = devices

        override suspend fun revoke(deviceId: String) {
            failWith?.let { throw it }
            revoked += deviceId
        }

        override fun signOutHere() {
            signedOutHere++
        }
    }

    private fun TestScope.viewModel(port: DevicesPort) =
        DevicesViewModel(port, { _, _ -> }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))

    @Test
    fun another_device_is_revoked_and_leaves_the_list() = runTest {
        val port = FakePort(listOf(device("a", current = true), device("b")))
        val vm = viewModel(port)
        testScheduler.advanceUntilIdle()
        vm.revoke(device("b"))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("b"), port.revoked)
        assertEquals(listOf("a"), vm.uiState.value.devices?.map { it.deviceId })
        assertEquals(DevicesEvent.Revoked, vm.events.first())
    }

    @Test
    fun this_phone_signs_out_the_ordinary_way_instead_of_revoking_itself() = runTest {
        val port = FakePort(listOf(device("a", current = true)))
        val vm = viewModel(port)
        testScheduler.advanceUntilIdle()
        vm.revoke(device("a", current = true))
        testScheduler.advanceUntilIdle()
        assertTrue(port.revoked.isEmpty())
        assertEquals(1, port.signedOutHere)
        assertEquals(DevicesEvent.SignedOutHere, vm.events.first())
    }

    @Test
    fun a_device_already_gone_leaves_the_list_and_says_so() = runTest {
        val port = FakePort(listOf(device("a", current = true), device("b"))).apply {
            failWith = ApiException(404, "x", code = "device_not_found")
        }
        val vm = viewModel(port)
        testScheduler.advanceUntilIdle()
        vm.revoke(device("b"))
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("a"), vm.uiState.value.devices?.map { it.deviceId })
        val event = vm.events.first() as DevicesEvent.Failed
        assertEquals(R.string.geek_owner_err_not_found, event.text.res)
    }

    @Test
    fun a_failed_list_can_be_retried() = runTest {
        val port = object : DevicesPort {
            var calls = 0
            override suspend fun list(): List<DeviceSession> {
                calls++
                if (calls == 1) throw ApiException(null, "offline")
                return emptyList()
            }
            override suspend fun revoke(deviceId: String) = Unit
            override fun signOutHere() = Unit
        }
        val vm = viewModel(port)
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.failed)
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<DeviceSession>(), vm.uiState.value.devices)
    }

    @Test
    fun the_platform_word_reads_as_a_name() {
        assertEquals("Android", platformLabel("android"))
        assertEquals("tizen", platformLabel("tizen"))
    }
}
