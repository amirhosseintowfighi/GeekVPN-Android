package com.geekvpn.shop

import com.geekvpn.api.PaymentStart
import com.geekvpn.api.PendingPayment
import com.geekvpn.api.StoreCategory
import com.geekvpn.api.StorePlan
import com.geekvpn.api.StoreProduct
import com.geekvpn.api.Storefront
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShopCatalogTest {

    private fun plan(id: String, days: Int?, gib: Int?, price: Long? = 100, was: Long? = null, featured: Boolean = false) =
        StorePlan(id, "p", null, days, price, was, gib, 2, null, featured)

    private fun product(id: String, tier: String?, plans: List<StorePlan>, featured: Boolean = false) =
        StoreProduct(id, tier, id, null, featured, plans)

    private fun category(id: String, name: String, vararg products: StoreProduct) =
        StoreCategory(id, name, products.toList())

    private fun shop(vararg categories: StoreCategory) = Storefront(categories.toList(), 0, false)

    @Test
    fun the_service_types_are_the_bots_categories_by_name() {
        val front = shop(
            category("eu", "اروپا", product("t", "tunnel", listOf(plan("t1", 30, 10)))),
            category("empty", "خالی", product("d", "direct", emptyList())),
            category("game", "گیمینگ", product("e", "elite", listOf(plan("e1", 30, 10)))),
        )
        assertEquals(listOf("اروپا", "گیمینگ"), ShopCatalog.kinds(front).map { it.name })
    }

    @Test
    fun every_volume_of_a_category_is_offered_not_just_one_products() {
        // The reported shop: volumes sold as separate products of one tier,
        // of which only the first was ever shown.
        val front = shop(
            category(
                "eu", "اروپا",
                product("100", "direct", listOf(plan("v100", 30, 100))),
                product("10", "direct", listOf(plan("v10", 30, 10))),
                product("20", "direct", listOf(plan("v20", 30, 20))),
            ),
        )
        val kind = ShopCatalog.kinds(front).single()
        val plans = ShopCatalog.plansFor(front, kind)
        assertEquals(listOf("v10", "v20", "v100"), ShopCatalog.volumes(plans, 30).map { it.planId })
    }

    @Test
    fun the_shop_opens_on_the_category_of_the_featured_product() {
        val front = shop(
            category("a", "A", product("d", "direct", listOf(plan("d1", 30, 10)))),
            category("b", "B", product("t", "tunnel", listOf(plan("t1", 30, 10)), featured = true)),
        )
        assertEquals("b", ShopCatalog.defaultKind(front)?.id)
        assertEquals("a", ShopCatalog.defaultKind(shop(category("a", "A", product("d", "direct", listOf(plan("d1", 30, 10))))))?.id)
    }

    @Test
    fun a_renewal_finds_its_category_by_name_then_by_tier() {
        val front = shop(
            category("a", "اروپا", product("d", "direct", listOf(plan("d1", 30, 10)))),
            category("b", "گیمینگ", product("t", "tunnel", listOf(plan("t1", 30, 10)))),
        )
        assertEquals("b", ShopCatalog.kindFor(front, "گیمینگ", "direct")?.id)
        assertEquals("b", ShopCatalog.kindFor(front, null, "tunnel")?.id)
        assertNull(ShopCatalog.kindFor(front, "رفته", "elite"))
    }

    @Test
    fun plans_without_an_id_a_length_or_a_price_are_not_offered() {
        val plans = ShopCatalog.plansOf(
            product("p", "tunnel", listOf(plan("", 30, 10), plan("a", null, 10), plan("b", 30, 10, price = null), plan("c", 30, 10)))
        )
        assertEquals(listOf("c"), plans.map { it.planId })
    }

    @Test
    fun durations_and_volumes_come_sorted_with_unlimited_last() {
        val plans = listOf(plan("a", 60, 40), plan("b", 30, null), plan("c", 30, 40), plan("d", 30, 10))
        assertEquals(listOf(30, 60), ShopCatalog.durations(plans))
        assertEquals(listOf("d", "c", "b"), ShopCatalog.volumes(plans, 30).map { it.planId })
    }

    @Test
    fun the_default_is_the_featured_plan_else_the_smallest_of_the_shortest() {
        val plans = listOf(plan("a", 60, 10), plan("b", 30, 40), plan("c", 30, 20))
        assertEquals("c", ShopCatalog.defaultPlan(plans)?.planId)
        assertEquals("a", ShopCatalog.defaultPlan(plans + plan("a", 60, 10, featured = true))?.planId)
    }

    @Test
    fun changing_the_length_keeps_the_volume_or_takes_the_nearest() {
        val plans = listOf(plan("m1-40", 30, 40), plan("m2-40", 60, 40), plan("m2-50", 60, 50), plan("m3-20", 90, 20), plan("m3-100", 90, 100))
        val current = plans.first()
        assertEquals("m2-40", ShopCatalog.withDuration(plans, current, 60)?.planId)
        assertEquals("m3-20", ShopCatalog.withDuration(plans, current, 90)?.planId)
    }

    @Test
    fun unlimited_stays_unlimited_when_the_length_changes() {
        val plans = listOf(plan("a", 30, null), plan("b", 60, 10), plan("c", 60, null))
        assertEquals("c", ShopCatalog.withDuration(plans, plans.first(), 60)?.planId)
    }

    @Test
    fun the_discount_is_only_shown_for_a_real_saving() {
        assertEquals(20, ShopCatalog.discountPercent(plan("a", 30, 10, price = 80, was = 100)))
        assertNull(ShopCatalog.discountPercent(plan("a", 30, 10, price = 100, was = 100)))
        assertNull(ShopCatalog.discountPercent(plan("a", 30, 10, price = 100, was = null)))
    }

    @Test
    fun whole_months_read_as_months_anything_else_as_days() {
        assertEquals(DurationLabel(3, months = true), ShopCatalog.durationLabel(90))
        assertEquals(DurationLabel(7, months = false), ShopCatalog.durationLabel(7))
        assertEquals(DurationLabel(45, months = false), ShopCatalog.durationLabel(45))
    }

    @Test
    fun topup_amounts_accept_persian_digits_and_separators() {
        assertEquals(150_000L, TopupLimits.parse("۱۵۰٬۰۰۰"))
        assertEquals(150_000L, TopupLimits.parse("150,000"))
        assertNull(TopupLimits.parse(""))
        assertNull(TopupLimits.parse("abc"))
        assertNull(TopupLimits.parse("9".repeat(13)))
    }

    @Test
    fun a_payment_start_says_which_shape_arrived() {
        val pending = PendingPayment("p1", "r", 100_073, "card", "awaiting_proof", null)
        assertEquals(PaymentStart.Kind.Card, PaymentStart("6037", "Ali", "Melli", null, pending, null, null, null).kind)
        assertEquals(PaymentStart.Kind.Gateway, PaymentStart(null, null, null, null, null, "https://pay", null, "p").kind)
        assertEquals(PaymentStart.Kind.Unknown, PaymentStart(null, null, null, null, null, null, null, null).kind)
    }
}
