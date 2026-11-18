package com.geekvpn.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanShareTest {

    @Test
    fun only_private_lan_addresses_hotspot_after_wifi() {
        val picked = LanShare.pick(
            listOf(
                "rmnet_data0" to "100.72.1.9", // the operator's CGNAT: not reachable from the LAN
                "tun0" to "10.10.14.1", // the VPN itself
                "ap0" to "192.168.43.1",
                "wlan0" to "192.168.1.34",
                "lo" to "127.0.0.1",
                "wlan0" to "192.168.1.34",
            ),
        )
        assertEquals(
            listOf(LanAddress("192.168.1.34", LanAddress.Kind.Wifi), LanAddress("192.168.43.1", LanAddress.Kind.Hotspot)),
            picked,
        )
    }

    @Test
    fun usb_tethering_and_private_ranges() {
        assertEquals(LanAddress.Kind.Usb, LanShare.pick(listOf("rndis0" to "10.42.0.1")).single().kind)
        assertTrue(LanShare.isPrivateV4("172.20.0.5"))
        assertFalse(LanShare.isPrivateV4("172.32.0.5"))
        assertFalse(LanShare.isPrivateV4("8.8.8.8"))
        assertFalse(LanShare.isPrivateV4("192.168.1"))
        assertFalse(LanShare.isPrivateV4("fe80::1"))
    }
}
