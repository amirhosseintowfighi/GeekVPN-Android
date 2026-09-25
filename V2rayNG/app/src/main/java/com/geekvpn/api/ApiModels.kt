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
