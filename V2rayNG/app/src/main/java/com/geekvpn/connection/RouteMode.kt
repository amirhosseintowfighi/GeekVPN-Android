package com.geekvpn.connection

import android.content.Context
import com.v2ray.ang.enums.RoutingType
import com.v2ray.ang.handler.SettingsManager

/**
 * Route.html's three choices, on top of v2rayNG's own routing rulesets.
 * Picking one replaces the unlocked rulesets, exactly as v2rayNG's own
 * "import preset" does; locked rules the user added in advanced settings stay.
 */
enum class RouteMode(val key: String) {
    /** Iranian sites and addresses direct, everything else through the VPN. */
    Smart("smart"),

    /** Everything through the VPN except the local network. */
    Global("global"),

    /** Nothing through the VPN: the tunnel stays up but every rule says direct. */
    Direct("direct");

    fun apply(context: Context) {
        when (this) {
            Smart -> SettingsManager.resetRoutingRulesetsFromPresets(context, RoutingType.WHITE_IRAN)
            Global -> SettingsManager.resetRoutingRulesetsFromPresets(context, RoutingType.GLOBAL)
            Direct -> SettingsManager.resetRoutingRulesets(DIRECT_RULESETS)
        }
    }

    companion object {
        fun of(key: String?): RouteMode? = entries.firstOrNull { it.key == key }

        /** v2rayNG has no "all direct" preset; the same shape as its presets. */
        private const val DIRECT_RULESETS = """[
            {"remarks": "GeekVPN: direct LAN IP", "outboundTag": "direct", "ip": ["geoip:private"]},
            {"remarks": "GeekVPN: everything direct", "outboundTag": "direct", "port": "0-65535"}
        ]"""
    }
}
