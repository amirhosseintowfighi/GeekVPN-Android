package com.geekvpn.ui.support

import com.geekvpn.api.ApiException
import com.geekvpn.api.TicketCard
import com.geekvpn.api.TicketMessage
import com.geekvpn.support.TicketStatus
import com.geekvpn.support.TicketTopic
import com.geekvpn.support.Tickets
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketsViewModelTest {
    private class FakePorts(override val signedIn: Boolean = true) : TicketsPorts {
        var tickets = listOf(
            TicketCard("aaaa0000", "SUP-1", "اتصال", "closed", "2026-09-20T10:00:00Z", "2026-09-21T10:00:00Z"),
            TicketCard("bbbb0000", "SUP-2", "پرداخت", "waiting", "2026-09-26T10:00:00Z", "2026-09-27T09:00:00Z"),
        )
        val threads = mutableMapOf("bbbb0000" to mutableListOf(TicketMessage("m1", true, "سلام", "2026-09-27T09:00:00Z")))
        val opened = mutableListOf<Triple<String, String, String>>()
        var failWith: ApiException? = null
        var listCalls = 0

        override suspend fun list(): List<TicketCard> {
            listCalls++
            failWith?.let { throw it }
            return tickets
        }
        override suspend fun thread(ticketId: String): List<TicketMessage> {
            failWith?.let { throw it }
            return threads[ticketId].orEmpty().toList()
        }
        override suspend fun reply(ticketId: String, message: String) {
            failWith?.let { throw it }
            threads.getOrPut(ticketId) { mutableListOf() } += TicketMessage("r", false, message, "2026-09-28T09:00:00Z")
        }
        override suspend fun open(topic: String, subject: String, message: String): String? {
            failWith?.let { throw it }
            opened += Triple(topic, subject, message)
            return "SUP-3"
        }
        override fun logFailure(operation: String, error: ApiException) = Unit
    }

    private fun TestScope.viewModel(ports: FakePorts): Pair<TicketsViewModel, MutableList<TicketsEvent>> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = TicketsViewModel(ports, CoroutineScope(SupervisorJob() + dispatcher))
        val events = mutableListOf<TicketsEvent>()
        CoroutineScope(SupervisorJob() + dispatcher).launch { vm.events.toList(events) }
        testScheduler.advanceUntilIdle()
        return vm to events
    }

    @Test
    fun the_list_shows_the_thread_that_moved_last_first() = runTest {
        val (vm, _) = viewModel(FakePorts())
        assertEquals(listOf("SUP-2", "SUP-1"), vm.uiState.value.tickets!!.map { it.reference })
    }

    @Test
    fun signed_out_nothing_is_asked_of_the_server() = runTest {
        val ports = FakePorts(signedIn = false)
        val (vm, _) = viewModel(ports)
        assertEquals(0, ports.listCalls)
        assertNull(vm.uiState.value.tickets)
    }

    @Test
    fun opening_a_ticket_loads_its_thread_and_back_returns_to_the_list() = runTest {
        val (vm, _) = viewModel(FakePorts())
        vm.open("bbbb0000")
        testScheduler.advanceUntilIdle()
        assertEquals("SUP-2", vm.uiState.value.openTicket?.reference)
        assertEquals(listOf("سلام"), vm.uiState.value.messages!!.map { it.bodyFa })
        assertTrue(vm.back())
        assertEquals(TicketsPage.List, vm.uiState.value.page)
        assertFalse(vm.back())
    }

    @Test
    fun a_reply_is_sent_trimmed_and_the_thread_reloads() = runTest {
        val ports = FakePorts()
        val (vm, _) = viewModel(ports)
        vm.open("bbbb0000")
        vm.setReply("  کوتاه ")
        vm.sendReply()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.tooShort)
        assertEquals(1, ports.threads.getValue("bbbb0000").size)

        vm.setReply("  هنوز وصل نمی‌شود، کانفیگ را هم عوض کردم ")
        vm.sendReply()
        testScheduler.advanceUntilIdle()
        assertEquals("هنوز وصل نمی‌شود، کانفیگ را هم عوض کردم", vm.uiState.value.messages!!.last().bodyFa)
        assertEquals("", vm.uiState.value.reply)
        assertFalse(vm.uiState.value.sending)
    }

    @Test
    fun a_new_ticket_needs_a_subject_and_goes_in_with_its_topic() = runTest {
        val ports = FakePorts()
        val (vm, events) = viewModel(ports)
        vm.startNew()
        vm.setTopic(TicketTopic.Speed)
        vm.setBody("شب‌ها سرعت خیلی پایین است")
        vm.submitNew()
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.tooShort)
        assertTrue(ports.opened.isEmpty())

        vm.setSubject(" سرعت شب ")
        vm.submitNew()
        testScheduler.advanceUntilIdle()
        assertEquals(Triple("speed", "سرعت شب", "شب‌ها سرعت خیلی پایین است"), ports.opened.single())
        assertEquals(listOf<TicketsEvent>(TicketsEvent.Opened("SUP-3")), events)
        assertEquals(TicketsPage.List, vm.uiState.value.page)
        assertEquals("", vm.uiState.value.body)
    }

    @Test
    fun a_failed_send_keeps_the_text_and_says_why() = runTest {
        val ports = FakePorts()
        val (vm, events) = viewModel(ports)
        vm.open("bbbb0000")
        testScheduler.advanceUntilIdle()
        ports.failWith = ApiException(null, "offline")
        vm.setReply("این پیام به سرور نمی‌رسد")
        vm.sendReply()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf<TicketsEvent>(TicketsEvent.Failed(R.string.geek_tickets_err_network)), events)
        assertEquals("این پیام به سرور نمی‌رسد", vm.uiState.value.reply)
    }

    @Test
    fun a_failed_refresh_keeps_the_list_on_screen() = runTest {
        val ports = FakePorts()
        val (vm, _) = viewModel(ports)
        ports.failWith = ApiException(502, "bad gateway")
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals(2, vm.uiState.value.tickets!!.size)
        assertTrue(vm.uiState.value.listFailed)
    }

    @Test
    fun states_and_limits_match_the_bot() {
        assertEquals(TicketStatus.Waiting, TicketStatus.of("waiting"))
        assertEquals(TicketStatus.Open, TicketStatus.of("something-new"))
        assertFalse(TicketStatus.Closed.canReply)
        assertTrue(TicketStatus.Answered.canReply)
        assertFalse(Tickets.messageOk("   ۱۲۳۴۵۶۷۸۹ "))
        assertTrue(Tickets.messageOk("۱۲۳۴۵۶۷۸۹۰"))
        // A row without an id cannot be opened, so it is not listed.
        assertEquals(1, Tickets.sorted(listOf(TicketCard(null, "x"), TicketCard("cccc0000", "y"))).size)
    }
}
