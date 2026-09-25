package com.geekvpn.connection

import com.geekvpn.api.SubscriptionCard
import com.geekvpn.connection.ConnectionPhase.Connecting
import com.geekvpn.connection.ConnectionPhase.Off
import com.geekvpn.connection.ConnectionPhase.On
import com.geekvpn.connection.ConnectionPhase.Stopping
import com.geekvpn.connection.ConnectionPhase.Testing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.Locale

class ConnectionLogicTest {

    @Test
    fun a_start_success_or_running_report_means_on() {
        assertEquals(On, ConnectionLogic.next(Connecting, ServiceSignal.StartSuccess))
        assertEquals(On, ConnectionLogic.next(Off, ServiceSignal.Running))
    }

    @Test
    fun a_stale_not_running_does_not_undo_a_start_in_progress() {
        assertEquals(Testing, ConnectionLogic.next(Testing, ServiceSignal.NotRunning))
        assertEquals(Connecting, ConnectionLogic.next(Connecting, ServiceSignal.NotRunning))
        assertEquals(Off, ConnectionLogic.next(On, ServiceSignal.NotRunning))
    }

    @Test
    fun a_late_running_report_does_not_undo_a_stop_in_progress() {
        assertEquals(Stopping, ConnectionLogic.next(Stopping, ServiceSignal.Running))
        assertEquals(Off, ConnectionLogic.next(Stopping, ServiceSignal.StopSuccess))
    }

    @Test
    fun a_failed_start_is_off() {
        assertEquals(Off, ConnectionLogic.next(Connecting, ServiceSignal.StartFailure))
    }

    @Test
    fun the_best_server_is_the_fastest_that_answered() {
        val delays = listOf(
            ConnectionLogic.Delay("a", 0),
            ConnectionLogic.Delay("b", -1),
            ConnectionLogic.Delay("c", 240),
            ConnectionLogic.Delay("d", 120),
            ConnectionLogic.Delay("e", 120),
        )
        assertEquals("d", ConnectionLogic.best(delays))
        assertNull(ConnectionLogic.best(listOf(ConnectionLogic.Delay("a", -1), ConnectionLogic.Delay("b", 0))))
        assertNull(ConnectionLogic.best(emptyList()))
    }

    @Test
    fun country_codes_come_from_flags_or_a_leading_code() {
        assertEquals("DE", ServerNames.countryCode("🇩🇪 Germany"))
        assertEquals("NL", ServerNames.countryCode("Amsterdam 🇳🇱"))
        assertEquals("US", ServerNames.countryCode("us-1 fast"))
        assertEquals("FR", ServerNames.countryCode("FR"))
        assertNull(ServerNames.countryCode("Germany"))
        assertNull(ServerNames.countryCode("سرور آلمان"))
    }

    @Test
    fun titles_drop_the_flag_but_never_become_empty() {
        assertEquals("Germany", ServerNames.title("🇩🇪 Germany"))
        assertEquals("🇩🇪", ServerNames.title("🇩🇪"))
    }

    @Test
    fun country_names_fall_back_to_the_code() {
        assertEquals("Germany", ServerNames.countryName("DE", Locale.ENGLISH))
        assertEquals("ZZ9", ServerNames.countryName("ZZ9", Locale.ENGLISH))
    }

    @Test
    fun days_left_round_up_and_never_go_negative() {
        val now = Instant.parse("2026-09-25T12:00:00Z")
        assertEquals(28, ServiceStatus.daysLeft("2026-10-23T12:00:00+00:00", now))
        assertEquals(1, ServiceStatus.daysLeft("2026-09-25T15:00:00Z", now))
        assertEquals(0, ServiceStatus.daysLeft("2026-09-01T00:00:00Z", now))
        // The backend's naive UTC form.
        assertEquals(2, ServiceStatus.daysLeft("2026-09-27T00:00:00", now))
        assertNull(ServiceStatus.daysLeft(null, now))
        assertNull(ServiceStatus.daysLeft("soon", now))
    }

    @Test
    fun a_service_card_becomes_a_status() {
        val card = SubscriptionCard(
            subscriptionId = "s1", productNameFa = "تانل", planNameFa = "۴۰ گیگ", state = "active",
            expiresAt = null, quotaGib = 40, usedGib = 12.4, subscriptionUrl = "https://x.invalid/s",
            remoteUsername = "u", tier = "tunnel",
        )
        val status = ServiceStatus.of(card, Instant.EPOCH, "۴۰ گیگ")!!
        assertEquals(27.6, status.remainingGib!!, 0.001)
        assertEquals(0.69f, status.remainingFraction, 0.001f)
        assertNull(status.daysLeft)
        assertNull(ServiceStatus.of(card.copy(subscriptionId = null), Instant.EPOCH, "x"))
        // Unlimited: nothing to run out of.
        assertEquals(1f, status.copy(quotaGib = null).remainingFraction)
    }

    @Test
    fun speed_comes_from_two_readings() {
        val meter = TrafficMeter()
        assertNull(meter.sample(1_000, 500, 0))
        assertEquals(TrafficMeter.Speed(2_000, 1_000), meter.sample(3_000, 1_500, 1_000))
        // A counter reset reads as no traffic, not as a negative speed.
        assertEquals(TrafficMeter.Speed(0, 0), meter.sample(10, 10, 2_000))
        assertNull(TrafficMeter().sample(-1, -1, 0))
    }

    @Test
    fun speeds_are_formatted_like_the_design() {
        assertEquals("2.48" to "MB/s", TrafficMeter.format(2_480_000))
        assertEquals("312" to "KB/s", TrafficMeter.format(312_000))
        assertEquals("80" to "B/s", TrafficMeter.format(80))
    }

    @Test
    fun the_exit_ip_is_read_from_check_host() {
        assertEquals("185.220.101.7", ExitIpLookup.parseIp("185.220.101.7\n"))
        assertEquals("2a01:4f8:c0c:1234::1", ExitIpLookup.parseIp("2a01:4f8:c0c:1234::1"))
        assertNull(ExitIpLookup.parseIp("<html>blocked</html>"))
        assertEquals("DE", ExitIpLookup.parseCountry("""<img src="/images/flags/de.png" alt="">"""))
        assertEquals("NL", ExitIpLookup.parseCountry("""{"country_code": "nl"}"""))
        assertNull(ExitIpLookup.parseCountry("<html></html>"))
    }

    @Test
    fun route_modes_round_trip_by_key() {
        RouteMode.entries.forEach { assertEquals(it, RouteMode.of(it.key)) }
        assertNull(RouteMode.of("nope"))
        assertNull(RouteMode.of(null))
    }
}
