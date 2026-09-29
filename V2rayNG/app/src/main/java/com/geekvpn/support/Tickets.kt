package com.geekvpn.support

import androidx.annotation.StringRes
import com.geekvpn.api.TicketCard
import com.geekvpn.connection.ServiceStatus
import com.v2ray.ang.R
import java.time.Instant

/** Where a ticket stands, as the customer reads it (the backend's `TicketState`). */
enum class TicketStatus(val key: String, @param:StringRes val label: Int) {
    Open("open", R.string.geek_tickets_state_open),

    /** Support answered and the next move is the customer's. */
    Waiting("waiting", R.string.geek_tickets_state_waiting),
    Answered("answered", R.string.geek_tickets_state_answered),
    Closed("closed", R.string.geek_tickets_state_closed);

    /** A closed ticket takes no more replies; a new topic is a new ticket. */
    val canReply: Boolean get() = this != Closed

    companion object {
        /** Unknown states read as open: the ticket is still there to reply to. */
        fun of(key: String?): TicketStatus = entries.firstOrNull { it.key == key } ?: Open
    }
}

/** The ticket categories, keyed like the bot's support handler and the Mini App. */
enum class TicketTopic(val key: String, @param:StringRes val label: Int) {
    Connection("connection", R.string.geek_tickets_topic_connection),
    Payment("payment", R.string.geek_tickets_topic_payment),
    Account("account", R.string.geek_tickets_topic_account),
    Speed("speed", R.string.geek_tickets_topic_speed),
    Other("other", R.string.geek_tickets_topic_other),
}

object Tickets {
    /** The bot's own floor, so a message accepted here is accepted there. */
    const val MIN_MESSAGE = 10
    const val MAX_MESSAGE = 4_000
    const val MAX_SUBJECT = 128

    /** The thread that moved last first, from either side. */
    fun sorted(tickets: List<TicketCard>): List<TicketCard> =
        tickets.filter { it.ticketId != null }
            .sortedByDescending { lastMoved(it) ?: Instant.EPOCH }

    fun lastMoved(ticket: TicketCard): Instant? =
        ServiceStatus.parseInstant(ticket.lastReplyAt) ?: ServiceStatus.parseInstant(ticket.createdAt)

    fun messageOk(text: String): Boolean = text.trim().length in MIN_MESSAGE..MAX_MESSAGE
}
