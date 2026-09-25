package com.geekvpn.ui.advanced

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R

/**
 * v2rayNG's settings, as GeekVPN's "advanced settings" shows them: the same
 * MMKV keys, defaults and dependencies as `SettingsActivity`, so the core
 * reads exactly what it always did, with GeekVPN's own screen on top.
 * v2rayNG's display-only options (list layout, its own theme and colours) are
 * left out: GeekVPN's screens do not use them.
 */
sealed interface AdvancedItem {
    val key: String
    @get:StringRes val title: Int

    data class Toggle(
        override val key: String,
        @param:StringRes override val title: Int,
        @param:StringRes val summary: Int?,
        val default: Boolean,
        /** Turning it on needs root, checked before the value is stored. */
        val needsRoot: Boolean = false,
    ) : AdvancedItem

    data class Text(
        override val key: String,
        @param:StringRes override val title: Int,
        val default: String,
        val number: Boolean = false,
        val password: Boolean = false,
        val check: Check = Check.None,
    ) : AdvancedItem

    data class Choice(
        override val key: String,
        @param:StringRes override val title: Int,
        @param:ArrayRes val entries: Int,
        @param:ArrayRes val values: Int,
        val default: String,
    ) : AdvancedItem

    /** Opens something outside these settings (system VPN page, help). */
    data class Link(
        override val key: String,
        @param:StringRes override val title: Int,
        @param:StringRes val summary: Int? = null,
    ) : AdvancedItem

    enum class Check { None, ObservatoryDuration, PositiveInt }
}

data class AdvancedSection(@param:StringRes val title: Int, val items: List<AdvancedItem>)

object AdvancedSettings {
    const val LINK_SYSTEM_VPN = "link|system_vpn"
    const val LINK_MODE_HELP = "link|mode_help"

    private fun toggle(key: String, title: Int, summary: Int?, default: Boolean, needsRoot: Boolean = false) =
        AdvancedItem.Toggle(key, title, summary, default, needsRoot)

    private fun text(key: String, title: Int, default: String, number: Boolean = false, password: Boolean = false, check: AdvancedItem.Check = AdvancedItem.Check.None) =
        AdvancedItem.Text(key, title, default, number, password, check)

    private fun choice(key: String, title: Int, entries: Int, values: Int, default: String) =
        AdvancedItem.Choice(key, title, entries, values, default)

