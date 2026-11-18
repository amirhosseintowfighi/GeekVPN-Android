package com.geekvpn.routing

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import java.net.IDN

/** The customer's own exceptions: sites that always go direct, and sites that always use the VPN. */
data class SiteLists(val direct: List<String> = emptyList(), val proxy: List<String> = emptyList())

/**
 * "سایت‌های استثنا" on top of v2rayNG's routing rulesets. Each list is one
 * locked ruleset with a fixed id, first in the list, so it wins over the
 * route mode's rules and survives switching mode (v2rayNG keeps locked
 * rulesets when a preset replaces the others). Pure, so it is tested.
 */
object SiteRules {
    const val DIRECT_ID = "geekvpn-sites-direct"
    const val PROXY_ID = "geekvpn-sites-proxy"

    private val LABEL = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

    /**
     * A site as typed or pasted ("https://www.Example.com/path", "سایت.ir")
     * as a bare lowercase ASCII domain, or null when it is not one. A leading
     * "www." is dropped: v2rayNG's `domain:` rule covers every subdomain.
     */
    fun normalize(input: String): String? {
        var host = input.trim().lowercase().removePrefix("domain:")
        host = host.substringAfter("://")
        host = host.substringBefore('/').substringBefore('?').substringBefore('#')
        host = host.substringAfterLast('@').substringBefore(':').trimEnd('.')
        host = host.removePrefix("*.").removePrefix("www.")
        if (host.isEmpty()) return null
        val ascii = try {
            IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase()
        } catch (_: IllegalArgumentException) {
            return null
        }
        val labels = ascii.split('.')
        if (labels.size < 2 || ascii.length > 253) return null
        if (labels.any { !LABEL.matches(it) }) return null
        // A bare IP is not a site; v2rayNG routes those by IP rules.
        if (labels.all { label -> label.all(Char::isDigit) }) return null
        return ascii
    }

    /** The lists as stored in [rulesets]. */
    fun read(rulesets: List<RulesetItem>?): SiteLists {
        fun of(id: String) = rulesets.orEmpty().firstOrNull { it.id == id }?.domain.orEmpty()
            .map { it.removePrefix("domain:") }
        return SiteLists(direct = of(DIRECT_ID), proxy = of(PROXY_ID))
    }

    /** [rulesets] with our two rulesets replaced by [lists]; empty lists are left out. */
    fun write(rulesets: List<RulesetItem>?, lists: SiteLists): MutableList<RulesetItem> {
        val others = rulesets.orEmpty().filterNot { it.id == DIRECT_ID || it.id == PROXY_ID }
        val ours = listOfNotNull(
            ruleset(PROXY_ID, "GeekVPN: always through the VPN", AppConfig.TAG_PROXY, lists.proxy),
            ruleset(DIRECT_ID, "GeekVPN: always direct", AppConfig.TAG_DIRECT, lists.direct),
        )
        return (ours + others).toMutableList()
    }

    /** [site] added to one list and taken out of the other: a site is in at most one. */
    fun add(lists: SiteLists, site: String, direct: Boolean): SiteLists {
        val direct2 = lists.direct - site
        val proxy2 = lists.proxy - site
        return if (direct) SiteLists(direct2 + site, proxy2) else SiteLists(direct2, proxy2 + site)
    }

    fun remove(lists: SiteLists, site: String) = SiteLists(lists.direct - site, lists.proxy - site)

    private fun ruleset(id: String, remarks: String, outbound: String, sites: List<String>): RulesetItem? =
        if (sites.isEmpty()) {
            null
        } else {
            RulesetItem(
                id = id,
                remarks = remarks,
                domain = sites.map { "domain:$it" },
                outboundTag = outbound,
                enabled = true,
                locked = true,
            )
        }
}
