package com.geekvpn.share

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

/** One address other devices can reach this phone at, and the interface it is on. */
data class LanAddress(val ip: String, val kind: Kind) {
    enum class Kind { Wifi, Hotspot, Usb, Other }
}

/**
 * Where "اشتراک VPN" can be reached from. v2rayNG's own proxy-sharing
 * setting makes the core's local SOCKS inbound listen on every address (Xray's
 * socks inbound also speaks HTTP, on the same port); this finds the private
 * IPv4 addresses on the phone's LAN-facing interfaces.
 */
object LanShare {
    /** Private IPv4 addresses from (interface name, address) pairs, Wi-Fi and hotspot first. */
    fun pick(interfaces: List<Pair<String, String>>): List<LanAddress> =
        interfaces
            .filter { (name, ip) -> isPrivateV4(ip) && !name.startsWith("tun") && !name.startsWith("lo") }
            .map { (name, ip) -> LanAddress(ip, kindOf(name)) }
            .distinctBy { it.ip }
            .sortedBy { it.kind.ordinal }

    /** The phone's current addresses; blocking only briefly, safe off the main thread. */
    fun current(): List<LanAddress> {
        val pairs = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { iface ->
                    iface.inetAddresses.toList().filterIsInstance<Inet4Address>().map { iface.name to it.hostAddress.orEmpty() }
                }
        } catch (e: SocketException) {
            LogUtil.w(AppConfig.TAG, "LanShare: interfaces unavailable", e)
            emptyList()
        }
        return pick(pairs)
    }

    private fun kindOf(name: String): LanAddress.Kind = when {
        // The hotspot's own interface; "wlan1" on some vendors.
        name.startsWith("ap") || name.startsWith("swlan") || name == "wlan1" -> LanAddress.Kind.Hotspot
        name.startsWith("wlan") -> LanAddress.Kind.Wifi
        name.startsWith("rndis") || name.startsWith("usb") -> LanAddress.Kind.Usb
        else -> LanAddress.Kind.Other
    }

    /** 10/8, 172.16/12, 192.168/16: what a router or this phone's hotspot hands out. */
    fun isPrivateV4(ip: String): Boolean {
        val parts = ip.split('.').map { it.toIntOrNull() ?: return false }
        if (parts.size != 4 || parts.any { it !in 0..255 }) return false
        val (a, b) = parts
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }
}
