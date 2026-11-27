package com.geekvpn.routing

import com.v2ray.ang.AppConfig

/**
 * «DNS»: a few well-known DNS-over-HTTPS resolvers for v2rayNG's remote DNS
 * (`PREF_REMOTE_DNS`, the resolver queries through the tunnel go to). The
 * setting stays v2rayNG's own, so «تنظیمات پیشرفته» shows and edits the
 * same value; anything that is not a preset there reads as custom here.
 * Pure, for the tests.
 */
object DnsPresets {
    enum class Preset(val servers: String) {
        /** v2rayNG's default. */
        Default(AppConfig.DNS_PROXY),
        Cloudflare("https://1.1.1.1/dns-query"),
        Google("https://dns.google/dns-query"),
        Quad9("https://dns.quad9.net/dns-query"),

        /** Blocks ad and tracker domains. */
        AdGuard("https://dns.adguard-dns.com/dns-query"),

        /** Blocks malware and adult domains. */
        Family("https://family.cloudflare-dns.com/dns-query"),
    }

    /** The preset [stored] is, or null for a value set by hand. An empty setting is v2rayNG's default. */
    fun of(stored: String?): Preset? {
        val value = stored?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(",").orEmpty()
        if (value.isEmpty()) return Preset.Default
        return Preset.entries.firstOrNull { it.servers == value }
    }
}
