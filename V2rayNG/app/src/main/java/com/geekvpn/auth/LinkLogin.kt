package com.geekvpn.auth

import com.geekvpn.api.ApiException
import com.geekvpn.api.AppUser
import com.geekvpn.api.LinkApi
import com.geekvpn.api.LinkStartRequest
import com.geekvpn.api.PasswordLoginRequest
import com.geekvpn.api.TokenPair
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.delay
import java.net.URI
import java.net.URISyntaxException

/** A started sign-in: the link to open in Telegram and how long it is good for. */
data class LinkStarted(val deepLink: String, val pollToken: String, val expiresInSeconds: Int)

sealed interface LinkOutcome {
    data class Approved(val tokens: TokenPair, val user: AppUser) : LinkOutcome
    data object Denied : LinkOutcome
    data object Expired : LinkOutcome
}

/**
 * Sign-in by approving the app from inside the bot (backend `AppLinkLogin`):
 * [start] gets a `t.me` link, the customer taps "تأیید" in Telegram, and
 * [await] long-polls until the server has an answer.
 */
class LinkLogin(
    private val api: LinkApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val logFailure: (String, Throwable) -> Unit = { message, e -> LogUtil.w(AppConfig.TAG, message, e) },
) {
    suspend fun start(device: LinkStartRequest): LinkStarted {
        val response = api.startLink(device)
        val deepLink = response.deepLink
        val pollToken = response.pollToken
        if (deepLink.isNullOrEmpty() || pollToken.isNullOrEmpty()) {
            throw ApiException(null, "link/start answered without a link")
        }
        return LinkStarted(deepLink, pollToken, response.expiresIn ?: DEFAULT_TTL_SECONDS)
    }

    /**
     * Waits for the customer's decision. Network failures are retried with
     * [PollBackoff] until [deadlineMillis] (wall clock), after which the
     * request is over server-side anyway.
     */
    suspend fun await(pollToken: String, deadlineMillis: Long): LinkOutcome {
        var failures = 0
        while (true) {
            val askedAt = now()
            try {
                val answer = api.pollLink(pollToken, wait = true)
                failures = 0
                when (answer.status) {
                    "approved" -> {
                        val tokens = answer.tokens
                        val user = answer.user
                        if (tokens != null && user != null) return LinkOutcome.Approved(tokens, user)
                        // Approved but the pair already went to an earlier poll.
                        return LinkOutcome.Expired
                    }
                    "denied" -> return LinkOutcome.Denied
                    "pending" -> Unit
                    else -> return LinkOutcome.Expired
                }
                // The server normally holds a pending poll ~25 s. A proxy that
                // answers at once must not turn this into a tight loop.
                val took = now() - askedAt
                if (took < MIN_POLL_INTERVAL_MS) delay(MIN_POLL_INTERVAL_MS - took)
            } catch (e: ApiException) {
                if (e.status == 404) return LinkOutcome.Expired
                failures++
                logFailure("LinkLogin: poll failed (status=${e.status}, attempt $failures)", e)
                delay(PollBackoff.delayMillis(failures, rateLimited = e.status == 429))
            }
            if (now() > deadlineMillis) return LinkOutcome.Expired
        }
    }

    /**
     * Sign in with the username and password set in the bot. [LinkOutcome.Approved]
     * or an [ApiException]: 401 wrong username or password, 429 too many tries.
     */
    suspend fun password(request: PasswordLoginRequest): LinkOutcome.Approved {
        val response = api.passwordLogin(request)
        val tokens = response.tokens
        val user = response.user
        if (tokens == null || tokens.accessToken.isNullOrEmpty() || tokens.refreshToken.isNullOrEmpty() || user == null) {
            throw ApiException(null, "password login answered without a session")
        }
        return LinkOutcome.Approved(tokens, user)
    }

    private companion object {
        const val DEFAULT_TTL_SECONDS = 300
        const val MIN_POLL_INTERVAL_MS = 1_000L
    }
}

/** How long to wait before polling again after [failures] failures in a row. */
object PollBackoff {
    private const val BASE_MS = 1_000L
    private const val MAX_MS = 15_000L
    private const val RATE_LIMITED_MS = 10_000L

    fun delayMillis(failures: Int, rateLimited: Boolean = false): Long {
        if (rateLimited) return maxOf(RATE_LIMITED_MS, backoff(failures))
        return backoff(failures)
    }

    private fun backoff(failures: Int): Long {
        if (failures <= 0) return 0
        val shift = (failures - 1).coerceAtMost(4)
        return (BASE_MS shl shift).coerceAtMost(MAX_MS)
    }
}

/** Telegram link forms. */
object TelegramLink {
    /**
     * `https://t.me/<bot>?start=<param>` as `tg://resolve?domain=<bot>&start=<param>`,
     * which opens the Telegram app directly instead of a browser page first.
     * Null when [link] is not a `t.me` bot link.
     */
    fun appUri(link: String): String? {
        val uri = try {
            URI(link)
        } catch (_: URISyntaxException) {
            return null
        }
        if (uri.scheme != "https" || uri.host != "t.me") return null
        val bot = uri.path?.trim('/')?.takeIf { BOT_NAME.matches(it) } ?: return null
        val start = uri.rawQuery
            ?.split('&')
            ?.firstOrNull { it.startsWith("start=") }
            ?.removePrefix("start=")
            ?.takeIf { START_PARAM.matches(it) }
        return if (start != null) "tg://resolve?domain=$bot&start=$start" else "tg://resolve?domain=$bot"
    }

    private val BOT_NAME = Regex("[A-Za-z0-9_]{5,32}")

    // Telegram's own limit on a start parameter.
    private val START_PARAM = Regex("[A-Za-z0-9_-]{1,64}")
}
