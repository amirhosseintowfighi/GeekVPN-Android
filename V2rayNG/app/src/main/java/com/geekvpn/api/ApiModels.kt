package com.geekvpn.api

/*
 * Wire shapes of the GeekVPN backend (GeekVPNBot repo), camelCase as it sends
 * them. Every field is nullable: Gson fills objects without running Kotlin
 * constructors, so a field the server leaves out arrives as null whatever its
 * declared type, and callers must decide what a missing value means.
 *
 * The backend's `tests/integration/test_app_auth_api.py::APP_CALLS` lists every
 * route used here, so a route renamed there fails its build first.
 */

data class LinkStartRequest(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
)

data class LinkStartResponse(
    val requestId: String?,
    val pollToken: String?,
    /** `https://t.me/<bot>?start=applogin_<code>`. */
    val deepLink: String?,
    val expiresIn: Int?,
)

data class LinkPollRequest(
    val pollToken: String,
    /** True: the server holds the request up to 25 s waiting for a decision. */
    val wait: Boolean,
)

data class LinkPollResponse(
    /** pending | approved | denied | expired. */
    val status: String?,
    val tokens: TokenPair?,
    val user: AppUser?,
)

data class TokenPair(
    val accessToken: String?,
    val refreshToken: String?,
    val tokenType: String?,
    val accessExpiresAt: String?,
    val refreshExpiresAt: String?,
    val sessionId: String?,
)

data class RefreshRequest(val refreshToken: String)

/** Username and password the customer set in the bot (backend `AppPasswordLogin`). */
data class PasswordLoginRequest(
    val username: String,
    val password: String,
    val deviceName: String,
    val platform: String,
    val appVersion: String,
)

data class PasswordLoginResponse(
    val tokens: TokenPair?,
    val user: AppUser?,
)

data class AppUser(
    val id: String?,
    val telegramId: Long?,
    val displayName: String?,
    val username: String?,
    val language: String?,
    val referralCode: String?,
    val photoUrl: String?,
)

/** One service, as `/api/miniapp/subscriptions` lists it. */
data class SubscriptionCard(
    val subscriptionId: String?,
    val productNameFa: String?,
    val planNameFa: String?,
    /** active | expired | exhausted | suspended | revoked. */
    val state: String?,
    val expiresAt: String?,
    val quotaGib: Int?,
    val usedGib: Double?,
    val subscriptionUrl: String?,
    val remoteUsername: String?,
    /** direct | tunnel | elite; null for a service adopted from a link. */
    val tier: String?,
)

/** `/api/miniapp/wallet`. Amounts are whole tomans. */
data class WalletSnapshot(
    val balance: Long?,
)

/* -- shop (phase 6) ---------------------------------------------------- */

/** `/api/miniapp/storefront`: categories, their products, their plans. Prices in tomans. */
data class Storefront(
    val categories: List<StoreCategory>?,
    val walletBalance: Long?,
    val isFirstPurchase: Boolean?,
)

data class StoreCategory(
    val categoryId: String?,
    val nameFa: String?,
    val products: List<StoreProduct>?,
)

data class StoreProduct(
    val productId: String?,
    /** direct | tunnel | elite. */
    val tier: String?,
    val nameFa: String?,
    val taglineFa: String?,
    val isFeatured: Boolean?,
    val plans: List<StorePlan>?,
)

data class StorePlan(
    val planId: String?,
    val productId: String?,
    val nameFa: String?,
    val durationDays: Int?,
    val price: Long?,
    /** Only where a real discount exists. */
    val compareAtPrice: Long?,
    /** Null for an unlimited plan. */
    val quotaGib: Int?,
    val deviceLimit: Int?,
    val badgeFa: String?,
    val isFeatured: Boolean?,
)

data class PlanRequest(
    val planId: String,
    val couponCode: String? = null,
    /** Set when the purchase extends a service the customer already has. */
    val renewsSubscriptionId: String? = null,
)

data class GatewayPlanRequest(
    val planId: String,
    val gatewayKey: String,
    val couponCode: String? = null,
    val renewsSubscriptionId: String? = null,
)

/** `/quote`: the price this customer pays today. */
data class Quote(
    val planId: String?,
    val basePrice: Long?,
    val total: Long?,
    val totalDiscount: Long?,
    val discountPercent: Int?,
    val compareAtPrice: Long?,
    val couponCode: String?,
)

data class CouponRequest(val planId: String, val code: String)

/** `/coupon/preview`. A refusal is data: [isValid] false with the reason in [messageFa]. */
data class CouponPreview(
    val code: String?,
    val isValid: Boolean?,
    val discount: Long?,
    val totalAfter: Long?,
    val messageFa: String?,
)

/** One entry of `/payment-methods`: `card` or an online gateway's key. */
data class PaymentMethodOption(
    val key: String?,
    val labelFa: String?,
)

data class WalletCheckout(val subscriptionId: String?)

/** A payment the customer still owes proof for, or is waiting on review of. */
data class PendingPayment(
    val paymentId: String?,
    val reference: String?,
    val amount: Long?,
    /** wallet | card | crypto | gateway. */
    val method: String?,
    /** awaiting_proof | pending_review | approved | rejected | cancelled. */
    val state: String?,
    val createdAt: String?,
)

/**
 * What starting a payment answers. The server sends one of three shapes (card
 * details, a gateway link, crypto) and they share no field, so this holds all
 * of them and [kind] tells which arrived.
 */
data class PaymentStart(
    val cardNumber: String?,
    val cardHolderFa: String?,
    val bankFa: String?,
    val reviewSlaFa: String?,
    val payment: PendingPayment?,
    /** Gateway: the bank page. */
    val url: String?,
    /** Gateway: instructions to read instead of (or with) the link. */
    val bodyFa: String?,
    val paymentId: String?,
) {
    enum class Kind { Card, Gateway, Unknown }

    val kind: Kind
        get() = when {
            !cardNumber.isNullOrBlank() && payment?.paymentId != null -> Kind.Card
            !url.isNullOrBlank() || !bodyFa.isNullOrBlank() -> Kind.Gateway
            else -> Kind.Unknown
        }
}

data class TopupRequest(val amount: Long, val method: String)

data class WalletTransaction(
    val transactionId: String?,
    /** topup | purchase | cashback | referral | refund | adjustment. */
    val kind: String?,
    val amount: Long?,
    val createdAt: String?,
    val descriptionFa: String?,
)

data class WalletTransactions(
    val items: List<WalletTransaction>?,
    val total: Int?,
)

/** `/payments/pending`: what the customer still owes proof for, with the card to pay. */
data class PaymentView(
    val paymentId: String?,
    val reference: String?,
    val amount: Long?,
    val method: String?,
    val state: String?,
    val createdAt: String?,
    val expiresAt: String?,
    val card: PaymentCard?,
)

data class PaymentCard(
    val cardNumber: String?,
    val cardHolderFa: String?,
    val bankFa: String?,
    val reviewSlaFa: String?,
)

/** `/trial`. */
data class TrialOffer(
    val available: Boolean?,
    val trafficMib: Int?,
    val durationDays: Int?,
)

data class TrialClaim(
    val subscriptionIds: List<String>?,
    /** Services still being built; they arrive with a later sync. */
    val pending: Int?,
)