    val sections: List<AdvancedSection> = listOf(
        AdvancedSection(
            R.string.title_mode_settings,
            listOf(
                choice(AppConfig.PREF_MODE, R.string.title_mode, R.array.mode_entries, R.array.mode_value, AppConfig.VPN),
                AdvancedItem.Link(LINK_MODE_HELP, R.string.title_mode_help),
                toggle(AppConfig.PREF_ROOT_MODE_ENABLE, R.string.title_root_mode_enabled, R.string.summary_root_mode_enabled, false, needsRoot = true),
                toggle(AppConfig.PREF_ROOT_LAN_SHARING, R.string.title_root_lan_sharing, R.string.summary_root_lan_sharing, false, needsRoot = true),
            ),
        ),
        AdvancedSection(
            R.string.title_vpn_settings,
            listOf(
                toggle(AppConfig.PREF_IPV6_ENABLED, R.string.title_pref_ipv6_enabled, R.string.summary_pref_ipv6_enabled, false),
                toggle(AppConfig.PREF_PREFER_IPV6, R.string.title_pref_prefer_ipv6, R.string.summary_pref_prefer_ipv6, false),
                toggle(AppConfig.PREF_LOCAL_DNS_ENABLED, R.string.title_pref_local_dns_enabled, R.string.summary_pref_local_dns_enabled, false),
                toggle(AppConfig.PREF_FAKE_DNS_ENABLED, R.string.title_pref_fake_dns_enabled, R.string.summary_pref_fake_dns_enabled, false),
                text(AppConfig.PREF_VPN_DNS, R.string.title_pref_vpn_dns, ""),
                toggle(AppConfig.PREF_APPEND_HTTP_PROXY, R.string.title_pref_append_http_proxy, R.string.summary_pref_append_http_proxy, false),
                choice(AppConfig.PREF_VPN_BYPASS_LAN, R.string.title_pref_vpn_bypass_lan, R.array.vpn_bypass_lan, R.array.vpn_bypass_lan_value, AppConfig.DEFAULT_VPN_BYPASS_LAN),
                choice(AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX, R.string.title_pref_vpn_interface_address, R.array.vpn_interface_address, R.array.vpn_interface_address_value, "0"),
                text(AppConfig.PREF_VPN_MTU, R.string.title_pref_vpn_mtu, "", number = true),
                toggle(AppConfig.PREF_USE_HEV_TUNNEL, R.string.title_pref_use_hev_tunnel, R.string.summary_pref_use_hev_tunnel, true),
                choice(AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, R.string.title_pref_hev_tunnel_loglevel, R.array.hev_tunnel_loglevel, R.array.hev_tunnel_loglevel, AppConfig.DEFAULT_HEV_TUNNEL_LOGLEVEL),
                text(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, R.string.title_pref_hev_tunnel_rw_timeout, "", number = true),
            ),
        ),
        AdvancedSection(
            R.string.title_core_settings,
            listOf(
                toggle(AppConfig.PREF_SNIFFING_ENABLED, R.string.title_pref_sniffing_enabled, R.string.summary_pref_sniffing_enabled, true),
                toggle(AppConfig.PREF_ROUTE_ONLY_ENABLED, R.string.title_pref_route_only_enabled, R.string.summary_pref_route_only_enabled, false),
                toggle(AppConfig.PREF_ENABLE_LOCAL_PROXY, R.string.title_pref_enable_local_proxy, R.string.summary_pref_enable_local_proxy, true),
                toggle(AppConfig.PREF_PROXY_SHARING, R.string.title_pref_proxy_sharing_enabled, R.string.summary_pref_proxy_sharing_enabled, false),
                toggle(AppConfig.PREF_DYNAMIC_SOCKS_PORT, R.string.title_pref_dynamic_socks_port, R.string.summary_pref_dynamic_socks_port, false),
                text(AppConfig.PREF_SOCKS_PORT, R.string.title_pref_socks_port, "", number = true),
                text(AppConfig.PREF_SOCKS_USERNAME, R.string.title_pref_socks_username, ""),
                text(AppConfig.PREF_SOCKS_PASSWORD, R.string.title_pref_socks_password, "", password = true),
                toggle(AppConfig.PREF_SOCKS_ENABLE_UDP, R.string.title_pref_socks_enable_udp, R.string.summary_pref_socks_enable_udp, AppConfig.DEFAULT_SOCKS_ENABLE_UDP),
                text(AppConfig.PREF_REMOTE_DNS, R.string.title_pref_remote_dns, ""),
                text(AppConfig.PREF_DOMESTIC_DNS, R.string.title_pref_domestic_dns, ""),
                text(AppConfig.PREF_DNS_HOSTS, R.string.title_pref_dns_hosts, ""),
                choice(AppConfig.PREF_LOGLEVEL, R.string.title_core_loglevel, R.array.core_loglevel, R.array.core_loglevel, "warning"),
                choice(
                    AppConfig.PREF_OUTBOUND_DOMAIN_RESOLVE_METHOD, R.string.title_outbound_domain_resolve_method,
                    R.array.outbound_domain_resolve_method, R.array.outbound_domain_resolve_method_value,
                    AppConfig.DEFAULT_OUTBOUND_DOMAIN_RESOLVE_METHOD,
                ),
            ),
        ),
        AdvancedSection(
            R.string.title_mux_settings,
            listOf(
                toggle(AppConfig.PREF_MUX_ENABLED, R.string.title_pref_mux_enabled, R.string.summary_pref_mux_enabled, false),
                text(AppConfig.PREF_MUX_CONCURRENCY, R.string.title_pref_mux_concurrency, "8", number = true),
                text(AppConfig.PREF_MUX_XUDP_CONCURRENCY, R.string.title_pref_mux_xudp_concurrency, AppConfig.DEFAULT_MUX_XUDP_CONCURRENCY, number = true),
                choice(AppConfig.PREF_MUX_XUDP_QUIC, R.string.title_pref_mux_xudp_quic, R.array.mux_xudp_quic_entries, R.array.mux_xudp_quic_value, "reject"),
            ),
        ),
        AdvancedSection(
            R.string.title_fragment_settings,
            listOf(
                toggle(AppConfig.PREF_FRAGMENT_ENABLED, R.string.title_pref_fragment_enabled, null, false),
                choice(AppConfig.PREF_FRAGMENT_PACKETS, R.string.title_pref_fragment_packets, R.array.fragment_packets, R.array.fragment_packets, "tlshello"),
                text(AppConfig.PREF_FRAGMENT_LENGTH, R.string.title_pref_fragment_length, "50-100"),
                text(AppConfig.PREF_FRAGMENT_INTERVAL, R.string.title_pref_fragment_interval, "10-20"),
                text(AppConfig.PREF_FRAGMENT_MAXSPLIT, R.string.title_pref_fragment_maxsplit, "10", number = true),
            ),
        ),
        AdvancedSection(
            R.string.title_observatory_settings,
            listOf(
                text(AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL, R.string.title_pref_observatory_least_ping_interval, AppConfig.OBSERVATORY_LEAST_PING_INTERVAL, check = AdvancedItem.Check.ObservatoryDuration),
                text(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_INTERVAL, R.string.title_pref_observatory_least_load_interval, AppConfig.OBSERVATORY_LEAST_LOAD_INTERVAL, check = AdvancedItem.Check.ObservatoryDuration),
                choice(
                    AppConfig.PREF_OBSERVATORY_LEAST_LOAD_METHOD, R.string.title_pref_observatory_least_load_method,
                    R.array.observatory_least_load_method, R.array.observatory_least_load_method, AppConfig.OBSERVATORY_LEAST_LOAD_METHOD,
                ),
                text(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_SAMPLING, R.string.title_pref_observatory_least_load_sampling, AppConfig.OBSERVATORY_LEAST_LOAD_SAMPLING, number = true, check = AdvancedItem.Check.PositiveInt),
                text(AppConfig.PREF_OBSERVATORY_LEAST_LOAD_TIMEOUT, R.string.title_pref_observatory_least_load_timeout, AppConfig.OBSERVATORY_LEAST_LOAD_TIMEOUT, check = AdvancedItem.Check.ObservatoryDuration),
            ),
        ),
        AdvancedSection(
            R.string.title_advanced,
            listOf(
                toggle(AppConfig.PREF_IS_BOOTED, R.string.title_pref_is_booted, R.string.summary_pref_is_booted, false),
                toggle(AppConfig.PREF_SPEED_ENABLED, R.string.title_pref_speed_enabled, R.string.summary_pref_speed_enabled, false),
                AdvancedItem.Link(LINK_SYSTEM_VPN, R.string.title_system_vpn_settings, R.string.summary_system_vpn_settings),
                text(AppConfig.PREF_DELAY_TEST_URL, R.string.title_pref_delay_test_url, ""),
                text(AppConfig.PREF_REAL_PING_CONCURRENCY, R.string.title_pref_real_ping_concurrency, "16", number = true),
                text(AppConfig.PREF_IP_API_URL, R.string.title_pref_ip_api_url, ""),
            ),
        ),
    )

