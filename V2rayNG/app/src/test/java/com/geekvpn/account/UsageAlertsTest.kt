package com.geekvpn.account

import com.geekvpn.api.SubscriptionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UsageAlertsTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private fun card(
        id: String = "s1",
        used: Double = 10.0,
        quota: Int? = 50,
        expires: String? = "2026-10-27T12:00:00Z",
        state: String = "active",
    ) = SubscriptionCard(
        subscriptionId = id,
        productNameFa = "تانل",
        planNameFa = "ماهانه",
        state = state,
        expiresAt = expires,
        quotaGib = quota,
        usedGib = used,
        subscriptionUrl = "https://panel.example/sub/$id",
        remoteUsername = null,
        tier = "tunnel",
    )

    @Test
    fun a_healthy_service_raises_nothing() {
        assertTrue(UsageAlerts.due(listOf(card()), emptySet(), now).isEmpty())
    }

    @Test
    fun eighty_percent_of_the_traffic_used_warns_once() {
        val alert = UsageAlerts.due(listOf(card(used = 41.0)), emptySet(), now).single()
        assertEquals(UsageAlert.Kind.Quota, alert.kind)
        assertEquals(82, alert.amount)

        assertTrue(UsageAlerts.due(listOf(card(used = 45.0)), setOf(alert.key), now).isEmpty())
    }

    @Test
    fun three_days_or_fewer_left_warns_with_the_days() {
        val alert = UsageAlerts.due(listOf(card(expires = "2026-09-29T20:00:00Z")), emptySet(), now).single()
        assertEquals(UsageAlert.Kind.Expiry, alert.kind)
        assertEquals(3, alert.amount)
    }

    @Test
    fun a_renewal_arms_the_warning_again() {
        val first = UsageAlerts.due(listOf(card(used = 45.0)), emptySet(), now).single()
        val renewed = card(used = 46.0, quota = 50, expires = "2026-11-27T12:00:00Z")
        assertEquals(1, UsageAlerts.due(listOf(renewed), setOf(first.key), now).size)
    }

    @Test
    fun an_expired_unlimited_or_inactive_service_raises_nothing() {
        val cards = listOf(
            card(id = "a", expires = "2026-09-26T12:00:00Z"),
            card(id = "b", quota = null, used = 900.0),
            card(id = "c", used = 49.0, state = "suspended"),
        )
        assertTrue(UsageAlerts.due(cards, emptySet(), now).isEmpty())
    }
}
