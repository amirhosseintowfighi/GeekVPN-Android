package com.geekvpn.ui.support

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.GeekGraph
import com.geekvpn.api.ApiException
import com.geekvpn.api.OpenTicketRequest
import com.geekvpn.api.TicketCard
import com.geekvpn.api.TicketMessage
import com.geekvpn.auth.Session
import com.geekvpn.support.TicketTopic
import com.geekvpn.support.Tickets
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which part of the support screen is showing. */
sealed interface TicketsPage {
    data object List : TicketsPage
    data class Thread(val ticketId: String) : TicketsPage
    data object New : TicketsPage
}

data class TicketsUiState(
    val signedIn: Boolean = true,
    /** Null until the first answer. */
    val tickets: List<TicketCard>? = null,
    val listFailed: Boolean = false,
    val page: TicketsPage = TicketsPage.List,
    /** The open thread, oldest first; null while it loads. */
    val messages: List<TicketMessage>? = null,
    val threadFailed: Boolean = false,
    val reply: String = "",
    val topic: TicketTopic = TicketTopic.Connection,
    val subject: String = "",
    val body: String = "",
    val sending: Boolean = false,
    /** What was typed is under the bot's minimum (or a new ticket has no subject). */
    val tooShort: Boolean = false,
) {
    val openTicket: TicketCard?
        get() = (page as? TicketsPage.Thread)?.let { thread -> tickets?.firstOrNull { it.ticketId == thread.ticketId } }
}

sealed interface TicketsEvent {
    data class Failed(@param:StringRes val message: Int, val serverText: String? = null) : TicketsEvent

    /** A new ticket went in; [reference] is what support's messages will quote. */
    data class Opened(val reference: String?) : TicketsEvent
}

/** The backend calls, apart so the screen's logic runs in a JVM test. */
interface TicketsPorts {
    val signedIn: Boolean
    suspend fun list(): List<TicketCard>
    suspend fun thread(ticketId: String): List<TicketMessage>
    suspend fun reply(ticketId: String, message: String)

    /** Opens a ticket and returns its reference. */
    suspend fun open(topic: String, subject: String, message: String): String?
    fun logFailure(operation: String, error: ApiException)
}

/**
 * "تیکت‌های من": the customer's support tickets with support's answers, the
 * same threads the bot and the Mini App show. Answers also arrive in the bot;
 * the open page is refreshed while it is on screen ([pollWhileVisible]).
 */