    val items: Map<String, AdvancedItem> = sections.flatMap { it.items }.associateBy { it.key }

    /** The stored-or-default values the rules below read. */
    class Values(private val read: (AdvancedItem) -> Any) {
        fun bool(key: String): Boolean = read(items.getValue(key)) as Boolean
        fun string(key: String): String = read(items.getValue(key)) as String
    }

    /** v2rayNG's `enabled =` conditions, key by key. */
    fun enabled(key: String, v: Values): Boolean {
        val isVpn = v.string(AppConfig.PREF_MODE) == AppConfig.VPN
        val hevTun = isVpn && v.bool(AppConfig.PREF_USE_HEV_TUNNEL)
        val localProxy = v.bool(AppConfig.PREF_ENABLE_LOCAL_PROXY) || hevTun
        val mux = v.bool(AppConfig.PREF_MUX_ENABLED)
        val fragment = v.bool(AppConfig.PREF_FRAGMENT_ENABLED)
        return when (key) {
            AppConfig.PREF_LOCAL_DNS_ENABLED, AppConfig.PREF_VPN_BYPASS_LAN, AppConfig.PREF_VPN_INTERFACE_ADDRESS_CONFIG_INDEX,
            AppConfig.PREF_VPN_MTU, AppConfig.PREF_USE_HEV_TUNNEL -> isVpn
            AppConfig.PREF_FAKE_DNS_ENABLED -> isVpn && v.bool(AppConfig.PREF_LOCAL_DNS_ENABLED)
            AppConfig.PREF_VPN_DNS -> isVpn && !v.bool(AppConfig.PREF_LOCAL_DNS_ENABLED)
            AppConfig.PREF_APPEND_HTTP_PROXY, AppConfig.PREF_PROXY_SHARING, AppConfig.PREF_DYNAMIC_SOCKS_PORT,
            AppConfig.PREF_SOCKS_USERNAME, AppConfig.PREF_SOCKS_PASSWORD, AppConfig.PREF_SOCKS_ENABLE_UDP -> localProxy
            AppConfig.PREF_HEV_TUNNEL_LOGLEVEL, AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT -> hevTun
            AppConfig.PREF_ENABLE_LOCAL_PROXY -> !hevTun
            AppConfig.PREF_SOCKS_PORT -> localProxy && !v.bool(AppConfig.PREF_DYNAMIC_SOCKS_PORT)
            AppConfig.PREF_MUX_CONCURRENCY, AppConfig.PREF_MUX_XUDP_CONCURRENCY -> mux
            AppConfig.PREF_MUX_XUDP_QUIC -> mux && (v.string(AppConfig.PREF_MUX_XUDP_CONCURRENCY).toIntOrNull() ?: 0) >= 0
            AppConfig.PREF_FRAGMENT_PACKETS, AppConfig.PREF_FRAGMENT_LENGTH, AppConfig.PREF_FRAGMENT_INTERVAL,
            AppConfig.PREF_FRAGMENT_MAXSPLIT -> fragment
            else -> true
        }
    }

