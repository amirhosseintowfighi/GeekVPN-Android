package com.geekvpn.ui.shop

import com.geekvpn.api.ApiException
import com.geekvpn.api.CouponPreview
import com.geekvpn.api.CouponRequest
import com.geekvpn.api.GatewayPlanRequest
import com.geekvpn.api.PaymentMethodOption
import com.geekvpn.api.PaymentStart
import com.geekvpn.api.PaymentView
import com.geekvpn.api.PendingPayment
import com.geekvpn.api.PlanRequest
import com.geekvpn.api.Quote
import com.geekvpn.api.StoreCategory
import com.geekvpn.api.StorePlan
import com.geekvpn.api.StoreProduct
import com.geekvpn.api.Storefront
import com.geekvpn.api.TopupRequest
import com.geekvpn.api.TrialClaim
import com.geekvpn.api.TrialOffer
import com.geekvpn.api.WalletCheckout
import com.geekvpn.api.WalletSnapshot
import com.geekvpn.api.WalletTransactions
import com.geekvpn.shop.ShopApi
import com.geekvpn.shop.Tier
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

class ShopViewModelTest {

    private class FakeApi : ShopApi {
        var front = Storefront(
            listOf(
                StoreCategory(
                    "c",
                    "VPN",
                    listOf(
                        StoreProduct(
                            "tunnel",
                            "tunnel",
                            "Tunnel",
                            null,
                            true,
                            listOf(
                                StorePlan("t-30-20", "tunnel", null, 30, 150_000, null, 20, 2, null, false),
                                StorePlan("t-30-40", "tunnel", null, 30, 200_000, 250_000, 40, 2, null, true),
                                StorePlan("t-60-40", "tunnel", null, 60, 380_000, null, 40, 2, null, false),
                            ),
                        ),
                        StoreProduct(
                            "direct",
                            "direct",
                            "Direct",
                            null,
                            false,
                            listOf(StorePlan("d-30-50", "direct", null, 30, 90_000, null, 50, 2, null, false)),
                        ),
                    ),
                ),
            ),
            walletBalance = 300_000,
            isFirstPurchase = true,
        )
        var failure: ApiException? = null
        val quotes = mutableListOf<PlanRequest>()
        val walletBuys = mutableListOf<PlanRequest>()
        val gatewayBuys = mutableListOf<GatewayPlanRequest>()
        val topups = mutableListOf<TopupRequest>()
        val uploads = mutableListOf<Pair<String, Int>>()
        var coupon = CouponPreview("OFF20", true, 40_000, 160_000, "ok")
        var trialClaim = TrialClaim(listOf("s1", "s2"), 0)

        private fun <T> answer(value: T): T {
            failure?.let { throw it }
            return value
        }

        override suspend fun storefront() = answer(front)
        override suspend fun quote(request: PlanRequest): Quote {
            quotes += request
            val price = front.categories!!.flatMap { it.products!! }.flatMap { it.plans!! }.first { it.planId == request.planId }.price!!
            val total = if (request.couponCode != null) price - 40_000 else price
            return answer(Quote(request.planId, price, total, price - total, 0, null, request.couponCode))
        }
        override suspend fun previewCoupon(request: CouponRequest) = answer(coupon)
        override suspend fun paymentMethods() = answer(listOf(PaymentMethodOption("card", "Card"), PaymentMethodOption("zarinpal", "ZarinPal")))
        override suspend fun checkoutWallet(request: PlanRequest): WalletCheckout {
            walletBuys += request
            return answer(WalletCheckout("s9"))
        }
        override suspend fun checkoutCard(request: PlanRequest) = answer(
            PaymentStart("6037991122334455", "Ali", "Melli", "15 min", PendingPayment("p1", "r", 200_073, "card", "awaiting_proof", null), null, null, null)
        )
        override suspend fun checkoutGateway(request: GatewayPlanRequest): PaymentStart {
            gatewayBuys += request
            return answer(PaymentStart(null, null, null, null, null, "https://bank.example/pay", null, "p2"))
        }
        override suspend fun topup(request: TopupRequest): PaymentStart {
            topups += request
            return answer(
                PaymentStart("6037991122334455", "Ali", "Melli", null, PendingPayment("p3", "r", request.amount + 41, "card", "awaiting_proof", null), null, null, null)
            )
        }
        override suspend fun wallet() = answer(WalletSnapshot(99_000))
        override suspend fun walletTransactions(pageSize: Int) = answer(WalletTransactions(emptyList(), 0))
        override suspend fun pendingPayments() = answer(emptyList<PaymentView>())
        override suspend fun uploadReceipt(paymentId: String, image: ByteArray, contentType: String): PendingPayment {
            uploads += paymentId to image.size
            return answer(PendingPayment(paymentId, "r", 1, "card", "pending_review", null))
        }
        override suspend fun trialOffer() = answer(TrialOffer(true, 50, 2))
        override suspend fun claimTrial() = answer(trialClaim)
    }

