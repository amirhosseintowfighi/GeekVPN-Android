package com.geekvpn.account

import com.geekvpn.api.ApiException
import com.geekvpn.api.RefundOffer
import com.v2ray.ang.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ServiceOwnerTest {
    private class FakePort : OwnerPort {
        val renames = mutableListOf<Pair<String, String?>>()
        val transfers = mutableListOf<Pair<String, Long>>()
        val refunds = mutableListOf<String>()
        var failWith: IOException? = null
        var offer = RefundOffer(true, 150_000, null, null, 48, "2026-11-28T10:00:00Z", null)

        override suspend fun rename(subscriptionId: String, name: String?) {
            failWith?.let { throw it }
            renames += subscriptionId to name
        }

        override suspend fun transfer(subscriptionId: String, recipient: Long) {
            failWith?.let { throw it }
            transfers += subscriptionId to recipient
        }

        override suspend fun refundOffer(subscriptionId: String) = offer

        override suspend fun refund(subscriptionId: String): Pair<Long?, String?> {
            failWith?.let { throw it }
            refunds += subscriptionId
            return 150_000L to "سرویس حذف شد"
        }
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(port: OwnerPort) =
        ServiceOwnerViewModel(port, { _, _ -> }, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))

    @Test
    fun a_name_is_squeezed_and_a_lone_dash_or_blank_clears_it() {
        assertEquals("گوشی من", OwnerInput.name("  گوشی    من "))
        assertNull(OwnerInput.name("-"))
        assertNull(OwnerInput.name(" - "))
        assertNull(OwnerInput.name("   "))
        assertTrue(OwnerInput.nameTooLong("a".repeat(33)))
        assertEquals(false, OwnerInput.nameTooLong("a".repeat(16) + "      " + "b".repeat(15)))
    }

    @Test
    fun a_telegram_id_is_read_in_persian_arabic_or_latin_digits_and_nothing_else() {
        assertEquals(123456789L, OwnerInput.telegramId(" ۱۲۳۴۵۶۷۸۹ "))
        assertEquals(42L, OwnerInput.telegramId("٤٢"))
        assertEquals(987L, OwnerInput.telegramId("987"))
        assertNull(OwnerInput.telegramId("@someone"))
        assertNull(OwnerInput.telegramId("-5"))
        assertNull(OwnerInput.telegramId("12 34"))
        assertNull(OwnerInput.telegramId("0"))
        assertNull(OwnerInput.telegramId(""))
        assertNull(OwnerInput.telegramId("99999999999999999999"))
    }

    @Test
    fun a_known_refusal_gets_the_apps_sentence_and_an_unknown_one_the_servers() {
        assertEquals(R.string.geek_owner_transfer_no_user, ownerFailure(ApiException(422, "x", code = "transfer_recipient_not_found", messageFa = "…")).res)
        assertEquals(R.string.geek_owner_err_disabled, ownerFailure(ApiException(409, "x", code = "feature_disabled", messageFa = "یه جای کار لنگید")).res)
        assertEquals(OwnerText("پیام سرور"), ownerFailure(ApiException(409, "x", code = "something_new", messageFa = "پیام سرور")))
        assertEquals(R.string.geek_owner_err_generic, ownerFailure(ApiException(500, "x")).res)
        assertEquals(R.string.geek_owner_err_network, ownerFailure(ApiException(null, "offline")).res)
        assertEquals(R.string.geek_owner_err_network, ownerFailure(IOException("reset")).res)
    }

    @Test
    fun a_refund_offer_that_says_no_explains_why() {
        val offer = RefundOffer(false, null, "refund_window_closed", "مهلت تموم شده", 48, null, null)
        assertEquals(R.string.geek_owner_refund_window_closed, refundReason(offer).res)
    }

    @Test
    fun renaming_sends_the_cleaned_name_and_closes_the_dialog() = runTest {
        val port = FakePort()
        val vm = viewModel(port)
        vm.openRename("s1", "تانل", "قدیمی")
        vm.rename("  کار   خانه ")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("s1" to "کار خانه"), port.renames)
        assertNull(vm.uiState.value.dialog)
        assertEquals(R.string.geek_owner_rename_done, vm.done.first().message.res)
    }

    @Test
    fun a_name_too_long_is_refused_without_asking_the_server() = runTest {
        val port = FakePort()
        val vm = viewModel(port)
        vm.openRename("s1", "تانل", null)
        vm.rename("x".repeat(40))
        testScheduler.advanceUntilIdle()
        assertTrue(port.renames.isEmpty())
        assertEquals(R.string.geek_owner_name_too_long, vm.uiState.value.error?.res)
    }

    @Test
    fun a_refused_transfer_keeps_the_dialog_open_with_the_reason() = runTest {
        val port = FakePort().apply { failWith = ApiException(422, "x", code = "transfer_to_self") }
        val vm = viewModel(port)
        vm.openTransfer("s1", "تانل")
        vm.transfer("۱۲۳")
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.dialog is OwnerDialog.Transfer)
        assertEquals(R.string.geek_owner_transfer_self, vm.uiState.value.error?.res)
        assertEquals(false, vm.uiState.value.busy)
    }

    @Test
    fun a_transfer_goes_to_the_typed_id() = runTest {
        val port = FakePort()
        val vm = viewModel(port)
        vm.openTransfer("s1", "تانل")
        vm.transfer("not a number")
        testScheduler.advanceUntilIdle()
        assertTrue(port.transfers.isEmpty())
        vm.transfer("۵۵۵")
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("s1" to 555L), port.transfers)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun a_refund_asks_first_and_runs_only_when_eligible() = runTest {
        val port = FakePort().apply { offer = RefundOffer(false, null, "refund_service_used", null, 48, null, null) }
        val vm = viewModel(port)
        vm.openRefund("s1", "تانل")
        testScheduler.advanceUntilIdle()
        assertEquals(false, (vm.uiState.value.dialog as OwnerDialog.Refund).offer?.eligible)
        vm.refund()
        testScheduler.advanceUntilIdle()
        assertTrue(port.refunds.isEmpty())

        port.offer = RefundOffer(true, 150_000, null, null, 48, null, null)
        vm.openRefund("s1", "تانل")
        testScheduler.advanceUntilIdle()
        vm.refund()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("s1"), port.refunds)
        val done = vm.done.first()
        assertEquals("سرویس حذف شد", done.message.text)
        assertTrue(done.refreshWallet)
    }
}
