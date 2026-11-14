package com.geekvpn.api

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ApiRoutesTest {

    private val hosts = ApiHosts.of("https://api.example.test/", " https://mirror.example.test/base , ,https://api.example.test")

    @Test
    fun hosts_are_the_main_address_then_distinct_mirrors() {
        assertEquals(
            listOf("https://api.example.test/", "https://mirror.example.test/base"),
            hosts.all.map { it.toString() },
        )
        assertEquals(1, ApiHosts.of("https://api.example.test", "").all.size)
    }

    @Test
    fun a_request_moves_onto_a_mirror_with_its_path_and_query() {
        val moved = hosts.move("https://api.example.test/api/miniapp/faq?x=1".toHttpUrl(), hosts.all[1])
        assertEquals("https://mirror.example.test/base/api/miniapp/faq?x=1", moved.toString())
        assertNull(hosts.move("https://other.example.test/api".toHttpUrl(), hosts.all[1]))
    }

    @Test
    fun the_address_that_answered_is_tried_first() {
        hosts.answered(hosts.all[1])
        assertEquals(listOf("mirror.example.test", "api.example.test"), hosts.order().map { it.host })
    }

    @Test
    fun only_a_request_that_never_left_moves_unless_it_is_a_get() {
        assertTrue(ApiFailoverPolicy.retryAfterError("GET", IOException("reset")))
        assertTrue(ApiFailoverPolicy.retryAfterError("POST", UnknownHostException("x")))
        assertTrue(ApiFailoverPolicy.retryAfterError("POST", ConnectException("refused")))
        assertTrue(ApiFailoverPolicy.retryAfterError("POST", SSLHandshakeException("reset")))
        assertTrue(ApiFailoverPolicy.retryAfterError("POST", SocketTimeoutException("connect timed out")))
        assertTrue(ApiFailoverPolicy.retryAfterError("POST", IOException("Unexpected response code for CONNECT: 502")))
        // Sent, then no answer: a checkout or a refresh may already have happened.
        assertFalse(ApiFailoverPolicy.retryAfterError("POST", SocketTimeoutException("timeout")))
        assertFalse(ApiFailoverPolicy.retryAfterError("POST", IOException("unexpected end of stream")))
        assertFalse(ApiFailoverPolicy.retryAfterForeignAnswer("POST", 502))
        assertTrue(ApiFailoverPolicy.retryAfterForeignAnswer("POST", 403))
        assertTrue(ApiFailoverPolicy.retryAfterForeignAnswer("GET", 502))
    }

    @Test
    fun an_api_answer_is_json_or_has_no_body() {
        assertTrue(ApiFailoverPolicy.isApiAnswer(200, "application/json"))
        assertTrue(ApiFailoverPolicy.isApiAnswer(422, "application/problem+json"))
        assertTrue(ApiFailoverPolicy.isApiAnswer(204, null))
        assertFalse(ApiFailoverPolicy.isApiAnswer(200, "text/html"))
        assertFalse(ApiFailoverPolicy.isApiAnswer(403, null))
    }

    /** What each host does, in place of the network. */
    private fun client(hosts: ApiHosts, seen: MutableList<String>, answer: (Request) -> Response) =
        OkHttpClient.Builder()
            .addInterceptor(ApiFailover(hosts))
            .addInterceptor(Interceptor { chain ->
                seen += chain.request().url.host
                answer(chain.request())
            })
            .build()

    private fun json(request: Request, code: Int = 200) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
        .body("{}".toResponseBody("application/json".toMediaType()))
        .build()

    private fun html(request: Request, code: Int) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
        .body("<html/>".toResponseBody("text/html".toMediaType()))
        .build()

    @Test
    fun a_blocked_main_address_falls_over_to_the_mirror_and_stays_there() {
        val hosts = ApiHosts.of("https://api.example.test", "https://mirror.example.test")
        val seen = mutableListOf<String>()
        val client = client(hosts, seen) { request ->
            if (request.url.host == "api.example.test") throw SSLHandshakeException("reset by the filter")
            json(request)
        }
        val get = Request.Builder().url("https://api.example.test/api/miniapp/faq").build()
        client.newCall(get).execute().use { assertEquals(200, it.code) }
        client.newCall(get).execute().use { assertEquals(200, it.code) }
        assertEquals(listOf("api.example.test", "mirror.example.test", "mirror.example.test"), seen)
    }

    @Test
    fun a_filtering_page_in_place_of_the_api_moves_on() {
        val hosts = ApiHosts.of("https://api.example.test", "https://mirror.example.test")
        val seen = mutableListOf<String>()
        val client = client(hosts, seen) { request ->
            if (request.url.host == "api.example.test") html(request, 403) else json(request)
        }
        val post = Request.Builder().url("https://api.example.test/api/app/auth/password")
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        client.newCall(post).execute().use { assertEquals(200, it.code) }
        assertEquals(listOf("api.example.test", "mirror.example.test"), seen)
    }

    @Test
    fun an_api_error_is_the_answer_and_a_sent_post_is_not_repeated() {
        val hosts = ApiHosts.of("https://api.example.test", "https://mirror.example.test")
        val seen = mutableListOf<String>()
        val client = client(hosts, seen) { request -> json(request, 401) }
        val get = Request.Builder().url("https://api.example.test/api/miniapp/wallet").build()
        client.newCall(get).execute().use { assertEquals(401, it.code) }
        assertEquals(listOf("api.example.test"), seen)

        seen.clear()
        val failing = client(hosts, seen) { throw SocketTimeoutException("timeout") }
        val refresh = Request.Builder().url("https://api.example.test/api/v1/auth/refresh")
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        try {
            failing.newCall(refresh).execute()
            fail("expected the timeout")
        } catch (_: SocketTimeoutException) {
        }
        assertEquals(listOf("api.example.test"), seen)
    }

    @Test
    fun the_core_proxy_comes_first_only_while_it_listens() {
        var listening = true
        var now = 0L
        var probes = 0
        val selector = CoreProxySelector(
            port = { 10809 },
            listening = { probes++; listening },
            clock = { now },
        )
        val uri = URI("https://api.example.test/")
        assertEquals(
            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809)), Proxy.NO_PROXY),
            selector.select(uri),
        )
        listening = false
        assertEquals(2, selector.select(uri).size) // remembered for a few seconds
        assertEquals(1, probes)
        now += 5_000
        assertEquals(listOf(Proxy.NO_PROXY), selector.select(uri))
        assertEquals(listOf(Proxy.NO_PROXY), CoreProxySelector(port = { 0 }).select(uri))
    }
}
