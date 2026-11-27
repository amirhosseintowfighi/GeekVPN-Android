package com.geekvpn.usage

import com.geekvpn.usage.DataCapPolicy.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DataCapPolicyTest {
    private val today = LocalDate.of(2026, 11, 25)
    private val gib = DataCap.GIB
    private val month = DataCap(10 * gib, DataCap.Period.Month, disconnect = true)
    private val key = DataCapPolicy.periodKey(month.period, today)

    @Test
    fun periods_start_on_the_day_or_the_first_of_the_month() {
        assertEquals("d2026-11-25", DataCapPolicy.periodKey(DataCap.Period.Day, today))
        assertEquals("m2026-11", DataCapPolicy.periodKey(DataCap.Period.Month, today))
        assertEquals(LocalDate.of(2026, 11, 1), DataCapPolicy.periodStart(DataCap.Period.Month, today))
        assertEquals(today, DataCapPolicy.periodStart(DataCap.Period.Day, today))
    }

    @Test
    fun no_cap_or_below_the_warning_does_nothing() {
        assertEquals(DataCapPolicy.Decision.None, DataCapPolicy.decide(null, 99 * gib, 0, emptySet(), key))
        assertEquals(DataCapPolicy.Decision.None, DataCapPolicy.decide(month, 7 * gib, 0, emptySet(), key))
    }

    @Test
    fun the_warning_goes_out_once_at_eighty_percent() {
        val first = DataCapPolicy.decide(month, 8 * gib, 0, emptySet(), key)
        assertEquals(listOf(Event.Warn), first.events)
        assertFalse(first.disconnect)
        val again = DataCapPolicy.decide(month, 9 * gib, 0, first.sentKeys, key)
        assertTrue(again.events.isEmpty())
    }

    @Test
    fun crossing_the_limit_tells_once_and_disconnects() {
        val warned = DataCapPolicy.decide(month, 8 * gib, 0, emptySet(), key).sentKeys
        val reached = DataCapPolicy.decide(month, 10 * gib, 5 * gib, warned, key)
        assertEquals(listOf(Event.Reached), reached.events)
        assertTrue(reached.disconnect)
        // Still over the limit on the next sample: no second notification.
        val next = DataCapPolicy.decide(month, 11 * gib, 5 * gib, warned + reached.sentKeys, key)
        assertTrue(next.events.isEmpty())
    }

    @Test
    fun a_connection_started_over_the_limit_is_not_dropped() {
        val decision = DataCapPolicy.decide(month, 12 * gib, 11 * gib, emptySet(), key)
        assertFalse(decision.disconnect)
    }

    @Test
    fun warn_only_never_disconnects_and_skips_the_warning_when_already_over() {
        val decision = DataCapPolicy.decide(month.copy(disconnect = false), 10 * gib, 0, emptySet(), key)
        assertEquals(listOf(Event.Reached), decision.events)
        assertFalse(decision.disconnect)
    }

    @Test
    fun a_new_limit_or_a_new_period_starts_over() {
        val sent = DataCapPolicy.decide(month, 10 * gib, 0, emptySet(), key).sentKeys
        val raised = DataCapPolicy.decide(month.copy(limitBytes = 20 * gib), 16 * gib, 0, sent, key)
        assertEquals(listOf(Event.Warn), raised.events)
        val nextMonth = DataCapPolicy.periodKey(DataCap.Period.Month, today.plusMonths(1))
        assertTrue(DataCapPolicy.prune(sent, nextMonth).isEmpty())
        assertEquals(sent, DataCapPolicy.prune(sent, key))
    }
}
