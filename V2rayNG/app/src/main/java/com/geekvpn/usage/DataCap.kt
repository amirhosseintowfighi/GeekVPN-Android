package com.geekvpn.usage

import java.time.LocalDate

/**
 * «سقف مصرف»: a limit the customer sets on this phone's VPN traffic per day
 * or per calendar month, counted from [DailyUsage]. Pure, for the tests;
 * [CapNotifier] posts what [DataCapPolicy.decide] returns and the VPN
 * process ([com.geekvpn.smartconnect.FailoverMonitor]) disconnects on it.
 */
data class DataCap(
    val limitBytes: Long,
    val period: Period,
    /** Disconnect when the limit is crossed; otherwise only tell. */
    val disconnect: Boolean,
) {
    enum class Period { Day, Month }

    companion object {
        const val MIB = 1024L * 1024
        const val GIB = MIB * 1024

        /** The limits offered in the screen, smallest first. */
        val PRESETS: List<Long> = listOf(500 * MIB, GIB, 2 * GIB, 5 * GIB, 10 * GIB, 20 * GIB, 50 * GIB)
    }
}

object DataCapPolicy {
    /** Share of the limit at which the early warning goes out. */
    const val WARN_FRACTION = 0.8

    enum class Event { Warn, Reached }

    data class Decision(
        /** Notifications to post now; each at most once per period. */
        val events: List<Event>,
        /** Keys to add to the sent set, matching [events]. */
        val sentKeys: Set<String>,
        val disconnect: Boolean,
    ) {
        companion object {
            val None = Decision(emptyList(), emptySet(), false)
        }
    }

    /** Identifies the period [today] is in: a new day or month starts with nothing sent. */
    fun periodKey(period: DataCap.Period, today: LocalDate): String = when (period) {
        DataCap.Period.Day -> "d$today"
        DataCap.Period.Month -> "m${today.year}-${today.monthValue.toString().padStart(2, '0')}"
    }

    /** First day counted in the period [today] is in. */
    fun periodStart(period: DataCap.Period, today: LocalDate): LocalDate = when (period) {
        DataCap.Period.Day -> today
        DataCap.Period.Month -> today.withDayOfMonth(1)
    }

    /**
     * What to do with [used] bytes counted so far this period.
     *
     * Disconnects only when this connection crossed the limit ([usedAtStart]
     * was under it): connecting again after a cap disconnect is the customer
     * choosing to go on, and the VPN must not drop again half a minute later.
     */
    fun decide(cap: DataCap?, used: Long, usedAtStart: Long, sent: Set<String>, periodKey: String): Decision {
        if (cap == null || cap.limitBytes <= 0) return Decision.None
        val events = mutableListOf<Event>()
        val keys = mutableSetOf<String>()
        val reached = used >= cap.limitBytes
        val reachKey = "reach|$periodKey|${cap.limitBytes}"
        val warnKey = "warn|$periodKey|${cap.limitBytes}"
        if (reached) {
            if (reachKey !in sent) {
                events += Event.Reached
                keys += reachKey
            }
            // Reaching the limit says all the warning would.
            keys += warnKey
        } else if (used >= (cap.limitBytes * WARN_FRACTION).toLong() && warnKey !in sent) {
            events += Event.Warn
            keys += warnKey
        }
        val disconnect = cap.disconnect && reached && usedAtStart < cap.limitBytes
        return Decision(events, keys - sent, disconnect)
    }

    /** Sent keys of earlier periods are dropped so the set stays small. */
    fun prune(sent: Set<String>, periodKey: String): Set<String> =
        sent.filterTo(mutableSetOf()) { it.split('|').getOrNull(1) == periodKey }
}
