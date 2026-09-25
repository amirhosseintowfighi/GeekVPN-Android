package com.geekvpn.api

import com.geekvpn.shop.ShopApi
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A call that did not produce a usable answer. [status] is the HTTP status, or
 * null when the server was never reached (or answered with something that is
 * not the promised JSON).
 */
class ApiException(
    val status: Int?,
    message: String,
    cause: Throwable? = null,
    /** The problem's machine code (`title`), e.g. `insufficient_funds`. */
    val code: String? = null,
    /** The server's own Persian sentence for the failure, when it sent one. */
    val messageFa: String? = null,
) : IOException(message, cause) {
    val isNetwork: Boolean get() = status == null
}

/** The part of a problem+json body the app shows. */
private data class Problem(val title: String?, @SerializedName("message_fa") val messageFa: String?)

/**
 * Where the client keeps its tokens. Implemented by `SessionStore`; an
 * interface so the refresh logic can be tested without Android storage.
 */
interface TokenHolder {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun onRefreshed(tokens: TokenPair)

    /** The session is gone server-side (logged out, device disconnected in the bot). */
    fun onSessionLost()
}

/** The sign-in calls, apart so the sign-in logic can be tested without HTTP. */
interface LinkApi {
    suspend fun startLink(request: LinkStartRequest): LinkStartResponse
    suspend fun pollLink(pollToken: String, wait: Boolean): LinkPollResponse

    /** 401 for any wrong username or password; the server does not say which. */
    suspend fun passwordLogin(request: PasswordLoginRequest): PasswordLoginResponse
}

/**
 * The GeekVPN backend over plain OkHttp + Gson (both already v2rayNG
 * dependencies). Calls run on [Dispatchers.IO].
 *
 * Only the main app process may use this. Refresh tokens rotate on every use
 * and the backend treats a reused one as theft, so two processes refreshing
 * the same token would sign the customer out; one process with one lock
 * cannot race itself. The app's own package is excluded from the VPN, so these
 * requests always go out directly, whether or not a tunnel is up.
 */