    private class FakeEnv(override val api: FakeApi = FakeApi()) : ShopEnvironment {
        var signedIn = true
        var saved: Long? = null
        var account = "u1"
        override fun signedIn() = signedIn
        override fun accountKey() = if (signedIn) account else null
        override fun balance() = saved
        override fun saveBalance(tomans: Long) {
            saved = tomans
        }
        override fun logFailure(message: String, e: Throwable) = Unit
    }

    /** The ViewModel and an event collector on the test scheduler (see LoginViewModelTest). */
    private class Harness(scope: TestScope, val env: FakeEnv = FakeEnv()) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val viewModel = ShopViewModel(env, CoroutineScope(SupervisorJob() + dispatcher))
        val events = mutableListOf<ShopEvent>()
        val api get() = env.api

        init {
            CoroutineScope(SupervisorJob() + dispatcher).launch { viewModel.events.toList(events) }
        }
    }

    private fun TestScope.loaded(): Harness {
        val h = Harness(this)
        h.viewModel.load()
        testScheduler.advanceUntilIdle()
        return h
    }

    @Test
    fun loading_opens_on_the_featured_plan_with_its_quote() = runTest {
        val h = loaded()
        val state = h.viewModel.uiState.value

        assertEquals(listOf(Tier.Direct, Tier.Tunnel), state.tiers)
        assertEquals(Tier.Tunnel, state.tier)
        assertEquals(listOf(30, 60), state.durations)
        assertEquals("t-30-40", state.plan?.planId)
        assertEquals(200_000L, state.total)
        assertEquals(250_000L, state.wasPrice)
        assertEquals(300_000L, h.env.saved)
        assertEquals(TrialOffer(true, 50, 2), state.trial)
    }

    @Test
    fun a_guest_gets_no_storefront_call() = runTest {
        val h = Harness(this)
        h.env.signedIn = false
        h.viewModel.load()
        testScheduler.advanceUntilIdle()

        assertFalse(h.viewModel.uiState.value.signedIn)
        assertTrue(h.viewModel.uiState.value.tiers.isEmpty())
    }

    @Test
    fun a_failed_load_says_so() = runTest {
        val h = Harness(this)
        h.api.failure = ApiException(null, "offline")
        h.viewModel.load()
        testScheduler.advanceUntilIdle()

        assertTrue(h.viewModel.uiState.value.loadFailed)
    }

    @Test
    fun a_longer_duration_keeps_the_volume() = runTest {
        val h = loaded()
        h.viewModel.selectDuration(60)
        testScheduler.advanceUntilIdle()

        assertEquals("t-60-40", h.viewModel.uiState.value.plan?.planId)
        assertEquals(380_000L, h.viewModel.uiState.value.total)
    }

    @Test
    fun switching_tier_shows_that_products_plans() = runTest {
        val h = loaded()
        h.viewModel.selectTier(Tier.Direct)
        testScheduler.advanceUntilIdle()

        assertEquals("d-30-50", h.viewModel.uiState.value.plan?.planId)
    }

    @Test
    fun a_valid_coupon_is_priced_in() = runTest {
        val h = loaded()
        h.viewModel.applyCoupon(" off20 ")
        testScheduler.advanceUntilIdle()

        assertEquals(CouponState("OFF20", true, "ok"), h.viewModel.uiState.value.coupon)
        assertEquals("OFF20", h.api.quotes.last().couponCode)
        assertEquals(160_000L, h.viewModel.uiState.value.total)
    }

    @Test
    fun a_refused_coupon_is_shown_and_not_priced() = runTest {
        val h = loaded()
        h.api.coupon = CouponPreview("BAD", false, 0, 0, "expired")
        h.viewModel.applyCoupon("BAD")
        testScheduler.advanceUntilIdle()

        assertEquals(CouponState("BAD", false, "expired"), h.viewModel.uiState.value.coupon)
        assertNull(h.api.quotes.last().couponCode)
    }

    @Test
    fun paying_from_the_wallet_buys_and_shows_the_services() = runTest {
        val h = loaded()
        h.viewModel.pay()
        assertEquals(ShopSheet.Checkout(CheckoutPurpose.Purchase, 200_000), h.viewModel.uiState.value.sheet)
        assertEquals(
            listOf("wallet", "card", "zarinpal"),
            h.viewModel.optionsFor(CheckoutPurpose.Purchase).map { it.key },
        )

        h.viewModel.choose("wallet")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(PlanRequest("t-30-40", null, null)), h.api.walletBuys)
        assertNull(h.viewModel.uiState.value.sheet)
        assertEquals(listOf(ShopEvent.Message(R.string.geek_shop_bought), ShopEvent.Purchased), h.events)
        assertEquals(99_000L, h.env.saved)
    }

    @Test
    fun a_refusal_shows_the_servers_own_words() = runTest {
        val h = loaded()
        h.viewModel.pay()
        h.api.failure = ApiException(409, "HTTP 409", code = "insufficient_funds", messageFa = "موجودی کافی نیست.")
        h.viewModel.choose("wallet")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf<ShopEvent>(ShopEvent.Text("موجودی کافی نیست.")), h.events)
        assertFalse(h.viewModel.uiState.value.busy)
    }

    @Test
    fun card_to_card_opens_the_deposit_card() = runTest {
        val h = loaded()
        h.viewModel.pay()
        h.viewModel.choose("card")
        testScheduler.advanceUntilIdle()

        val sheet = h.viewModel.uiState.value.sheet as ShopSheet.Deposit
        assertEquals(DepositInfo("p1", 200_073, "6037991122334455", "Ali", "Melli", "15 min"), sheet.info)
    }

    @Test
    fun a_gateway_opens_its_page_and_the_return_refreshes() = runTest {
        val h = loaded()
        h.viewModel.pay()
        h.viewModel.choose("zarinpal")
        testScheduler.advanceUntilIdle()

        assertEquals("zarinpal", h.api.gatewayBuys.single().gatewayKey)
        assertEquals(listOf<ShopEvent>(ShopEvent.OpenUrl("https://bank.example/pay")), h.events)

        h.viewModel.onReturn(ShopViewModel.RESULT_OK)
        testScheduler.advanceUntilIdle()
        assertEquals(ShopEvent.Message(R.string.geek_pay_result_ok), h.events[1])
        assertEquals(99_000L, h.env.saved)
    }

    @Test
    fun coming_back_without_a_gateway_open_does_nothing() = runTest {
        val h = loaded()
        h.viewModel.onReturn(null)
        testScheduler.advanceUntilIdle()

        assertTrue(h.events.isEmpty())
    }

    @Test
    fun a_top_up_below_the_minimum_is_refused_before_asking_the_server() = runTest {
        val h = loaded()
        h.viewModel.topup(10_000)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf<ShopEvent>(ShopEvent.Message(R.string.geek_wallet_err_min)), h.events)
        assertTrue(h.api.topups.isEmpty())
    }

    @Test
    fun a_top_up_by_card_offers_no_wallet_and_opens_the_deposit_card() = runTest {
        val h = loaded()
        h.viewModel.topup(100_000)
        assertEquals(listOf("card", "zarinpal"), h.viewModel.optionsFor(CheckoutPurpose.Topup(100_000)).map { it.key })
        h.viewModel.choose("card")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(TopupRequest(100_000, "card")), h.api.topups)
        assertEquals(100_041L, (h.viewModel.uiState.value.sheet as ShopSheet.Deposit).info.amount)
    }

    @Test
    fun the_receipt_is_sent_for_the_open_payment() = runTest {
        val h = loaded()
        h.viewModel.pay()
        h.viewModel.choose("card")
        testScheduler.advanceUntilIdle()

        h.viewModel.uploadReceipt { ByteArray(1234) }
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("p1" to 1234), h.api.uploads)
        assertNull(h.viewModel.uiState.value.sheet)
        assertTrue(ShopEvent.Message(R.string.geek_deposit_sent) in h.events)
    }

    @Test
    fun an_unreadable_image_is_not_sent() = runTest {
        val h = loaded()
        h.viewModel.pay()
        h.viewModel.choose("card")
        testScheduler.advanceUntilIdle()

        h.viewModel.uploadReceipt { null }
        testScheduler.advanceUntilIdle()

        assertTrue(h.api.uploads.isEmpty())
        assertTrue(h.viewModel.uiState.value.sheet is ShopSheet.Deposit)
        assertEquals(ShopEvent.Message(R.string.geek_deposit_err_image), h.events.last())
    }

    @Test
    fun the_trial_is_claimed_once() = runTest {
        val h = loaded()
        h.viewModel.claimTrial()
        testScheduler.advanceUntilIdle()

        assertEquals(false, h.viewModel.uiState.value.trial?.available)
        assertEquals(listOf(ShopEvent.Message(R.string.geek_trial_ready), ShopEvent.Purchased), h.events)
    }

    @Test
    fun a_renewal_is_sent_with_the_service_it_extends() = runTest {
        val h = loaded()
        h.viewModel.renew("sub-7", "Tunnel", "direct")
        testScheduler.advanceUntilIdle()
        assertEquals(Tier.Direct, h.viewModel.uiState.value.tier)

        h.viewModel.pay()
        h.viewModel.choose("wallet")
        testScheduler.advanceUntilIdle()

        assertEquals("sub-7", h.api.walletBuys.single().renewsSubscriptionId)
        assertEquals(ShopEvent.Message(R.string.geek_shop_renewed), h.events.first())
        assertNull(h.viewModel.uiState.value.renewing)
    }

    @Test
    fun another_account_starts_from_a_fresh_shop() = runTest {
        val h = loaded()
        h.viewModel.renew("sub-7", "Tunnel", "direct")
        h.env.account = "u2"
        h.viewModel.load()
        testScheduler.advanceUntilIdle()

        assertNull(h.viewModel.uiState.value.renewing)
        assertEquals(Tier.Tunnel, h.viewModel.uiState.value.tier)
    }
}
