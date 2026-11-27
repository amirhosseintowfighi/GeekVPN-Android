package com.geekvpn.connection

import com.geekvpn.ui.history.HistoryFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ConnectionHistoryTest {
    private val t0 = 1_800_000_000_000L

    @Test
    fun a_connection_is_opened_touched_and_ended() {
        var log = HistoryLog.begin(emptyList(), t0, "🇩🇪 Germany")
        assertTrue(log.single().open)
        log = HistoryLog.touch(log, t0 + 30_000, 1_000)
        log = HistoryLog.failover(log, "🇳🇱 Netherlands")
        log = HistoryLog.end(log, t0 + 60_000, 5_000, ConnectionRecord.End.DataCap)
        val record = log.single()
        assertFalse(record.open)
        assertEquals(60_000, record.duration(t0 + 999_999))
        assertEquals(5_000, record.bytes)
        assertEquals(1, record.failovers)
        assertEquals("🇳🇱 Netherlands", record.lastServer)
        assertEquals(ConnectionRecord.End.DataCap, record.endReason)
    }

    @Test
    fun a_connection_left_open_by_a_dead_process_ends_where_it_was_last_seen() {
        var log = HistoryLog.begin(emptyList(), t0, "A")
        log = HistoryLog.touch(log, t0 + 90_000, 10)
        log = HistoryLog.begin(log, t0 + 3_600_000, "B")
        assertEquals(listOf("B", "A"), log.map { it.server })
        val old = log[1]
        assertEquals(t0 + 90_000, old.endedAt)
        assertEquals(ConnectionRecord.End.Interrupted, old.endReason)
        assertTrue(log[0].open)
    }

    @Test
    fun ended_records_are_not_changed_again() {
        val ended = HistoryLog.end(HistoryLog.begin(emptyList(), t0, "A"), t0 + 1, 1, ConnectionRecord.End.Stopped)
        assertEquals(ended, HistoryLog.touch(ended, t0 + 2, 99))
        assertEquals(ended, HistoryLog.end(ended, t0 + 2, 99, ConnectionRecord.End.DataCap))
        assertEquals(emptyList<ConnectionRecord>(), HistoryLog.touch(emptyList(), t0, 1))
    }

    @Test
    fun old_and_extra_records_are_dropped() {
        var log = emptyList<ConnectionRecord>()
        repeat(HistoryLog.MAX_RECORDS + 5) { i -> log = HistoryLog.begin(log, t0 + i, "S$i") }
        assertEquals(HistoryLog.MAX_RECORDS, log.size)
        val later = HistoryLog.begin(log, t0 + HistoryLog.KEEP_MS + 1_000, "new")
        assertEquals(listOf("new"), later.map { it.server })
    }

    @Test
    fun unknown_end_names_read_as_null() {
        assertNull(ConnectionRecord(end = "Something").endReason)
    }

    @Test
    fun durations_read_as_a_clock() {
        assertEquals("01:15:04", HistoryFormat.duration(4_504_000, Locale.US))
    }
}