class TicketsViewModel(
    private val ports: TicketsPorts,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val state = MutableStateFlow(TicketsUiState(signedIn = ports.signedIn))
    val uiState: StateFlow<TicketsUiState> = state.asStateFlow()

    private val eventChannel = Channel<TicketsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var listJob: Job? = null
    private var threadJob: Job? = null

    init {
        refresh()
    }

    /** Reloads the list, and the open thread with it. */
    fun refresh() {
        if (!state.value.signedIn) return
        loadList()
        (state.value.page as? TicketsPage.Thread)?.let { loadThread(it.ticketId) }
    }

    /**
     * Refreshes what is on screen until the caller's scope (the activity's
     * STARTED) ends; coming back to the screen refreshes at once.
     */
    suspend fun pollWhileVisible() {
        if (state.value.tickets != null) refresh()
        while (true) {
            delay(POLL_MS)
            if (!state.value.sending) refresh()
        }
    }

    fun open(ticketId: String) {
        state.update { it.copy(page = TicketsPage.Thread(ticketId), messages = null, threadFailed = false, reply = "", tooShort = false) }
        loadThread(ticketId)
    }

    fun startNew() {
        state.update { it.copy(page = TicketsPage.New, tooShort = false) }
    }

    /** Back inside the screen; false when the list is showing and the screen should close. */
    fun back(): Boolean {
        if (state.value.page == TicketsPage.List) return false
        threadJob?.cancel()
        state.update { it.copy(page = TicketsPage.List, messages = null, threadFailed = false, tooShort = false) }
        return true
    }

    fun setReply(text: String) = state.update { it.copy(reply = text.take(Tickets.MAX_MESSAGE), tooShort = false) }
    fun setTopic(topic: TicketTopic) = state.update { it.copy(topic = topic) }
    fun setSubject(text: String) = state.update { it.copy(subject = text.take(Tickets.MAX_SUBJECT), tooShort = false) }
    fun setBody(text: String) = state.update { it.copy(body = text.take(Tickets.MAX_MESSAGE), tooShort = false) }

    fun sendReply() {
        val current = state.value
        val thread = current.page as? TicketsPage.Thread ?: return
        if (current.sending) return
        if (!Tickets.messageOk(current.reply)) {
            state.update { it.copy(tooShort = true) }
            return
        }
        state.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                ports.reply(thread.ticketId, current.reply.trim())
                state.update { it.copy(reply = "") }
                loadThread(thread.ticketId)
                loadList()
            } catch (e: ApiException) {
                ports.logFailure("reply", e)
                eventChannel.send(failure(e, R.string.geek_tickets_err_send))
            } finally {
                state.update { it.copy(sending = false) }
            }
        }
    }

    fun submitNew() {
        val current = state.value
        if (current.page != TicketsPage.New || current.sending) return
        if (current.subject.isBlank() || !Tickets.messageOk(current.body)) {
            state.update { it.copy(tooShort = true) }
            return
        }
        state.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                val reference = ports.open(current.topic.key, current.subject.trim(), current.body.trim())
                state.update { it.copy(page = TicketsPage.List, subject = "", body = "", topic = TicketTopic.Connection) }
                eventChannel.send(TicketsEvent.Opened(reference))
                loadList()
            } catch (e: ApiException) {
                ports.logFailure("open", e)
                eventChannel.send(failure(e, R.string.geek_tickets_err_send))
            } finally {
                state.update { it.copy(sending = false) }
            }
        }
    }

    private fun loadList() {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            try {
                val tickets = Tickets.sorted(ports.list())
                state.update { it.copy(tickets = tickets, listFailed = false) }
            } catch (e: ApiException) {
                ports.logFailure("list", e)
                // Keep what is on screen; only an empty screen shows the failure.
                state.update { it.copy(listFailed = true) }
            }
        }
    }

    private fun loadThread(ticketId: String) {
        threadJob?.cancel()
        threadJob = viewModelScope.launch {
            try {
                val messages = ports.thread(ticketId)
                if (state.value.page == TicketsPage.Thread(ticketId)) {
                    state.update { it.copy(messages = messages, threadFailed = false) }
                }
            } catch (e: ApiException) {
                ports.logFailure("thread", e)
                state.update { it.copy(threadFailed = true) }
            }
        }
    }

    private fun failure(e: ApiException, @StringRes fallback: Int) =
        TicketsEvent.Failed(if (e.isNetwork) R.string.geek_tickets_err_network else fallback, e.messageFa)

    companion object {
        const val POLL_MS = 20_000L

        fun factory() = viewModelFactory {
            initializer {
                TicketsViewModel(
                    object : TicketsPorts {
                        override val signedIn: Boolean get() = GeekGraph.session.session.value is Session.SignedIn
                        override suspend fun list() = GeekGraph.api.tickets()
                        override suspend fun thread(ticketId: String) = GeekGraph.api.ticketMessages(ticketId)
                        override suspend fun reply(ticketId: String, message: String) {
                            GeekGraph.api.replyToTicket(ticketId, message)
                        }
                        override suspend fun open(topic: String, subject: String, message: String) =
                            GeekGraph.api.openTicket(OpenTicketRequest(topic, message, subject)).reference
                        override fun logFailure(operation: String, error: ApiException) {
                            LogUtil.w(AppConfig.TAG, "Tickets: $operation failed (${error.status})", error)
                        }
                    },
                )
            }
        }
    }
}