    /**
     * Setting [key] to [value] and what else changes with it, as v2rayNG does:
     * the hev tunnel needs the local proxy, and without the local proxy there
     * is no HTTP proxy to append.
     */
    fun changes(key: String, value: Any, v: Values): Map<String, Any> = buildMap {
        put(key, value)
        if (key == AppConfig.PREF_USE_HEV_TUNNEL && value == true && !v.bool(AppConfig.PREF_ENABLE_LOCAL_PROXY)) {
            put(AppConfig.PREF_ENABLE_LOCAL_PROXY, true)
        }
        if (key == AppConfig.PREF_ENABLE_LOCAL_PROXY && value == false && v.bool(AppConfig.PREF_APPEND_HTTP_PROXY)) {
            put(AppConfig.PREF_APPEND_HTTP_PROXY, false)
        }
    }

    /** The value to store for a text setting, or null when it is invalid. */
    fun checked(item: AdvancedItem.Text, input: String): String? = when (item.check) {
        AdvancedItem.Check.None -> input.trim()
        AdvancedItem.Check.ObservatoryDuration -> input.trim().takeIf { AppConfig.OBSERVATORY_DURATION_PATTERN.matches(it) }
        AdvancedItem.Check.PositiveInt -> input.trim().toIntOrNull()?.takeIf { it > 0 }?.toString()
    }
}
