package com.geekvpn.scanner

import com.v2ray.ang.dto.entities.ProfileItem
import java.security.MessageDigest

/**
 * What a config needs for the clean-IP scanner to apply: it reaches its
 * server through Cloudflare, so any healthy Cloudflare address can carry it
 * as long as SNI and Host still name the customer's domain.
 */
data class CdnTarget(
    /** TLS server name; what a clean address must serve. */
    val sni: String,
    /** HTTP Host; the same domain in practice, kept apart because configs keep them apart. */
    val host: String,
    val port: Int,
) {
    companion object {
        /** Transports Cloudflare proxies (an HTTP request underneath). */
        private val CDN_NETWORKS = setOf("ws", "grpc", "xhttp", "httpupgrade")

        /**
         * The target of [profile], or null when it is not CDN-fronted: a
         * transport Cloudflare cannot carry, no TLS, or no domain to keep
         * in SNI and Host. The address may already be an IP (an override,
         * or a config written that way); the domain then comes from SNI/Host.
         */
        fun of(profile: ProfileItem): CdnTarget? {
            if (profile.network !in CDN_NETWORKS) return null
            if (profile.security != "tls") return null
            val port = profile.serverPort?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            val server = profile.server?.trim().orEmpty()
            val sni = profile.sni?.trim().orEmpty().ifEmpty { server }
            // grpc keeps its authority in `authority`, the others in `host`.
            val rawHost = if (profile.network == "grpc") profile.authority ?: profile.host else profile.host
            val host = rawHost?.trim()?.substringBefore(',').orEmpty().ifEmpty { sni }
            if (!isDomain(sni) || !isDomain(host)) return null
            return CdnTarget(sni.lowercase(), host.lowercase(), port)
        }

        /** A DNS name with a dot and letters in its last label; never an IP literal. */
        fun isDomain(value: String): Boolean {
            if (value.length > 253 || !value.contains('.')) return false
            val labels = value.trimEnd('.').split('.')
            if (labels.any { it.isEmpty() || it.length > 63 }) return false
            if (!labels.all { label -> label.all { it.isLetterOrDigit() || it == '-' } && !label.startsWith('-') && !label.endsWith('-') }) {
                return false
            }
            return labels.last().any { it.isLetter() }
        }
    }
}

/**
 * Which config an override belongs to. Not the v2rayNG GUID: a subscription
 * refresh replaces every profile with a new GUID, and the override has to
 * outlive that. The subscription, the name and the original address and port
 * together survive a refresh and change only when the config really does.
 */
object ProfileKey {
    fun of(profile: ProfileItem, originalServer: String? = profile.server): String {
        val identity = listOf(profile.subscriptionId, profile.remarks, originalServer.orEmpty(), profile.serverPort.orEmpty())
            .joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}

/** The selected config, when the scanner may be offered for it. */
data class CleanIpTarget(val guid: String, val title: String, val target: CdnTarget) {
    companion object {
        /**
         * Only for `direct` account services and for manual links: tunnel and
         * elite services go through our own servers, where a Cloudflare
         * address means nothing. And only for a CDN-fronted config.
         */
        fun of(guid: String, title: String, profile: ProfileItem, accountTier: String?, isAccountService: Boolean): CleanIpTarget? {
            if (isAccountService && accountTier != "direct") return null
            val target = CdnTarget.of(profile) ?: return null
            return CleanIpTarget(guid, title, target)
        }
    }
}
