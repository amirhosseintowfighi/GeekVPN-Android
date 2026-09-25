package com.geekvpn.shop

import com.geekvpn.api.CouponPreview
import com.geekvpn.api.CouponRequest
import com.geekvpn.api.GatewayPlanRequest
import com.geekvpn.api.PaymentMethodOption
import com.geekvpn.api.PaymentStart
import com.geekvpn.api.PaymentView
import com.geekvpn.api.PendingPayment
import com.geekvpn.api.PlanRequest
import com.geekvpn.api.Quote
import com.geekvpn.api.Storefront
import com.geekvpn.api.TopupRequest
import com.geekvpn.api.TrialClaim
import com.geekvpn.api.TrialOffer
import com.geekvpn.api.WalletCheckout
import com.geekvpn.api.WalletSnapshot
import com.geekvpn.api.WalletTransactions

/**
 * The shop's calls to the backend (implemented by `GeekApi`), apart so the
 * shop's flows can be tested without HTTP. Every call throws `ApiException`.
 */
interface ShopApi {
    suspend fun storefront(): Storefront
    suspend fun quote(request: PlanRequest): Quote
    suspend fun previewCoupon(request: CouponRequest): CouponPreview
    suspend fun paymentMethods(): List<PaymentMethodOption>
    suspend fun checkoutWallet(request: PlanRequest): WalletCheckout
    suspend fun checkoutCard(request: PlanRequest): PaymentStart
    suspend fun checkoutGateway(request: GatewayPlanRequest): PaymentStart
    suspend fun topup(request: TopupRequest): PaymentStart
    suspend fun wallet(): WalletSnapshot
    suspend fun walletTransactions(pageSize: Int): WalletTransactions
    suspend fun pendingPayments(): List<PaymentView>
    suspend fun uploadReceipt(paymentId: String, image: ByteArray, contentType: String): PendingPayment
    suspend fun trialOffer(): TrialOffer
    suspend fun claimTrial(): TrialClaim
}

/** How the shop's server-side keys read: the wallet is offered by the app, card by the server. */
object PaymentKeys {
    const val WALLET = "wallet"
    const val CARD = "card"

    /** The server has no crypto gateway and the app no screen for one. */
    const val CRYPTO = "crypto"
}

/** Whole tomans the wallet accepts in one top-up; the backend's `MIN_TOPUP` and `MAX_TOPUP`. */
object TopupLimits {
    const val MIN = 50_000L
    const val MAX = 50_000_000L
    val PRESETS = listOf(50_000L, 100_000L, 200_000L, 500_000L)

    /**
     * The custom amount field: Persian, Arabic and Latin digits, with any
     * separators the keyboard or a paste brings along. Null when it holds no
     * number or one too long to be an amount.
     */
    fun parse(text: String): Long? {
        val digits = buildString {
            text.forEach { ch ->
                val value = Character.digit(ch, 10)
                if (value >= 0) append(value)
            }
        }
        if (digits.isEmpty() || digits.length > 12) return null
        return digits.toLong()
    }
}
