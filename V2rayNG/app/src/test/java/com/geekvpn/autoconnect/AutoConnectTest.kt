package com.geekvpn.autoconnect

import com.geekvpn.autoconnect.AutoConnect.Decision
import com.geekvpn.scanner.NetworkIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoConnectTest {
    private val cafe = NetworkIdentity.wifi(listOf("10.0.0.1"), "cafe.lan")
    private val home = NetworkIdentity.wifi(listOf("192.168.1.1"), null)

    private fun decide(
        identity: NetworkIdentity = cafe,
        enabled: Boolean = true,
        running: Boolean = false,
        hasServer: Boolean = true,
        canStart: Boolean = true,
    ) = AutoConnect.decide(enabled, identity, trusted = setOf(home.key), running, hasServer, canStart)

    @Test
    fun an_unknown_wifi_starts_the_vpn() {
        assertEquals(Decision.Start, decide())
    }

    @Test
    fun a_trusted_wifi_or_mobile_data_does_nothing() {
        assertEquals(Decision.Nothing, decide(identity = home))
        assertEquals(Decision.Nothing, decide(identity = NetworkIdentity.mobile("43235", "Irancell")))
    }

    @Test
    fun nothing_happens_when_off_running_or_without_a_server() {
        assertEquals(Decision.Nothing, decide(enabled = false))
        assertEquals(Decision.Nothing, decide(running = true))
        assertEquals(Decision.Nothing, decide(hasServer = false))
    }

    @Test
    fun where_android_forbids_a_background_start_it_asks() {
        assertEquals(Decision.Ask, decide(canStart = false))
    }
}
