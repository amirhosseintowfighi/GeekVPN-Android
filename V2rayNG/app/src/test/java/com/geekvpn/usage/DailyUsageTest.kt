package com.geekvpn.usage

import com.geekvpn.ui.usage.UsageChart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class DailyUsageTest {
    private val today = LocalDate.of(2026, 9, 28)

    @Test
    fun the_recorder_books_only_what_moved_since_the_last_sample() {
        val booked = mutableListOf<Pair<LocalDate, Long>>()
        val recorder = DailyUsage.Recorder({ day, bytes -> booked += day to bytes }, { today })
        recorder.sample(1_000) // baseline
        recorder.sample(1_500)
        recorder.sample(1_500) // nothing moved
        recorder.sample(200) // counter reset: books nothing, becomes the new baseline
        recorder.sample(700)
        recorder.sample(-1) // unsupported: ignored
        assertEquals(listOf(today to 500L, today to 500L), booked)
    }

    @Test
    fun the_chart_scales_to_the_busiest_day() {
        val days = listOf(DayUsage(today.minusDays(1), 50), DayUsage(today, 200))
        assertEquals(listOf(0.25f, 1f), UsageChart.heights(days))
        assertEquals(UsageChart.Summary(250, 125, 200), UsageChart.summary(days))
        assertEquals(listOf(0f), UsageChart.heights(listOf(DayUsage(today, 0))))
    }

    @Test
    fun sizes_pick_their_unit() {
        assertEquals("1.5" to UsageChart.SizeUnit.Gib, UsageChart.size(1_610_612_736, Locale.ENGLISH))
        assertEquals("300" to UsageChart.SizeUnit.Mib, UsageChart.size(300L * 1024 * 1024, Locale.ENGLISH))
        assertEquals("2" to UsageChart.SizeUnit.Kib, UsageChart.size(2048, Locale.ENGLISH))
    }
}
