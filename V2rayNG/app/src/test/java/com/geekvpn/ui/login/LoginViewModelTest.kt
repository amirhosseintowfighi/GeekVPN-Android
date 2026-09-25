package com.geekvpn.ui.login

import com.geekvpn.api.ApiException
import com.geekvpn.api.AppUser
import com.geekvpn.api.TokenPair
import com.geekvpn.auth.LinkOutcome
import com.geekvpn.auth.LinkStarted
import com.v2ray.ang.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LoginViewModelTest {

    private val link = "https://t.me/GeekVPNBot?start=applogin_abc"
    private val approved = LinkOutcome.Approved(
        TokenPair("a", "r", "Bearer", null, null, "s"),
        AppUser("u1", 555, "Amir", null, "fa", "R555", null),
    )

    private class FakeBackend : LoginBackend {
        var startError: ApiException? = null
        val outcome = CompletableDeferred<LinkOutcome>()
        var storeWorks = true
        var syncError: IOException? = null
        var signedIn = false
        var synced = false
        var guest = false
        var awaitedDeadline = 0L
        var passwordError: ApiException? = null
        /** When set, the password check waits for it: the "server is thinking" moment. */
        var passwordGate: CompletableDeferred<Unit>? = null
        val passwordCalls = mutableListOf<Pair<String, String>>()

        override suspend fun start(): LinkStarted {
            startError?.let { throw it }
            return LinkStarted("https://t.me/GeekVPNBot?start=applogin_abc", "poll", 300)
        }

        override suspend fun await(pollToken: String, deadlineMillis: Long): LinkOutcome {
            awaitedDeadline = deadlineMillis
            return outcome.await()
        }

        override suspend fun passwordLogin(username: String, password: String): LinkOutcome.Approved {
            passwordCalls += username to password
            passwordGate?.await()
            passwordError?.let { throw it }
            return LinkOutcome.Approved(
                TokenPair("a", "r", "Bearer", null, null, "s"),
                AppUser("u1", 555, "Amir", null, "fa", "R555", null),
            )
        }

        override fun signIn(approved: LinkOutcome.Approved): Boolean {
            signedIn = storeWorks
            return storeWorks
        }

        override suspend fun sync() {
            syncError?.let { throw it }
            synced = true
        }

        override fun continueAsGuest() {
            guest = true
        }

        override fun now() = 1_000_000L

        override fun logFailure(message: String, e: Throwable) = Unit
    }

    /**
     * The ViewModel and an event collector on the test scheduler. Not in
     * `backgroundScope`: `advanceUntilIdle()` stops once only background work
     * is left, so a collector there would never see the events.
     */
    private class Harness(scope: TestScope, val backend: FakeBackend = FakeBackend()) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val viewModel = LoginViewModel(backend, CoroutineScope(SupervisorJob() + dispatcher))
        val events = mutableListOf<LoginEvent>()

        init {
            CoroutineScope(SupervisorJob() + dispatcher).launch {
                viewModel.events.toList(events)
            }
        }
    }

    @Test
    fun starts_on_the_choice_screen() = runTest {
        val h = Harness(this)
        assertEquals(LoginUiState.Choose, h.viewModel.uiState.value)
    }

    @Test
    fun telegram_sign_in_opens_the_link_and_waits() = runTest {
        val h = Harness(this)
        h.viewModel.startTelegram(LinkPurpose.SignIn)
        testScheduler.advanceUntilIdle()

        assertEquals(LoginUiState.Waiting(LinkPurpose.SignIn, link, 1_000_000L + 300_000L), h.viewModel.uiState.value)
        assertEquals(listOf<LoginEvent>(LoginEvent.OpenTelegram(link)), h.events)
        assertEquals(1_300_000L, h.backend.awaitedDeadline)
    }

    @Test
    fun approval_stores_the_session_syncs_and_finishes() = runTest {
        val h = Harness(this)
        h.viewModel.startTelegram(LinkPurpose.CreateAccount)
        testScheduler.advanceUntilIdle()
        h.backend.outcome.complete(approved)
        testScheduler.advanceUntilIdle()

        assertTrue(h.backend.signedIn)
        assertTrue(h.backend.synced)
        assertEquals(LoginUiState.Syncing, h.viewModel.uiState.value)
        assertEquals(listOf(LoginEvent.OpenTelegram(link), LoginEvent.Done), h.events)
    }

    @Test
    fun a_failed_first_sync_still_finishes_with_a_note() = runTest {
        val h = Harness(this)
        h.backend.syncError = IOException("offline")
        h.viewModel.startTelegram(LinkPurpose.SignIn)
        testScheduler.advanceUntilIdle()
        h.backend.outcome.complete(approved)
        testScheduler.advanceUntilIdle()

        assertEquals(
            listOf(
                LoginEvent.OpenTelegram(link),
                LoginEvent.Message(R.string.geek_login_sync_failed),
                LoginEvent.Done,
            ),
            h.events,
        )
    }

    @Test
    fun a_session_that_cannot_be_stored_returns_to_the_choice_screen() = runTest {
        val h = Harness(this)
        h.backend.storeWorks = false
        h.viewModel.startTelegram(LinkPurpose.SignIn)
        testScheduler.advanceUntilIdle()
        h.backend.outcome.complete(approved)
        testScheduler.advanceUntilIdle()

        assertEquals(LoginUiState.Choose, h.viewModel.uiState.value)
        assertFalse(h.backend.synced)
        assertEquals(LoginEvent.Message(R.string.geek_login_err_storage), h.events.last())
    }

    @Test
    fun denied_and_expired_return_to_the_choice_screen_with_a_reason() = runTest {
        for ((outcome, message) in listOf(
            LinkOutcome.Denied to R.string.geek_login_err_denied,
            LinkOutcome.Expired to R.string.geek_login_err_expired,
        )) {
            val h = Harness(this)
            h.viewModel.startTelegram(LinkPurpose.SignIn)
            testScheduler.advanceUntilIdle()
            h.backend.outcome.complete(outcome)
            testScheduler.advanceUntilIdle()

            assertEquals(LoginUiState.Choose, h.viewModel.uiState.value)
            assertEquals(LoginEvent.Message(message), h.events.last())
            assertFalse(h.backend.signedIn)
        }
    }

    @Test
    fun start_failures_map_to_their_messages() = runTest {
        for ((status, message) in listOf(
            null to R.string.geek_login_err_network,
            503 to R.string.geek_login_err_disabled,
            429 to R.string.geek_login_err_rate,
            500 to R.string.geek_login_err_network,
        )) {
            val h = Harness(this)
            h.backend.startError = ApiException(status, "failed")
            h.viewModel.startTelegram(LinkPurpose.SignIn)
            testScheduler.advanceUntilIdle()

            assertEquals(LoginUiState.Choose, h.viewModel.uiState.value)
            assertEquals(listOf<LoginEvent>(LoginEvent.Message(message)), h.events)
        }
    }

    @Test
    fun cancel_stops_waiting_and_a_late_approval_is_ignored() = runTest {
        val h = Harness(this)
        h.viewModel.startTelegram(LinkPurpose.SignIn)
        testScheduler.advanceUntilIdle()
        h.viewModel.cancel()
        h.backend.outcome.complete(approved)
        testScheduler.advanceUntilIdle()

        assertEquals(LoginUiState.Choose, h.viewModel.uiState.value)
        assertFalse(h.backend.signedIn)
    }

    @Test
    fun a_second_tap_while_starting_does_not_start_twice() = runTest {
        val h = Harness(this)
        h.viewModel.startTelegram(LinkPurpose.SignIn)
        h.viewModel.startTelegram(LinkPurpose.CreateAccount)
        testScheduler.advanceUntilIdle()

        assertEquals(LinkPurpose.SignIn, (h.viewModel.uiState.value as LoginUiState.Waiting).purpose)
        assertEquals(1, h.events.count { it is LoginEvent.OpenTelegram })
    }

    @Test
    fun reopen_sends_the_same_link_again_only_while_waiting() = runTest {
        val h = Harness(this)
        h.viewModel.reopenTelegram()
        testScheduler.advanceUntilIdle()
        assertEquals(emptyList<LoginEvent>(), h.events)

        h.viewModel.startTelegram(LinkPurpose.SignIn)
        testScheduler.advanceUntilIdle()
        h.viewModel.reopenTelegram()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf(LoginEvent.OpenTelegram(link), LoginEvent.OpenTelegram(link)), h.events)
    }

    @Test
    fun guest_mode_is_stored_and_finishes() = runTest {
        val h = Harness(this)
        h.viewModel.continueAsGuest()
        testScheduler.advanceUntilIdle()

        assertTrue(h.backend.guest)
        assertEquals(listOf<LoginEvent>(LoginEvent.Done), h.events)
    }

    @Test
    fun username_opens_the_form_and_back_returns_to_the_choice() = runTest {
        val h = Harness(this)
        h.viewModel.username()
        assertEquals(LoginUiState.Username(), h.viewModel.uiState.value)

        h.viewModel.cancel()
        assertEquals(LoginUiState.Choose, h.viewModel.uiState.value)
    }

    @Test
    fun the_right_password_signs_in_syncs_and_finishes() = runTest {
        val h = Harness(this)
        h.viewModel.username()
        h.viewModel.submitPassword("  ali_92 ", "correct horse")
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("ali_92" to "correct horse"), h.backend.passwordCalls)
        assertTrue(h.backend.signedIn)
        assertTrue(h.backend.synced)
        assertEquals(listOf<LoginEvent>(LoginEvent.Done), h.events)
    }

    @Test
    fun empty_fields_are_not_sent() = runTest {
        val h = Harness(this)
        h.viewModel.username()
        h.viewModel.submitPassword(" ", "x")
        h.viewModel.submitPassword("ali_92", "")
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList<Pair<String, String>>(), h.backend.passwordCalls)
        assertEquals(
            listOf<LoginEvent>(
                LoginEvent.Message(R.string.geek_login_err_fields),
                LoginEvent.Message(R.string.geek_login_err_fields),
            ),
            h.events,
        )
        assertEquals(LoginUiState.Username(), h.viewModel.uiState.value)
    }

    @Test
    fun password_failures_keep_the_form_and_say_why() = runTest {
        for ((status, message) in listOf(
            401 to R.string.geek_login_err_wrong,
            429 to R.string.geek_login_err_rate,
            null to R.string.geek_login_err_network,
        )) {
            val h = Harness(this)
            h.backend.passwordError = ApiException(status, "failed")
            h.viewModel.username()
            h.viewModel.submitPassword("ali_92", "wrong one")
            testScheduler.advanceUntilIdle()

            assertEquals(LoginUiState.Username(), h.viewModel.uiState.value)
            assertEquals(listOf<LoginEvent>(LoginEvent.Message(message)), h.events)
            assertFalse(h.backend.signedIn)
        }
    }

    @Test
    fun the_form_is_busy_while_the_server_checks_and_a_second_tap_is_ignored() = runTest {
        val h = Harness(this)
        h.backend.passwordGate = CompletableDeferred()
        h.viewModel.username()
        h.viewModel.submitPassword("ali_92", "correct horse")
        testScheduler.advanceUntilIdle()

        assertEquals(LoginUiState.Username(busy = true), h.viewModel.uiState.value)
        h.viewModel.submitPassword("ali_92", "correct horse")
        testScheduler.advanceUntilIdle()
        assertEquals(1, h.backend.passwordCalls.size)

        h.backend.passwordGate?.complete(Unit)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf<LoginEvent>(LoginEvent.Done), h.events)
    }
}
