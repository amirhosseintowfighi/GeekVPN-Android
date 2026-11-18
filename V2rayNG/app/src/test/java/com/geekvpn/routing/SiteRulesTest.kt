package com.geekvpn.routing

import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteRulesTest {

    @Test
    fun typed_and_pasted_sites_become_bare_domains() {
        assertEquals("example.com", SiteRules.normalize("https://www.Example.com/path?q=1"))
        assertEquals("sub.example.co.uk", SiteRules.normalize(" sub.example.co.uk:8443 "))
        assertEquals("example.com", SiteRules.normalize("*.example.com"))
        assertEquals("example.com", SiteRules.normalize("domain:example.com"))
        SiteRules.normalize("ایران.ir").let { assertTrue(it!!.startsWith("xn--") && it.endsWith(".ir")) }
        assertNull(SiteRules.normalize("localhost"))
        assertNull(SiteRules.normalize("1.2.3.4"))
        assertNull(SiteRules.normalize("bad_name.com"))
        assertNull(SiteRules.normalize(""))
    }

    @Test
    fun our_rulesets_go_first_locked_and_others_stay() {
        val preset = RulesetItem(id = "x", remarks = "preset", outboundTag = "direct", domain = listOf("geosite:ir"))
        val written = SiteRules.write(
            listOf(preset),
            SiteLists(direct = listOf("bank.ir"), proxy = listOf("example.com")),
        )
        assertEquals(listOf(SiteRules.PROXY_ID, SiteRules.DIRECT_ID, "x"), written.map { it.id })
        assertTrue(written.take(2).all { it.locked == true && it.enabled })
        assertEquals(listOf("domain:example.com"), written[0].domain)
        assertEquals("proxy", written[0].outboundTag)
        assertEquals("direct", written[1].outboundTag)

        assertEquals(SiteLists(listOf("bank.ir"), listOf("example.com")), SiteRules.read(written))
        // Emptied: our rulesets go away entirely.
        assertEquals(listOf("x"), SiteRules.write(written, SiteLists()).map { it.id })
    }

    @Test
    fun a_site_is_in_one_list_at_most() {
        val lists = SiteRules.add(SiteLists(), "example.com", direct = true)
        val moved = SiteRules.add(lists, "example.com", direct = false)
        assertEquals(SiteLists(emptyList(), listOf("example.com")), moved)
        assertEquals(SiteLists(), SiteRules.remove(moved, "example.com"))
    }
}
