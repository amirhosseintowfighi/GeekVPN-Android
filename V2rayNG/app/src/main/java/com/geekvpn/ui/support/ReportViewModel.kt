package com.geekvpn.ui.support

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.GeekGraph
import com.geekvpn.api.ApiException
import com.geekvpn.api.OpenTicketRequest
import com.geekvpn.auth.Session
import com.geekvpn.support.ProblemReport
import com.geekvpn.support.ReportCollector
import com.geekvpn.support.ReportFacts
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReportUiState(
    val description: String = "",
    /** The technical part as it will be sent; null while it is gathered. */
    val technical: String? = null,
    val signedIn: Boolean = false,
    val sending: Boolean = false,
    /** The description is too short to open a ticket with. */
    val tooShort: Boolean = false,
)

sealed interface ReportEvent {
    /** Opened; [reference] is what the bot's messages about it will quote. */
    data class Sent(val reference: String?) : ReportEvent
    data class Failed(@param:StringRes val message: Int, val serverText: String? = null) : ReportEvent

    /** Signed out: the whole report, for the clipboard and the bot. */
    data class Copy(val text: String) : ReportEvent
}

/** What the report needs from the phone and the backend. */
interface ReportPorts {
    val signedIn: Boolean
    fun facts(): ReportFacts
    fun log(): List<String>

    /** Opens a ticket and returns its reference. */
    suspend fun send(topic: String, message: String): String?
}

class ReportViewModel(
    private val ports: ReportPorts,
    private val io: CoroutineDispatcher,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val state = MutableStateFlow(ReportUiState(signedIn = ports.signedIn))
    val uiState: StateFlow<ReportUiState> = state.asStateFlow()

    private val eventChannel = Channel<ReportEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var facts: ReportFacts? = null
    private var log: List<String> = emptyList()

    init {
        viewModelScope.launch {
            val (gathered, lines) = withContext(io) { ports.facts() to ports.log() }
            facts = gathered
            log = lines
            state.update { it.copy(technical = ProblemReport.compose("", gathered, lines).trim()) }
        }
    }

    fun describe(text: String) {
        state.update { it.copy(description = text.take(MAX_DESCRIPTION), tooShort = false) }
    }

    fun send() {
        val current = state.value
        val gathered = facts ?: return
        if (current.sending) return
        if (current.description.trim().length < MIN_DESCRIPTION) {
            state.update { it.copy(tooShort = true) }
            return
        }
        val text = ProblemReport.compose(current.description, gathered, log)
        if (!current.signedIn) {
            eventChannel.trySend(ReportEvent.Copy(text))
            return
        }
        viewModelScope.launch {
            state.update { it.copy(sending = true) }
            val event = try {
                ReportEvent.Sent(ports.send(TOPIC, text))
            } catch (e: ApiException) {
                LogUtil.w(AppConfig.TAG, "Report: opening the ticket failed (${e.status})", e)
                ReportEvent.Failed(
                    if (e.isNetwork) R.string.geek_report_err_network else R.string.geek_report_err_server,
                    e.messageFa,
                )
            }
            state.update { it.copy(sending = false) }
            eventChannel.send(event)
        }
    }

    companion object {
        /** The ticket category for connection problems (the bot's own key). */
        const val TOPIC = "connection"
        const val MIN_DESCRIPTION = 5
        const val MAX_DESCRIPTION = 1_000

        fun factory(application: Application) = viewModelFactory {
            initializer {
                ReportViewModel(
                    ports = object : ReportPorts {
                        override val signedIn: Boolean get() = GeekGraph.session.session.value is Session.SignedIn
                        override fun facts() = ReportCollector.facts(application)
                        override fun log() = ReportCollector.log()
                        override suspend fun send(topic: String, message: String) =
                            GeekGraph.api.openTicket(OpenTicketRequest(topic, message)).reference
                    },
                    io = Dispatchers.IO,
                )
            }
        }
    }
}
