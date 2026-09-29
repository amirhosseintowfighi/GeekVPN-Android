package com.geekvpn.widget

import com.geekvpn.account.SubscriptionPlan
import com.geekvpn.api.SubscriptionCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class WidgetInfoTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private fun card(id: String, state: String = "active") = SubscriptionCard(
        subscriptionId = id,
        productNameFa = "تانل",
        planNameFa = "ماهانه",
        state = state,
        expiresAt = "2026-10-27T12:00:00Z",
        quotaGib = 50,
        usedGib = 10.0,
        subscriptionUrl = null,
        remoteUsername = null,
        tier = "tunnel",
    )

    @Test
    fun the_selected_servers_service_wins() {
        val picked = WidgetInfo.pick(listOf(card("a"), card("b")), SubscriptionPlan.guidOf("b"), now)
        assertEquals("b", picked?.subscriptionId)
    }

    @Test
    fun a_manual_link_or_nothing_selected_falls_back_to_the_first_active() {
        assertEquals("a", WidgetInfo.pick(listOf(card("a"), card("b")), "manual-sub", now)?.subscriptionId)
        assertEquals("a", WidgetInfo.pick(listOf(card("a")), null, now)?.subscriptionId)
    }

    @Test
    fun an_expired_service_is_never_shown() {
        val cards = listOf(card("a", state = "expired"), card("b"))
        assertEquals("b", WidgetInfo.pick(cards, SubscriptionPlan.guidOf("a"), now)?.subscriptionId)
        assertNull(WidgetInfo.pick(listOf(card("a", state = "expired")), null, now))
    }
}
