package com.geekvpn.api

import com.google.gson.Gson
import com.google.gson.JsonParseException
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
class ApiException(val status: Int?, message: String, cause: Throwable? = null) :
    IOException(message, cause) {
    val isNetwork: Boolean get() = status == null
}

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

/** The two sign-in calls, apart so the polling logic can be tested without HTTP. */
interface LinkApi {
    suspend fun startLink(request: LinkStartRequest): LinkStartResponse
    suspend fun pollLink(pollToken: String, wait: Boolean): LinkPollResponse
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
) : LinkApi {
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

    suspend fun subscriptions(): List<SubscriptionCard> =
        get(authorized, "/api/miniapp/subscriptions", object : TypeToken<List<SubscriptionCard>>() {})

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
                    throw ApiException(it.code, "HTTP ${it.code} on ${request.url.encodedPath}")
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
