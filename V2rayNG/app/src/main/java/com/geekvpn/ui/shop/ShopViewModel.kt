package com.geekvpn.ui.shop

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geekvpn.GeekGraph
import com.geekvpn.api.ApiException
import com.geekvpn.api.CouponRequest
import com.geekvpn.api.GatewayPlanRequest
import com.geekvpn.api.PaymentMethodOption
import com.geekvpn.api.PaymentStart
import com.geekvpn.api.PaymentView
import com.geekvpn.api.PlanRequest
import com.geekvpn.api.Quote
import com.geekvpn.api.StorePlan
import com.geekvpn.api.Storefront
import com.geekvpn.api.TopupRequest
import com.geekvpn.api.TrialOffer
import com.geekvpn.api.WalletTransaction
import com.geekvpn.auth.Session
import com.geekvpn.shop.PaymentKeys
import com.geekvpn.shop.ShopApi
import com.geekvpn.shop.ShopCatalog
import com.geekvpn.shop.Tier
import com.geekvpn.shop.TopupLimits
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** The card to transfer to, for a purchase or a top-up (Deposit.html). */
data class DepositInfo(
    val paymentId: String,
    /** Whole tomans, identifying digits included: the exact sum to send. */
    val amount: Long,
    val cardNumber: String,
    val cardHolder: String,
    val bank: String,
    val reviewNote: String,
)

/** What the pay sheet is paying for. */
sealed interface CheckoutPurpose {
    data object Purchase : CheckoutPurpose
    data class Topup(val amount: Long) : CheckoutPurpose
}

/** The sheets the shop opens over the tabs. */
sealed interface ShopSheet {
    /** How to pay: the wallet (purchases only) and the server's methods. */
    data class Checkout(val purpose: CheckoutPurpose, val total: Long) : ShopSheet
    data object Wallet : ShopSheet
    data class Deposit(val info: DepositInfo) : ShopSheet
}

data class CouponState(val code: String, val valid: Boolean, val message: String?)

/** The service a purchase extends; [title] is only for the banner. */
data class RenewTarget(val subscriptionId: String, val title: String)

data class WalletUiState(
    val transactions: List<WalletTransaction> = emptyList(),
    val pending: List<PaymentView> = emptyList(),
    val loading: Boolean = false,
)

data class ShopUiState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val tiers: List<Tier> = emptyList(),
    val tier: Tier? = null,
    val durations: List<Int> = emptyList(),
    val duration: Int? = null,
    val volumes: List<StorePlan> = emptyList(),
    val plan: StorePlan? = null,
    val quote: Quote? = null,
    val coupon: CouponState? = null,
    val renewing: RenewTarget? = null,
    val trial: TrialOffer? = null,
    val methods: List<PaymentMethodOption> = emptyList(),
    val balance: Long? = null,
    /** A payment or claim is being started; the buttons wait. */
    val busy: Boolean = false,
    val sheet: ShopSheet? = null,
    val wallet: WalletUiState = WalletUiState(),
    val uploading: Boolean = false,
) {
    /** What the customer pays now: the quote once it is in, the listed price before. */
    val total: Long? get() = quote?.total ?: plan?.price

    /** The struck-through price, when the total is below it. */
    val wasPrice: Long?
        get() {
            val total = total ?: return null
            val was = listOfNotNull(quote?.basePrice, plan?.compareAtPrice, quote?.compareAtPrice).maxOrNull()
            return was?.takeIf { it > total }
        }
}

sealed interface ShopEvent {
    data class Message(@param:StringRes val text: Int) : ShopEvent

    /** The server's own sentence (a refused coupon, "not enough in the wallet"). */
    data class Text(val text: String) : ShopEvent

    /** An online gateway's page. */
    data class OpenUrl(val url: String) : ShopEvent

    /** A service was bought or claimed: show the services and fetch them. */
    data object Purchased : ShopEvent

    /** Money arrived in the wallet or a receipt went in: refresh what the account shows. */
    data object AccountChanged : ShopEvent
}

/** What the shop needs besides the API; faked in tests. */
interface ShopEnvironment {
    val api: ShopApi
    fun signedIn(): Boolean

    /** Whose shop this is: the signed-in user's id, null for a guest or nobody. */
    fun accountKey(): String?
    fun balance(): Long?
    fun saveBalance(tomans: Long)
    fun logFailure(message: String, e: Throwable)
}

