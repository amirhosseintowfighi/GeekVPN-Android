package com.geekvpn.account

import com.geekvpn.api.SubscriptionCard
import com.v2ray.ang.dto.entities.SubscriptionItem

/**
 * How the account's services map onto v2rayNG subscriptions: one subscription
 * per service, keyed `geek-<subscriptionId>` so it is found again on the next
 * sync and never confused with a link the customer added by hand.
 *
 * Pure: [plan] only decides; `AccountSync` applies the result.
 */
object SubscriptionPlan {
    const val GUID_PREFIX = "geek-"

    fun guidOf(subscriptionId: String): String = GUID_PREFIX + subscriptionId

    fun isAccountGuid(guid: String): Boolean = guid.startsWith(GUID_PREFIX)

    data class Upsert(
        val guid: String,
        val item: SubscriptionItem,
        /** The servers under it must be (re)downloaded now rather than on the next auto-update. */
        val fetch: Boolean,
    )

    data class Plan(val upserts: List<Upsert>, val removals: List<String>)

    /**
     * @param remote the account's services, as the backend lists them.
     * @param local every v2rayNG subscription on the device, by GUID.
     */
    fun plan(remote: List<SubscriptionCard>, local: Map<String, SubscriptionItem>): Plan {
        val upserts = remote.mapNotNull { card ->
            val id = card.subscriptionId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            // A service with no link yet (still being provisioned) has nothing to connect to.
            val url = card.subscriptionUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val guid = guidOf(id)
            val enabled = card.state == STATE_ACTIVE
            val existing = local[guid]
            val item = (existing?.copy() ?: SubscriptionItem()).apply {
                remarks = remarksOf(card)
                this.url = url
                this.enabled = enabled
                autoUpdate = true
            }
            val fetch = enabled && (
                existing == null ||
                    existing.url != url ||
                    !existing.enabled ||
                    existing.lastUpdated <= 0
                )
            if (existing == item && !fetch) null else Upsert(guid, item, fetch)
        }
        val kept = remote.mapNotNull { card ->
            val id = card.subscriptionId?.takeIf { it.isNotBlank() }
            if (id != null && !card.subscriptionUrl.isNullOrBlank()) guidOf(id) else null
        }.toSet()
        val removals = local.keys.filter { isAccountGuid(it) && it !in kept }.sorted()
        return Plan(upserts, removals)
    }

    /** The name shown in v2rayNG's group tabs. */
    fun remarksOf(card: SubscriptionCard): String =
        listOf(card.planNameFa, card.productNameFa, card.remoteUsername)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?: DEFAULT_REMARKS

    private const val STATE_ACTIVE = "active"
    private const val DEFAULT_REMARKS = "GeekVPN"
}
