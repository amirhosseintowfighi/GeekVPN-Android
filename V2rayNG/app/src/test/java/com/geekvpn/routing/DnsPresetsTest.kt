package com.geekvpn.routing

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPresetsTest {
    @Test
    fun an_empty_setting_is_v2rayngs_default() {
        assertEquals(DnsPresets.Preset.Default, DnsPresets.of(""))
        assertEquals(DnsPresets.Preset.Default, DnsPresets.of(null))
        assertEquals(DnsPresets.Preset.Default, DnsPresets.of(AppConfig.DNS_PROXY))
    }

    @Test
    fun presets_are_recognised_with_stray_spaces() {
        assertEquals(DnsPresets.Preset.AdGuard, DnsPresets.of(" https://dns.adguard-dns.com/dns-query "))
    }

    @Test
    fun anything_else_is_custom() {
        assertNull(DnsPresets.of("8.8.8.8"))
        assertNull(DnsPresets.of("https://1.1.1.1/dns-query,8.8.8.8"))
    }

    @Test
    fun every_preset_is_a_server_v2rayng_accepts() {
        // SettingsManager.getRemoteDnsServers drops anything else and falls back to its default.
        DnsPresets.Preset.entries.forEach { preset ->
            assertTrue(preset.name, Utils.isCoreDNSAddress(preset.servers))
        }
    }
}
