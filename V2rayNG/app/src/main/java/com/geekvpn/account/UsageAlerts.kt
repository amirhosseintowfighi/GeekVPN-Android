package com.geekvpn.account

import com.geekvpn.api.SubscriptionCard
import com.geekvpn.connection.ServiceStatus
import java.time.Instant

/** One warning about one service, with the key that stops it repeating. */
data class UsageAlert(
    val key: String,
    val subscriptionId: String,
    val serviceName: String,
    val kind: Kind,
    /** Percent of the quota used, or days left. */
    val amount: Int,
) {
    enum class Kind { Quota, Expiry }
}

/**
 * When to warn that a service is running out: at [QUOTA_WARN_FRACTION] of its
 * traffic used, and when [EXPIRY_WARN_DAYS] or fewer days are left. Pure.
 *
 * Each warning is sent once. Its key carries what a renewal changes (the
 * quota for traffic, the expiry for time), so a renewed service is warned
 * about again when it runs low again, and not before.
 */
object UsageAlerts {
    const val QUOTA_WARN_FRACTION = 0.8
    const val EXPIRY_WARN_DAYS = 3

    fun due(cards: List<SubscriptionCard>, sent: Set<String>, now: Instant): List<UsageAlert> =
        cards.flatMap { card -> alertsFor(card, now) }.filter { it.key !in sent }

    private fun alertsFor(card: SubscriptionCard, now: Instant): List<UsageAlert> {
        val status = ServiceStatus.of(card, now, SubscriptionPlan.remarksOf(card)) ?: return emptyList()
        if (!status.active) return emptyList()
        val id = status.subscriptionId
        return buildList {
            val quota = status.quotaGib
            if (quota != null && quota > 0 && status.usedGib / quota >= QUOTA_WARN_FRACTION) {
                add(
                    UsageAlert(
                        key = "quota|$id|$quota|${card.expiresAt.orEmpty()}",
                        subscriptionId = id,
                        serviceName = status.title,
                        kind = UsageAlert.Kind.Quota,
                        amount = (status.usedGib / quota * 100).toInt().coerceAtMost(100),
                    )
                )
            }
            val days = status.daysLeft
            if (days != null && days in 1..EXPIRY_WARN_DAYS) {
                add(
                    UsageAlert(
                        key = "expiry|$id|${card.expiresAt.orEmpty()}",
                        subscriptionId = id,
                        serviceName = status.title,
                        kind = UsageAlert.Kind.Expiry,
                        amount = days,
                    )
                )
            }
        }
    }
}