object GeekShopEnvironment : ShopEnvironment {
    override val api: ShopApi get() = GeekGraph.api
    override fun signedIn() = GeekGraph.session.session.value is Session.SignedIn
    override fun accountKey() = (GeekGraph.session.session.value as? Session.SignedIn)?.user?.id
    override fun balance() = GeekGraph.accountStore.balance.value
    override fun saveBalance(tomans: Long) = GeekGraph.accountStore.saveBalance(tomans)
    override fun logFailure(message: String, e: Throwable) {
        LogUtil.w(AppConfig.TAG, message, e)
    }
}

/**
 * Shop.html, Wallet.html and Deposit.html. The storefront, prices and every
 * payment come from the backend; this keeps the pickers, the quote for the
 * current choice, and which sheet is open.
 */
class ShopViewModel : ViewModel {
    private val env: ShopEnvironment

    /** What `viewModels()` calls. */
    constructor() : super() {
        env = GeekShopEnvironment
    }

    /** For tests: a fake environment on a test dispatcher instead of the Android main thread. */
    constructor(env: ShopEnvironment, scope: CoroutineScope) : super(scope) {
        this.env = env
    }

    private val state = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = state.asStateFlow()

    private val eventChannel = Channel<ShopEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var storefront: Storefront? = null
    private var loadJob: Job? = null
    private var quoteJob: Job? = null
    private var walletJob: Job? = null

    /** A gateway page was opened; whatever happens there, refresh when the customer is back. */
    private var awaitingGateway = false

    /** The account the shop below was loaded for. */
    private var loadedFor: String? = null

    /**
     * The shop tab was shown, or the session changed. Loads once per account:
     * another account (or none) starts from nothing, since its prices, wallet
     * and trial are not the last one's.
     */
    fun load(force: Boolean = false) {
        val signedIn = env.signedIn() && ensureAccount() != null
        state.update { it.copy(signedIn = signedIn, balance = env.balance()) }
        if (!signedIn) return
        if (!force && (storefront != null || loadJob?.isActive == true)) return
        loadJob = viewModelScope.launch {
            state.update { it.copy(loading = true, loadFailed = false) }
            try {
                val front = env.api.storefront()
                storefront = front
                front.walletBalance?.let { rememberBalance(it) }
                applyStorefront(front)
            } catch (e: ApiException) {
                env.logFailure("Shop: storefront failed (status=${e.status})", e)
                state.update { it.copy(loadFailed = true) }
            } finally {
                state.update { it.copy(loading = false) }
            }
            // Extras: the shop still works without them.
            val methods = attempt("payment-methods") { env.api.paymentMethods() }.orEmpty()
            val trial = attempt("trial") { env.api.trialOffer() }
            state.update { it.copy(methods = methods, trial = trial) }
        }
    }

    fun selectTier(tier: Tier) {
        val front = storefront ?: return
        if (tier == state.value.tier) return
        val plans = ShopCatalog.plansOf(ShopCatalog.productFor(front, tier))
        showPlan(tier, plans, ShopCatalog.defaultPlan(plans))
    }

    fun selectDuration(days: Int) {
        val front = storefront ?: return
        val tier = state.value.tier ?: return
        val plans = ShopCatalog.plansOf(ShopCatalog.productFor(front, tier))
        showPlan(tier, plans, ShopCatalog.withDuration(plans, state.value.plan, days))
    }

    fun selectPlan(planId: String) {
        val plan = state.value.volumes.firstOrNull { it.planId == planId } ?: return
        state.update { it.copy(plan = plan) }
        requote()
    }

    /** "تمدید" on a service: the next purchase extends it, in its own tier. */
    fun renew(subscriptionId: String, title: String, tier: String?) {
        state.update { it.copy(renewing = RenewTarget(subscriptionId, title)) }
        Tier.of(tier)?.let { wanted ->
            if (wanted in state.value.tiers) selectTier(wanted)
        }
        requote()
    }

    fun cancelRenew() {
        state.update { it.copy(renewing = null) }
        requote()
    }

