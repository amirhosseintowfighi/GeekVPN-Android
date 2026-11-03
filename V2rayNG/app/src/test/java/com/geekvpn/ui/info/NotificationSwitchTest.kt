package com.geekvpn.ui.info

import com.geekvpn.api.NotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSwitchTest {
    @Test
    fun a_missing_switch_reads_as_on() {
        val parsed = NotificationPreferences(expiry = null, traffic = false, promos = null, news = true, quietHours = null)
        assertTrue(NotificationSwitch.Expiry.of(parsed))
        assertFalse(NotificationSwitch.Traffic.of(parsed))
        assertTrue(NotificationSwitch.QuietHours.of(parsed))
    }

    @Test
    fun setting_one_switch_keeps_the_others_and_fills_the_missing_ones() {
        val parsed = NotificationPreferences(expiry = null, traffic = false, promos = true, news = null, quietHours = true)
        val changed = NotificationSwitch.Promos.set(parsed, false)
        assertEquals(
            NotificationPreferences(expiry = true, traffic = false, promos = false, news = true, quietHours = true),
            changed,
        )
    }

    @Test
    fun every_switch_maps_to_its_own_field() {
        val all = NotificationPreferences().normalized()
        NotificationSwitch.entries.forEach { switch ->
            val off = switch.set(all, false)
            NotificationSwitch.entries.forEach { other -> assertEquals(other != switch, other.of(off)) }
        }
    }
}
