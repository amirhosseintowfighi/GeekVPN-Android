package com.geekvpn.connection

import com.geekvpn.api.SubscriptionCard
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.ceil

/** A service as the Home, Services and Servers screens show it. */
data class ServiceStatus(
    val subscriptionId: String,
    val title: String,
    val active: Boolean,
    /** null: unlimited. */
    val quotaGib: Double?,
    val usedGib: Double,
    /** null: no expiry. Never negative. */
    val daysLeft: Int?,
    /** direct | tunnel | elite, or null for a service adopted from a link. */
    val tier: String?,
    val subscriptionUrl: String?,
) {
    /** 0..1 of the quota still left; 1 for unlimited. */
    val remainingFraction: Float
        get() {
            val quota = quotaGib ?: return 1f
            if (quota <= 0) return 0f
            return ((quota - usedGib) / quota).toFloat().coerceIn(0f, 1f)
        }

    val remainingGib: Double?
        get() = quotaGib?.let { (it - usedGib).coerceAtLeast(0.0) }

    companion object {
        fun of(card: SubscriptionCard, now: Instant, title: String): ServiceStatus? {
            val id = card.subscriptionId?.takeIf { it.isNotBlank() } ?: return null
            return ServiceStatus(
                subscriptionId = id,
                title = title,
                active = card.state == "active",
                quotaGib = card.quotaGib?.toDouble(),
                usedGib = card.usedGib ?: 0.0,
                daysLeft = daysLeft(card.expiresAt, now),
                tier = card.tier,
                subscriptionUrl = card.subscriptionUrl,
            )
        }

        /** Whole days until [expiresAt], rounded up so "3 hours left" reads 1 day. */
        fun daysLeft(expiresAt: String?, now: Instant): Int? {
            if (expiresAt.isNullOrBlank()) return null
            val end = try {
                OffsetDateTime.parse(expiresAt).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    // The backend's naive UTC form, "2026-10-01T12:00:00".
                    Instant.parse("${expiresAt}Z")
                } catch (_: DateTimeParseException) {
                    return null
                }
            }
            val millis = Duration.between(now, end).toMillis()
            if (millis <= 0) return 0
            return ceil(millis / 86_400_000.0).toInt()
        }
    }
}
