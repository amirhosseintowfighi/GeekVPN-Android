package com.geekvpn.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SignalQualityTest {

    @Test
    fun the_design_samples_map_to_the_bars_drawn_in_servers_html() {
        assertEquals(Signal(4, SignalTone.Good), SignalQuality.of(96))
        assertEquals(Signal(4, SignalTone.Good), SignalQuality.of(121))
        assertEquals(Signal(4, SignalTone.Good), SignalQuality.of(142))
        assertEquals(Signal(3, SignalTone.Good), SignalQuality.of(168))
        assertEquals(Signal(3, SignalTone.Good), SignalQuality.of(189))
        assertEquals(Signal(3, SignalTone.Good), SignalQuality.of(233))
        assertEquals(Signal(2, SignalTone.Fair), SignalQuality.of(388))
        assertEquals(Signal(1, SignalTone.Poor), SignalQuality.of(410))
    }

    @Test
    fun cut_points_belong_to_the_better_band() {
        assertEquals(4, SignalQuality.of(SignalQuality.GOOD_MAX_MS).bars)
        assertEquals(3, SignalQuality.of(SignalQuality.GOOD_MAX_MS + 1).bars)
        assertEquals(3, SignalQuality.of(SignalQuality.OK_MAX_MS).bars)
        assertEquals(2, SignalQuality.of(SignalQuality.OK_MAX_MS + 1).bars)
        assertEquals(2, SignalQuality.of(SignalQuality.FAIR_MAX_MS).bars)
        assertEquals(1, SignalQuality.of(SignalQuality.FAIR_MAX_MS + 1).bars)
    }

    @Test
    fun untested_and_failed_delays_light_no_bars() {
        // v2rayNG stores 0 for "not tested yet" and -1 for "test failed".
        assertEquals(Signal(0, SignalTone.Unknown), SignalQuality.of(0))
        assertEquals(Signal(0, SignalTone.Unknown), SignalQuality.of(-1))
    }
}