class GeekApi(
    private val baseUrl: String,
    private val tokens: TokenHolder,
    private val gson: Gson = Gson(),
    baseClient: OkHttpClient = OkHttpClient(),
) : LinkApi, ShopApi {
    private val refreshLock = Any()

    /** Without the authenticator: used for sign-in and for refresh itself. */
    private val anonymous: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // A waiting poll is held up to 25 s by the server.
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val authorized: OkHttpClient = anonymous.newBuilder()
        .addInterceptor { chain ->
            val token = tokens.accessToken()
            val request = if (token != null) {
                chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        .authenticator(RefreshAuthenticator())
        .build()

    override suspend fun startLink(request: LinkStartRequest): LinkStartResponse =
        post(anonymous, "/api/app/auth/link/start", request)

    override suspend fun pollLink(pollToken: String, wait: Boolean): LinkPollResponse =
        post(anonymous, "/api/app/auth/link/poll", LinkPollRequest(pollToken, wait))

    override suspend fun passwordLogin(request: PasswordLoginRequest): PasswordLoginResponse =
        post(anonymous, "/api/app/auth/password", request)

    /** Ends this device's session on the server. Local sign-out does not wait for it. */
    suspend fun logout() {
        post<Any>(authorized, "/api/v1/auth/logout", emptyMap<String, String>())
    }

    override suspend fun wallet(): WalletSnapshot =
        get(authorized, "/api/miniapp/wallet", object : TypeToken<WalletSnapshot>() {})

    suspend fun subscriptions(): List<SubscriptionCard> =
        get(authorized, "/api/miniapp/subscriptions", object : TypeToken<List<SubscriptionCard>>() {})

    override suspend fun storefront(): Storefront =
        get(authorized, "/api/miniapp/storefront", object : TypeToken<Storefront>() {})

    override suspend fun quote(request: PlanRequest): Quote = post(authorized, "/api/miniapp/quote", request)

    override suspend fun previewCoupon(request: CouponRequest): CouponPreview =
        post(authorized, "/api/miniapp/coupon/preview", request)

    override suspend fun paymentMethods(): List<PaymentMethodOption> =
        get(authorized, "/api/miniapp/payment-methods", object : TypeToken<List<PaymentMethodOption>>() {})

    override suspend fun checkoutWallet(request: PlanRequest): WalletCheckout =
        post(authorized, "/api/miniapp/checkout/wallet", request)

    override suspend fun checkoutCard(request: PlanRequest): PaymentStart =
        post(authorized, "/api/miniapp/checkout/card", request)

    override suspend fun checkoutGateway(request: GatewayPlanRequest): PaymentStart =
        post(authorized, "/api/miniapp/checkout/gateway", request)

    override suspend fun topup(request: TopupRequest): PaymentStart = post(authorized, "/api/miniapp/wallet/topup", request)

    override suspend fun walletTransactions(pageSize: Int): WalletTransactions =
        get(
            authorized,
            "/api/miniapp/wallet/transactions?page=1&page_size=$pageSize",
            object : TypeToken<WalletTransactions>() {},
        )

    override suspend fun pendingPayments(): List<PaymentView> =
        get(authorized, "/api/miniapp/payments/pending", object : TypeToken<List<PaymentView>>() {})

    /** The receipt image itself is the body; the server hands it to the operator through the bot. */
    override suspend fun uploadReceipt(paymentId: String, image: ByteArray, contentType: String): PendingPayment =
        execute(
            authorized,
            request("/api/miniapp/payments/$paymentId/receipt-photo")
                .post(image.toRequestBody(contentType.toMediaType()))
                .build(),
            object : TypeToken<PendingPayment>() {},
        )

    override suspend fun trialOffer(): TrialOffer = get(authorized, "/api/miniapp/trial", object : TypeToken<TrialOffer>() {})

    override suspend fun claimTrial(): TrialClaim = post(authorized, "/api/miniapp/trial", emptyMap<String, String>())

    private suspend inline fun <reified T> post(client: OkHttpClient, path: String, body: Any): T =
        execute(client, request(path).post(gson.toJson(body).toRequestBody(JSON)).build(), object : TypeToken<T>() {})

    private suspend fun <T> get(client: OkHttpClient, path: String, type: TypeToken<T>): T =
        execute(client, request(path).get().build(), type)

    private fun request(path: String) = Request.Builder()
        .url(baseUrl.trimEnd('/') + path)
        .header("Accept", "application/json")

    private suspend fun <T> execute(client: OkHttpClient, request: Request, type: TypeToken<T>): T =
        withContext(Dispatchers.IO) {
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw ApiException(null, "network failure on ${request.url.encodedPath}", e)
            }
            response.use {
                if (!it.isSuccessful) {
                    val problem = try {
                        gson.fromJson(it.body.string(), Problem::class.java)
                    } catch (_: JsonParseException) {
                        null
                    } catch (_: IOException) {
                        null
                    }
                    throw ApiException(
                        it.code,
                        "HTTP ${it.code} on ${request.url.encodedPath}",
                        code = problem?.title,
                        messageFa = problem?.messageFa?.takeIf { text -> text.isNotBlank() },
                    )
                }
                val text = it.body.string()
                try {
                    gson.fromJson(text, type.type)
                        ?: throw ApiException(null, "empty body on ${request.url.encodedPath}")
                } catch (e: JsonParseException) {
                    // A captive portal or a proxy's HTML error page, not the API.
                    throw ApiException(null, "unexpected body on ${request.url.encodedPath}", e)
                }
            }
        }

    /**
     * On a 401, trades the refresh token for a new pair once and retries.
     * Requests that fail together wait on one lock, and whoever comes second
     * sees the token already changed and just retries with it.
     */
    private inner class RefreshAuthenticator : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (response.priorResponse != null) return null // already retried once
            val failedWith = response.request.header("Authorization")?.removePrefix("Bearer ")

            synchronized(refreshLock) {
                val current = tokens.accessToken() ?: return null
                if (current != failedWith) {
                    return response.request.newBuilder().header("Authorization", "Bearer $current").build()
                }
                val refresh = tokens.refreshToken() ?: return null
                val renewed = when (val outcome = refreshBlocking(refresh)) {
                    is RefreshOutcome.Renewed -> outcome.tokens
                    RefreshOutcome.Rejected -> {
                        tokens.onSessionLost()
                        return null
                    }
                    // Offline: keep the session and let this call fail as a network error.
                    RefreshOutcome.Unreachable -> return null
                }
                tokens.onRefreshed(renewed)
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${renewed.accessToken}")
                    .build()
            }
        }
    }

    private sealed interface RefreshOutcome {
        data class Renewed(val tokens: TokenPair) : RefreshOutcome
        data object Rejected : RefreshOutcome
        data object Unreachable : RefreshOutcome
    }

    private fun refreshBlocking(refreshToken: String): RefreshOutcome {
        val request = request("/api/v1/auth/refresh")
            .post(gson.toJson(RefreshRequest(refreshToken)).toRequestBody(JSON))
            .build()
        return try {
            anonymous.newCall(request).execute().use {
                when {
                    it.isSuccessful -> {
                        val pair: TokenPair? = gson.fromJson(it.body.string(), TokenPair::class.java)
                        if (pair != null && !pair.accessToken.isNullOrEmpty() && !pair.refreshToken.isNullOrEmpty()) {
                            RefreshOutcome.Renewed(pair)
                        } else {
                            RefreshOutcome.Unreachable
                        }
                    }
                    // 401: expired, revoked or already used. The session cannot come back.
                    it.code == 401 -> RefreshOutcome.Rejected
                    else -> RefreshOutcome.Unreachable
                }
            }
        } catch (_: IOException) {
            RefreshOutcome.Unreachable
        } catch (_: JsonParseException) {
            RefreshOutcome.Unreachable
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
