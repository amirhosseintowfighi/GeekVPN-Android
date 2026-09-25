package com.geekvpn.auth

import com.geekvpn.api.ApiException
import com.geekvpn.api.AppUser
import com.geekvpn.api.LinkApi
import com.geekvpn.api.LinkPollResponse
import com.geekvpn.api.LinkStartRequest
import com.geekvpn.api.LinkStartResponse
import com.geekvpn.api.TokenPair
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkLoginTest {

    private val device = LinkStartRequest("d1", "Pixel 6", "android", "0.1.0")
    private val tokens = TokenPair("access-token", "refresh-token", "Bearer", null, null, "s1")
    private val user = AppUser("u1", 555, "Amir", null, "fa", "R555", null)

    /** Answers polls from [script] in order; each entry is a response or an exception to throw. */
    private class FakeApi(
        private val start: LinkStartResponse = LinkStartResponse("r1", "poll-token", "https://t.me/GeekVPNBot?start=applogin_abc", 300),
        private val script: MutableList<Any> = mutableListOf(),
    ) : LinkApi {
        var polls = 0
        override suspend fun startLink(request: LinkStartRequest) = start
        override suspend fun pollLink(pollToken: String, wait: Boolean): LinkPollResponse {
            polls++
            val next = script.removeAt(0)
            if (next is Throwable) throw next
            return next as LinkPollResponse
        }
    }

    /**
     * Virtual milliseconds since the test started, from the scheduler's stable
     * `timeSource` (`TestScope.currentTime` is still experimental).
     */
    private fun TestScope.clock(): () -> Long {
        val start = testScheduler.timeSource.markNow()
        return { start.elapsedNow().inWholeMilliseconds }
    }

    private fun login(api: FakeApi, clock: () -> Long) = LinkLogin(api, now = clock, logFailure = { _, _ -> })

    private fun TestScope.login(api: FakeApi) = login(api, clock())

    private fun pending() = LinkPollResponse("pending", null, null)

    @Test
    fun start_returns_the_link_and_ttl() = runTest {
        val started = login(FakeApi()).start(device)
        assertEquals("https://t.me/GeekVPNBot?start=applogin_abc", started.deepLink)
        assertEquals("poll-token", started.pollToken)
        assertEquals(300, started.expiresInSeconds)
    }

    @Test(expected = ApiException::class)
    fun start_without_a_link_is_an_error() = runTest {
        login(FakeApi(start = LinkStartResponse("r1", "poll-token", null, 300))).start(device)
    }

    @Test
    fun start_without_a_ttl_assumes_five_minutes() = runTest {
        val started = login(FakeApi(start = LinkStartResponse("r1", "p", "https://t.me/GeekVPNBot?start=x", null))).start(device)
        assertEquals(300, started.expiresInSeconds)
    }

    @Test
    fun pending_then_approved_signs_in() = runTest {
        val api = FakeApi(script = mutableListOf(pending(), pending(), LinkPollResponse("approved", tokens, user)))
        val outcome = login(api).await("poll-token", deadlineMillis = 300_000)
        assertEquals(LinkOutcome.Approved(tokens, user), outcome)
        assertEquals(3, api.polls)
    }

    @Test
    fun an_immediate_pending_answer_is_not_polled_in_a_tight_loop() = runTest {
        val clock = clock()
        val api = FakeApi(script = mutableListOf(pending(), pending(), LinkPollResponse("denied", null, null)))
        login(api, clock).await("poll-token", deadlineMillis = 300_000)
        // Two pending answers that took no time: at least a second apart each.
        assertTrue(clock() >= 2_000)
    }

    @Test
    fun approved_without_tokens_reads_as_expired() = runTest {
        val api = FakeApi(script = mutableListOf(LinkPollResponse("approved", null, null)))
        assertEquals(LinkOutcome.Expired, login(api).await("poll-token", 300_000))
    }

    @Test
    fun denied_and_expired_and_unknown_statuses() = runTest {
        assertEquals(LinkOutcome.Denied, login(FakeApi(script = mutableListOf(LinkPollResponse("denied", null, null)))).await("p", 300_000))
        assertEquals(LinkOutcome.Expired, login(FakeApi(script = mutableListOf(LinkPollResponse("expired", null, null)))).await("p", 300_000))
        assertEquals(LinkOutcome.Expired, login(FakeApi(script = mutableListOf(LinkPollResponse(null, null, null)))).await("p", 300_000))
    }

    @Test
    fun an_unknown_poll_token_is_expired_at_once() = runTest {
        val api = FakeApi(script = mutableListOf(ApiException(404, "gone")))
        assertEquals(LinkOutcome.Expired, login(api).await("p", 300_000))
        assertEquals(1, api.polls)
    }

    @Test
    fun network_failures_are_retried_with_backoff() = runTest {
        val clock = clock()
        val api = FakeApi(
            script = mutableListOf(
                ApiException(null, "offline"),
                ApiException(null, "offline"),
                LinkPollResponse("approved", tokens, user),
            )
        )
        val outcome = login(api, clock).await("p", 300_000)
        assertEquals(LinkOutcome.Approved(tokens, user), outcome)
        assertEquals(1_000L + 2_000L, clock())
    }

    @Test
    fun failures_past_the_deadline_give_up() = runTest {
        val clock = clock()
        val api = FakeApi(script = MutableList<Any>(100) { ApiException(null, "offline") })
        assertEquals(LinkOutcome.Expired, login(api, clock).await("p", deadlineMillis = 20_000))
        assertTrue(clock() in 20_000L..40_000L)
    }

    @Test
    fun backoff_doubles_up_to_fifteen_seconds() {
        assertEquals(0L, PollBackoff.delayMillis(0))
        assertEquals(1_000L, PollBackoff.delayMillis(1))
        assertEquals(2_000L, PollBackoff.delayMillis(2))
        assertEquals(8_000L, PollBackoff.delayMillis(4))
        assertEquals(15_000L, PollBackoff.delayMillis(5))
        assertEquals(15_000L, PollBackoff.delayMillis(50))
    }

    @Test
    fun rate_limited_waits_at_least_ten_seconds() {
        assertEquals(10_000L, PollBackoff.delayMillis(1, rateLimited = true))
        assertEquals(15_000L, PollBackoff.delayMillis(6, rateLimited = true))
    }

    @Test
    fun telegram_links_open_the_app_directly() {
        assertEquals(
            "tg://resolve?domain=GeekVPNBot&start=applogin_abc-DEF_1",
            TelegramLink.appUri("https://t.me/GeekVPNBot?start=applogin_abc-DEF_1"),
        )
        assertEquals("tg://resolve?domain=GeekVPNBot", TelegramLink.appUri("https://t.me/GeekVPNBot"))
    }

    @Test
    fun other_links_are_not_rewritten() {
        assertNull(TelegramLink.appUri("http://t.me/GeekVPNBot?start=x"))
        assertNull(TelegramLink.appUri("https://example.com/GeekVPNBot?start=x"))
        assertNull(TelegramLink.appUri("https://t.me/joinchat/abc"))
        assertNull(TelegramLink.appUri("not a url"))
        // A parameter Telegram would reject is dropped rather than passed on.
        assertEquals("tg://resolve?domain=GeekVPNBot", TelegramLink.appUri("https://t.me/GeekVPNBot?start=a%20b"))
    }
}
