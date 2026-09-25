package com.geekvpn.scanner

import com.google.gson.JsonParser
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannerTest {

    private fun profile(
        network: String? = "ws",
        security: String? = "tls",
        server: String? = "de1.cdn.example.com",
        sni: String? = null,
        host: String? = null,
        port: String? = "443",
    ) = ProfileItem(configType = EConfigType.VLESS).apply {
        this.network = network
        this.security = security
        this.server = server
        this.sni = sni
        this.host = host
        this.serverPort = port
        this.subscriptionId = "geek-123"
        this.remarks = "🇩🇪 Germany"
    }

    @Test
    fun a_ws_tls_config_on_a_domain_is_cdn_fronted() {
        assertEquals(CdnTarget("de1.cdn.example.com", "de1.cdn.example.com", 443), CdnTarget.of(profile()))
    }

    @Test
    fun sni_and_host_come_from_the_config_when_it_names_them() {
        val target = CdnTarget.of(profile(server = "104.18.1.1", sni = "Edge.Example.com", host = "edge.example.com,other.example.com", port = "2053"))
        assertEquals(CdnTarget("edge.example.com", "edge.example.com", 2053), target)
    }

    @Test
    fun anything_cloudflare_cannot_carry_is_not_cdn_fronted() {
        assertNull(CdnTarget.of(profile(network = "tcp")))
        assertNull(CdnTarget.of(profile(network = "kcp")))
        assertNull(CdnTarget.of(profile(security = "reality")))
        assertNull(CdnTarget.of(profile(security = null)))
        assertNull(CdnTarget.of(profile(server = "104.18.1.1")))
        assertNull(CdnTarget.of(profile(port = null)))
    }

    @Test
    fun domains_are_told_from_ip_literals() {
        assertTrue(CdnTarget.isDomain("a.example.com"))
        assertTrue(CdnTarget.isDomain("xn--mgba3a4f16a.ir"))
        assertFalse(CdnTarget.isDomain("104.18.1.1"))
        assertFalse(CdnTarget.isDomain("localhost"))
        assertFalse(CdnTarget.isDomain("-bad.example.com"))
        assertFalse(CdnTarget.isDomain("[2606:4700::1]"))
    }

    @Test
    fun the_scanner_is_offered_only_for_direct_services_and_manual_links() {
        val p = profile()
        assertEquals("g", CleanIpTarget.of("g", "t", p, "direct", isAccountService = true)?.guid)
        assertNull(CleanIpTarget.of("g", "t", p, "tunnel", isAccountService = true))
        assertNull(CleanIpTarget.of("g", "t", p, "elite", isAccountService = true))
        assertNull(CleanIpTarget.of("g", "t", p, null, isAccountService = true))
        assertEquals("g", CleanIpTarget.of("g", "t", p, null, isAccountService = false)?.guid)
        assertNull(CleanIpTarget.of("g", "t", profile(network = "tcp"), null, isAccountService = false))
    }

    @Test
    fun an_override_keeps_the_domain_in_sni_and_host() {
        val original = profile()
        val target = CdnTarget.of(original)!!
        val applied = IpOverrides.withAddress(original, target, "104.18.32.47")
        assertEquals("104.18.32.47", applied.server)
        assertEquals("de1.cdn.example.com", applied.sni)
        assertEquals("de1.cdn.example.com", applied.host)
        // The stored profile is never touched.
        assertEquals("de1.cdn.example.com", original.server)
        assertNull(original.sni)
    }

    @Test
    fun grpc_keeps_its_authority() {
        val original = profile(network = "grpc").apply { authority = "grpc.example.com" }
        val applied = IpOverrides.withAddress(original, CdnTarget.of(original)!!, "104.18.32.47")
        assertEquals("grpc.example.com", applied.authority)
        assertNull(applied.host)
    }

    @Test
    fun the_profile_key_survives_a_refresh_but_not_a_different_config() {
        val a = profile()
        val refreshed = profile().apply { addedTime = 1 }
        assertEquals(ProfileKey.of(a), ProfileKey.of(refreshed))
        assertNotEquals(ProfileKey.of(a), ProfileKey.of(profile(port = "2053")))
        assertNotEquals(ProfileKey.of(a), ProfileKey.of(profile().apply { subscriptionId = "geek-999" }))
    }

    @Test
    fun results_rank_by_latency_with_jitter_and_drop_duplicates() {
        val ranked = ScanStore.rank(
            listOf(
                CleanIp("1.1.1.1", latencyMs = 300, jitterMs = 2.0),
                CleanIp("2.2.2.2", latencyMs = 250, jitterMs = 40.0),
                CleanIp("3.3.3.3", latencyMs = 280, jitterMs = 1.0),
                CleanIp("3.3.3.3", latencyMs = 999),
            )
        )
        assertEquals(listOf("3.3.3.3", "1.1.1.1", "2.2.2.2"), ranked.map { it.ip })
    }

    @Test
    fun a_record_goes_stale_after_a_day() {
        val record = ScanRecord(emptyList(), scannedAt = 0)
        assertFalse(record.isStale(ScanRecord.STALE_AFTER_MS))
        assertTrue(record.isStale(ScanRecord.STALE_AFTER_MS + 1))
    }

    @Test
    fun the_scan_config_carries_the_domain_the_budget_and_known_addresses() {
        val json = JsonParser.parseString(
            ScanConfig.json(CdnTarget("a.example.com", "b.example.com", 2053), listOf("104.18.1.1"), downloadTest = true)
        ).asJsonObject
        assertEquals("a.example.com", json["sni"].asString)
        assertEquals("b.example.com", json["host"].asString)
        assertEquals(2053, json["port"].asInt)
        assertEquals(300, json["maxIps"].asInt)
        assertEquals(5, json["stopAfter"].asInt)
        assertEquals("chrome", json["fingerprint"].asString)
        assertEquals("104.18.1.1", json["preferIps"].asJsonArray[0].asString)
        assertTrue(json["download"].asJsonObject["enable"].asBoolean)
        assertTrue(json["jitter"].asJsonObject["enable"].asBoolean)
    }

    @Test
    fun networks_are_keyed_by_operator_or_by_what_dhcp_gave() {
        val irancell = NetworkIdentity.mobile("43235", "MTN Irancell")
        assertEquals("mobile:43235", irancell.key)
        assertEquals(R.string.geek_operator_irancell, irancell.operatorLabel)
        assertNull(NetworkIdentity.mobile("99999", "Other").operatorLabel)
        assertEquals(NetworkIdentity.UNKNOWN, NetworkIdentity.mobile("", null))
        val home = NetworkIdentity.wifi(listOf("192.168.1.1"), "lan")
        assertEquals(home, NetworkIdentity.wifi(listOf("192.168.1.1"), "lan"))
        assertNotEquals(home.key, NetworkIdentity.wifi(listOf("10.0.0.1"), null).key)
    }
}
