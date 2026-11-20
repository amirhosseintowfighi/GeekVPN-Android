package com.geekvpn.autoconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleTest {
    private val day = LocalDateTime.of(2026, 11, 20, 0, 0)

    @Test
    fun the_next_event_is_the_sooner_of_start_and_end() {
        // 08:00–23:00, now 07:00: connect at 08:00 today.
        assertEquals(Schedule.Event(day.withHour(8), true), Schedule.next(day.withHour(7), 8 * 60, 23 * 60))
        // Now 12:00: disconnect at 23:00 today.
        assertEquals(Schedule.Event(day.withHour(23), false), Schedule.next(day.withHour(12), 8 * 60, 23 * 60))
        // Now 23:30: connect tomorrow at 08:00.
        assertEquals(Schedule.Event(day.plusDays(1).withHour(8), true), Schedule.next(day.withHour(23).withMinute(30), 8 * 60, 23 * 60))
    }

    @Test
    fun an_event_exactly_now_is_tomorrows() {
        // At 08:00 sharp the start is tomorrow's, so the end comes first.
        assertEquals(Schedule.Event(day.withHour(9), false), Schedule.next(day.withHour(8), 8 * 60, 9 * 60))
        assertEquals(Schedule.Event(day.plusDays(1).withHour(8), true), Schedule.next(day.withHour(9), 8 * 60, 9 * 60))
    }

    @Test
    fun a_window_across_midnight() {
        // 22:00–06:00.
        assertEquals(Schedule.Event(day.withHour(6), false), Schedule.next(day.withHour(1), 22 * 60, 6 * 60))
        assertEquals(Schedule.Event(day.withHour(22), true), Schedule.next(day.withHour(12), 22 * 60, 6 * 60))
        assertTrue(Schedule.inWindow(LocalTime.of(23, 0), 22 * 60, 6 * 60))
        assertTrue(Schedule.inWindow(LocalTime.of(5, 59), 22 * 60, 6 * 60))
        assertFalse(Schedule.inWindow(LocalTime.of(6, 0), 22 * 60, 6 * 60))
        assertFalse(Schedule.inWindow(LocalTime.of(12, 0), 22 * 60, 6 * 60))
    }

    @Test
    fun the_window_includes_its_start_and_not_its_end() {
        assertTrue(Schedule.inWindow(LocalTime.of(8, 0), 8 * 60, 23 * 60))
        assertFalse(Schedule.inWindow(LocalTime.of(23, 0), 8 * 60, 23 * 60))
        assertFalse(Schedule.inWindow(LocalTime.of(7, 59), 8 * 60, 23 * 60))
    }

    @Test
    fun the_same_start_and_end_is_no_schedule() {
        assertNull(Schedule.next(day.withHour(7), 8 * 60, 8 * 60))
    }
}
