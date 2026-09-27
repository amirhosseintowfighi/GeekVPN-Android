package com.geekvpn.ui.support

import com.geekvpn.api.ApiException
import com.geekvpn.support.ReportFacts
import com.v2ray.ang.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportViewModelTest {
    private class FakePorts(override val signedIn: Boolean, private val failWith: ApiException? = null) : ReportPorts {
        val sent = mutableListOf<Pair<String, String>>()
        override fun facts() = ReportFacts(
            appVersion = "1.3.0", versionCode = 1, android = "14", sdk = 34, device = "Pixel", network = "wifi",
            mode = "vpn", route = "smart", autoServer = true, connected = false, core = "Xray", config = null,
            lastFailure = null, lastFailureMinutesAgo = null,
        )
        override fun log() = listOf("E/com.geekvpn.app: dial 104.18.32.47:443 failed")
        override suspend fun send(topic: String, message: String): String? {
            failWith?.let { throw it }
            sent += topic to message
            return "SUP-1405-000007"
        }
    }

    private fun TestScope.viewModel(ports: FakePorts): Pair<ReportViewModel, MutableList<ReportEvent>> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = ReportViewModel(ports, dispatcher, CoroutineScope(SupervisorJob() + dispatcher))
        val events = mutableListOf<ReportEvent>()
        CoroutineScope(SupervisorJob() + dispatcher).launch { vm.events.toList(events) }
        testScheduler.advanceUntilIdle()
        return vm to events
    }

    @Test
    fun the_technical_part_is_ready_to_show_and_already_redacted() = runTest {
        val (vm, _) = viewModel(FakePorts(signedIn = true))
        val technical = vm.uiState.value.technical!!
        assertTrue(technical.contains("dial <ip>:443 failed"))
        assertFalse(technical.contains("104.18.32.47"))
    }

    @Test
    fun a_description_is_required() = runTest {
        val ports = FakePorts(signedIn = true)
        val (vm, events) = viewModel(ports)
        vm.describe("بد")
        vm.send()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.tooShort)
        assertTrue(ports.sent.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun signed_in_it_opens_a_connection_ticket() = runTest {
        val ports = FakePorts(signedIn = true)
        val (vm, events) = viewModel(ports)
        vm.describe("از دیشب وصل نمی‌شود")
        vm.send()
        testScheduler.advanceUntilIdle()
        val (topic, message) = ports.sent.single()
        assertEquals(ReportViewModel.TOPIC, topic)
        assertTrue(message.startsWith("از دیشب وصل نمی‌شود"))
        assertEquals(listOf(ReportEvent.Sent("SUP-1405-000007")), events)
        assertFalse(vm.uiState.value.sending)
    }

    @Test
    fun signed_out_the_report_is_copied_instead() = runTest {
        val ports = FakePorts(signedIn = false)
        val (vm, events) = viewModel(ports)
        vm.describe("از دیشب وصل نمی‌شود")
        vm.send()
        testScheduler.advanceUntilIdle()
        assertTrue(ports.sent.isEmpty())
        assertTrue((events.single() as ReportEvent.Copy).text.contains("--- report ---"))
    }

    @Test
    fun a_network_failure_says_so_and_keeps_the_text() = runTest {
        val (vm, events) = viewModel(FakePorts(signedIn = true, failWith = ApiException(null, "offline")))
        vm.describe("از دیشب وصل نمی‌شود")
        vm.send()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(ReportEvent.Failed(R.string.geek_report_err_network, null)), events)
        assertEquals("از دیشب وصل نمی‌شود", vm.uiState.value.description)
    }
}
