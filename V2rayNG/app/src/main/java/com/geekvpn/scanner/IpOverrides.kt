package com.geekvpn.scanner

import android.content.Context
import com.geekvpn.GeekStorage
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.util.LogUtil

/**
 * Where a clean address meets the config. v2rayNG's `CoreConfigContextBuilder`
 * passes every profile it is about to turn into Xray JSON through [apply],
 * for connecting and for the real-delay test alike, so the stored config never
 * changes and the override counts only on the network it was found on.
 *
 * Runs in the VPN service's process too; everything here reads MMKV, which is
 * opened multi-process.
 */
object IpOverrides {
    private val store by lazy { ScanStore(GeekStorage.open(STORE_ID)) }

    /** [profile] with the clean address in place of its own, or [profile] unchanged. */
    fun apply(context: Context, profile: ProfileItem): ProfileItem {
        return try {
            val target = CdnTarget.of(profile) ?: return profile
            // Only where the domain is known to be on Cloudflare: a clean address
            // in front of a server the CDN does not carry breaks the config.
            if (store.cdnVerdict(target)?.behind != true) return profile
            val network = NetworkIdentity.current(context)
            val override = store.override(ProfileKey.of(profile), network.key) ?: return profile
            withAddress(profile, target, override.ip)
        } catch (e: Exception) {
            // Never the reason a connection fails: fall back to the config as written.
            LogUtil.w(AppConfig.TAG, "IpOverrides: override not applied", e)
            profile
        }
    }

    /**
     * The address becomes the IP; SNI and Host keep naming the domain, which
     * is what Cloudflare routes on. Written out even where the config left
     * them to default from the address, since the address is now an IP.
     */
    fun withAddress(profile: ProfileItem, target: CdnTarget, ip: String): ProfileItem =
        if (profile.network == "grpc") {
            profile.copy(server = ip, sni = target.sni, authority = profile.authority?.ifBlank { null } ?: target.host)
        } else {
            profile.copy(server = ip, sni = target.sni, host = profile.host?.ifBlank { null } ?: target.host)
        }

    const val STORE_ID = "GEEK_SCAN"
}