    fun applyCoupon(code: String) {
        val plan = state.value.plan?.planId ?: return
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val preview = try {
                env.api.previewCoupon(CouponRequest(plan, trimmed))
            } catch (e: ApiException) {
                report("Shop: coupon preview failed", e)
                return@launch
            }
            val valid = preview.isValid == true
            state.update { it.copy(coupon = CouponState(preview.code ?: trimmed, valid, preview.messageFa)) }
            if (valid) requote()
        }
    }

    fun clearCoupon() {
        state.update { it.copy(coupon = null) }
        requote()
    }

    /** "پرداخت": choose how to pay for the current plan. */
    fun pay() {
        val current = state.value
        val total = current.total ?: return
        if (current.plan == null || current.busy) return
        state.update { it.copy(sheet = ShopSheet.Checkout(CheckoutPurpose.Purchase, total)) }
    }

    /** "شارژ": choose how to put [amount] tomans into the wallet. */
    fun topup(amount: Long) {
        when {
            amount < TopupLimits.MIN -> eventChannel.trySend(ShopEvent.Message(R.string.geek_wallet_err_min))
            amount > TopupLimits.MAX -> eventChannel.trySend(ShopEvent.Message(R.string.geek_wallet_err_max))
            else -> state.update { it.copy(sheet = ShopSheet.Checkout(CheckoutPurpose.Topup(amount), amount)) }
        }
    }

    /** The methods the pay sheet offers for [purpose], in order: wallet, card, gateways. */
    fun optionsFor(purpose: CheckoutPurpose): List<PaymentMethodOption> {
        val server = state.value.methods.filter { !it.key.isNullOrBlank() && it.key != PaymentKeys.CRYPTO && it.key != PaymentKeys.WALLET }
        val card = server.filter { it.key == PaymentKeys.CARD }
        val gateways = server.filter { it.key != PaymentKeys.CARD }
        val wallet = if (purpose is CheckoutPurpose.Purchase) listOf(PaymentMethodOption(PaymentKeys.WALLET, null)) else emptyList()
        // A server that did not answer still takes card transfers; that is how every shop starts.
        val cards = card.ifEmpty { if (server.isEmpty()) listOf(PaymentMethodOption(PaymentKeys.CARD, null)) else emptyList() }
        return wallet + cards + gateways
    }

    fun choose(method: String) {
        val sheet = state.value.sheet as? ShopSheet.Checkout ?: return
        if (state.value.busy) return
        viewModelScope.launch {
            state.update { it.copy(busy = true) }
            try {
                when (val purpose = sheet.purpose) {
                    CheckoutPurpose.Purchase -> buy(method)
                    is CheckoutPurpose.Topup -> startTopup(purpose.amount, method)
                }
            } catch (e: ApiException) {
                report("Shop: payment with $method failed", e)
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun buy(method: String) {
        val current = state.value
        val planId = current.plan?.planId ?: return
        val coupon = current.coupon?.takeIf { it.valid }?.code
        val renews = current.renewing?.subscriptionId
        when (method) {
            PaymentKeys.WALLET -> {
                env.api.checkoutWallet(PlanRequest(planId, coupon, renews))
                state.update { it.copy(sheet = null, coupon = null, renewing = null) }
                refreshBalance()
                eventChannel.send(ShopEvent.Message(if (renews != null) R.string.geek_shop_renewed else R.string.geek_shop_bought))
                eventChannel.send(ShopEvent.Purchased)
            }
            PaymentKeys.CARD -> openPayment(env.api.checkoutCard(PlanRequest(planId, coupon, renews)))
            else -> openPayment(env.api.checkoutGateway(GatewayPlanRequest(planId, method, coupon, renews)))
        }
    }

    private suspend fun startTopup(amount: Long, method: String) {
        openPayment(env.api.topup(TopupRequest(amount, method)))
    }

    private suspend fun openPayment(start: PaymentStart) {
        when (start.kind) {
            PaymentStart.Kind.Card -> {
                val info = depositOf(start)
                state.update { it.copy(sheet = if (info != null) ShopSheet.Deposit(info) else null) }
                if (info == null) eventChannel.send(ShopEvent.Message(R.string.geek_shop_err_generic))
            }
            PaymentStart.Kind.Gateway -> {
                state.update { it.copy(sheet = null) }
                val url = start.url
                if (!url.isNullOrBlank()) {
                    awaitingGateway = true
                    eventChannel.send(ShopEvent.OpenUrl(url))
                } else {
                    start.bodyFa?.let { eventChannel.send(ShopEvent.Text(it)) }
                }
            }
            PaymentStart.Kind.Unknown -> {
                state.update { it.copy(sheet = null) }
                eventChannel.send(ShopEvent.Message(R.string.geek_shop_err_generic))
            }
        }
    }

    fun openWallet() {
        ensureAccount()
        state.update { it.copy(sheet = ShopSheet.Wallet, balance = env.balance()) }
        refreshWallet()
    }

    /** A pending card payment in the wallet list: show its card again to finish it. */
    fun openPending(payment: PaymentView) {
        val card = payment.card ?: return
        val id = payment.paymentId ?: return
        if (payment.state != STATE_AWAITING_PROOF) return
        state.update {
            it.copy(
                sheet = ShopSheet.Deposit(
                    DepositInfo(
                        paymentId = id,
                        amount = payment.amount ?: 0,
                        cardNumber = card.cardNumber.orEmpty(),
                        cardHolder = card.cardHolderFa.orEmpty(),
                        bank = card.bankFa.orEmpty(),
                        reviewNote = card.reviewSlaFa.orEmpty(),
                    )
                )
            )
        }
    }

    fun closeSheet() {
        if (state.value.busy || state.value.uploading) return
        state.update { it.copy(sheet = null) }
    }

    /**
     * "واریز کردم": the photo the customer picked. [read] loads and shrinks it
     * off the main thread; null when the image could not be read.
     */
    fun uploadReceipt(read: suspend () -> ByteArray?) {
        val deposit = (state.value.sheet as? ShopSheet.Deposit)?.info ?: return
        if (state.value.uploading) return
        viewModelScope.launch {
            state.update { it.copy(uploading = true) }
            try {
                val image = read()
                if (image == null) {
                    eventChannel.send(ShopEvent.Message(R.string.geek_deposit_err_image))
                    return@launch
                }
                env.api.uploadReceipt(deposit.paymentId, image, RECEIPT_TYPE)
                state.update { it.copy(sheet = null) }
                eventChannel.send(ShopEvent.Message(R.string.geek_deposit_sent))
                eventChannel.send(ShopEvent.AccountChanged)
            } catch (e: ApiException) {
                report("Shop: receipt upload failed", e)
            } catch (e: IOException) {
                env.logFailure("Shop: receipt could not be read", e)
                eventChannel.send(ShopEvent.Message(R.string.geek_deposit_err_image))
            } finally {
                state.update { it.copy(uploading = false) }
            }
        }
    }

    fun claimTrial() {
        if (state.value.busy || state.value.trial?.available != true) return
        viewModelScope.launch {
            state.update { it.copy(busy = true) }
            try {
                val claim = env.api.claimTrial()
                state.update { it.copy(trial = it.trial?.copy(available = false)) }
                val delivered = claim.subscriptionIds.orEmpty().isNotEmpty()
                eventChannel.send(ShopEvent.Message(if (delivered) R.string.geek_trial_ready else R.string.geek_trial_pending))
                eventChannel.send(ShopEvent.Purchased)
            } catch (e: ApiException) {
                if (e.status == 409) state.update { it.copy(trial = it.trial?.copy(available = false)) }
                report("Shop: trial claim failed", e)
            } finally {
                state.update { it.copy(busy = false) }
            }
        }
    }

    /**
     * Back in the app: from the gateway's page by its link ([result] from the
     * link, only a hint) or by the back button. The server is the only word on
     * whether money arrived, so both just refresh.
     */
    fun onReturn(result: String?) {
        if (!awaitingGateway && result == null) return
        awaitingGateway = false
        // A guest has no account for a payment to have gone into.
        if (!env.signedIn()) return
        viewModelScope.launch {
            refreshBalance()
            if (state.value.sheet == ShopSheet.Wallet) refreshWallet()
            val message = when (result) {
                RESULT_OK -> R.string.geek_pay_result_ok
                RESULT_FAILED -> R.string.geek_pay_result_failed
                RESULT_PENDING, RESULT_UNKNOWN -> R.string.geek_pay_result_pending
                else -> null
            }
            message?.let { eventChannel.send(ShopEvent.Message(it)) }
            eventChannel.send(ShopEvent.AccountChanged)
        }
    }

    private fun ensureAccount(): String? {
        val account = env.accountKey()
        if (account != loadedFor) {
            reset()
            loadedFor = account
        }
        return account
    }

    /** Forget the last account's shop. */
    private fun reset() {
        loadJob?.cancel()
        quoteJob?.cancel()
        walletJob?.cancel()
        storefront = null
        awaitingGateway = false
        state.value = ShopUiState()
    }

    private fun applyStorefront(front: Storefront) {
        val tiers = ShopCatalog.tiers(front)
        val tier = state.value.tier?.takeIf { it in tiers } ?: ShopCatalog.defaultTier(front)
        state.update { it.copy(tiers = tiers) }
        if (tier == null) {
            state.update { it.copy(tier = null, durations = emptyList(), duration = null, volumes = emptyList(), plan = null, quote = null) }
            return
        }
        val plans = ShopCatalog.plansOf(ShopCatalog.productFor(front, tier))
        val keep = state.value.plan?.planId?.let { id -> plans.firstOrNull { it.planId == id } }
        showPlan(tier, plans, keep ?: ShopCatalog.defaultPlan(plans))
    }

    private fun showPlan(tier: Tier, plans: List<StorePlan>, plan: StorePlan?) {
        val duration = plan?.durationDays
        state.update {
            it.copy(
                tier = tier,
                durations = ShopCatalog.durations(plans),
                duration = duration,
                volumes = duration?.let { days -> ShopCatalog.volumes(plans, days) }.orEmpty(),
                plan = plan,
                // A coupon was checked against the old plan; it may not apply to this one.
                coupon = if (plan?.planId == it.plan?.planId) it.coupon else null,
                quote = null,
            )
        }
        requote()
    }

    private fun requote() {
        quoteJob?.cancel()
        val current = state.value
        val planId = current.plan?.planId ?: return
        val coupon = current.coupon?.takeIf { it.valid }?.code
        quoteJob = viewModelScope.launch {
            state.update { it.copy(quote = null) }
            val quote = attempt("quote") { env.api.quote(PlanRequest(planId, coupon, current.renewing?.subscriptionId)) }
            if (state.value.plan?.planId == planId) state.update { it.copy(quote = quote) }
        }
    }

    private fun refreshWallet() {
        if (walletJob?.isActive == true) return
        walletJob = viewModelScope.launch {
            state.update { it.copy(wallet = it.wallet.copy(loading = true)) }
            refreshBalance()
            val transactions = attempt("transactions") { env.api.walletTransactions(TRANSACTION_PAGE) }?.items
            val pending = attempt("pending payments") { env.api.pendingPayments() }
            state.update {
                it.copy(
                    wallet = WalletUiState(
                        transactions = transactions ?: it.wallet.transactions,
                        pending = pending ?: it.wallet.pending,
                        loading = false,
                    )
                )
            }
        }
    }

    private suspend fun refreshBalance() {
        attempt("wallet") { env.api.wallet() }?.balance?.let { rememberBalance(it) }
    }

    private fun rememberBalance(tomans: Long) {
        env.saveBalance(tomans)
        state.update { it.copy(balance = tomans) }
    }

    private suspend fun <T> attempt(what: String, call: suspend () -> T): T? = try {
        call()
    } catch (e: ApiException) {
        env.logFailure("Shop: $what failed (status=${e.status})", e)
        null
    }

    private suspend fun report(what: String, e: ApiException) {
        env.logFailure("$what (status=${e.status}, code=${e.code})", e)
        val text = e.messageFa
        eventChannel.send(
            when {
                e.isNetwork -> ShopEvent.Message(R.string.geek_login_err_network)
                text != null -> ShopEvent.Text(text)
                else -> ShopEvent.Message(R.string.geek_shop_err_generic)
            }
        )
    }

    private fun depositOf(start: PaymentStart): DepositInfo? {
        val payment = start.payment ?: return null
        val id = payment.paymentId ?: return null
        return DepositInfo(
            paymentId = id,
            amount = payment.amount ?: return null,
            cardNumber = start.cardNumber.orEmpty(),
            cardHolder = start.cardHolderFa.orEmpty(),
            bank = start.bankFa.orEmpty(),
            reviewNote = start.reviewSlaFa.orEmpty(),
        )
    }

    companion object {
        const val RECEIPT_TYPE = "image/jpeg"
        const val RESULT_OK = "ok"
        const val RESULT_PENDING = "pending"
        const val RESULT_FAILED = "failed"
        const val RESULT_UNKNOWN = "unknown"
        private const val STATE_AWAITING_PROOF = "awaiting_proof"
        private const val TRANSACTION_PAGE = 10
    }
}
