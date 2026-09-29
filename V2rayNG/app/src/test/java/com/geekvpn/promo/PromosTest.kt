package com.geekvpn.promo

import com.geekvpn.api.AppPromo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromosTest {
    private val offer = AppPromo("۲۰٪ تخفیف", "این هفته", "AUTUMN", "2026-10-05")

    @Test
    fun an_offer_shows_until_it_is_closed() {
        assertTrue(Promos.visible(offer, emptySet()))
        assertFalse(Promos.visible(offer, setOf(Promos.key(offer))))
    }

    @Test
    fun a_changed_offer_is_a_new_one() {
        val next = offer.copy(couponCode = "WINTER")
        assertNotEquals(Promos.key(offer), Promos.key(next))
        assertTrue(Promos.visible(next, setOf(Promos.key(offer))))
    }

    @Test
    fun an_offer_without_a_title_never_shows() {
        assertFalse(Promos.visible(offer.copy(titleFa = " "), emptySet()))
    }
}
