package com.geekvpn.account

import com.geekvpn.api.SubscriptionCard
import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionPlanTest {

    private fun card(
        id: String? = "s1",
        url: String? = "https://panel.example/sub/abc",
        state: String? = "active",
        plan: String? = "ماهانه ۵۰ گیگ",
        product: String? = "تانل",
        remote: String? = "geek_1",
    ) = SubscriptionCard(
        subscriptionId = id,
        productNameFa = product,
        planNameFa = plan,
        state = state,
        expiresAt = null,
        quotaGib = 50,
        usedGib = 1.5,
        subscriptionUrl = url,
        remoteUsername = remote,
        tier = "tunnel",
    )

    @Test
    fun a_new_active_service_becomes_an_auto_updating_subscription_and_is_fetched() {
        val plan = SubscriptionPlan.plan(listOf(card()), emptyMap())

        assertEquals(emptyList<String>(), plan.removals)
        val upsert = plan.upserts.single()
        assertEquals("geek-s1", upsert.guid)
        assertEquals("ماهانه ۵۰ گیگ", upsert.item.remarks)
        assertEquals("https://panel.example/sub/abc", upsert.item.url)
        assertTrue(upsert.item.enabled)
        assertTrue(upsert.item.autoUpdate)
        assertTrue(upsert.fetch)
    }

    @Test
    fun an_unchanged_service_that_was_already_fetched_is_left_alone() {
        val existing = SubscriptionItem(
            remarks = "ماهانه ۵۰ گیگ",
            url = "https://panel.example/sub/abc",
            enabled = true,
            lastUpdated = 1_000,
            autoUpdate = true,
        )
        val plan = SubscriptionPlan.plan(listOf(card()), mapOf("geek-s1" to existing))
        assertEquals(SubscriptionPlan.Plan(emptyList(), emptyList()), plan)
    }

    @Test
    fun a_changed_link_keeps_the_local_settings_and_is_fetched_again() {
        val existing = SubscriptionItem(
            remarks = "ماهانه ۵۰ گیگ",
            url = "https://panel.example/sub/old",
            enabled = true,
            addedTime = 42,
            lastUpdated = 1_000,
            autoUpdate = true,
            filter = "DE",
        )
        val upsert = SubscriptionPlan.plan(listOf(card()), mapOf("geek-s1" to existing)).upserts.single()
        assertEquals("https://panel.example/sub/abc", upsert.item.url)
        assertEquals(42L, upsert.item.addedTime)
        assertEquals("DE", upsert.item.filter)
        assertTrue(upsert.fetch)
    }

    @Test
    fun an_expired_service_is_disabled_and_not_fetched() {
        val existing = SubscriptionItem(
            remarks = "ماهانه ۵۰ گیگ",
            url = "https://panel.example/sub/abc",
            enabled = true,
            lastUpdated = 1_000,
            autoUpdate = true,
        )
        val upsert = SubscriptionPlan.plan(listOf(card(state = "expired")), mapOf("geek-s1" to existing)).upserts.single()
        assertFalse(upsert.item.enabled)
        assertFalse(upsert.fetch)
    }

    @Test
    fun a_renewed_service_is_enabled_and_fetched() {
        val existing = SubscriptionItem(
            remarks = "ماهانه ۵۰ گیگ",
            url = "https://panel.example/sub/abc",
            enabled = false,
            lastUpdated = 1_000,
            autoUpdate = true,
        )
        val upsert = SubscriptionPlan.plan(listOf(card()), mapOf("geek-s1" to existing)).upserts.single()
        assertTrue(upsert.item.enabled)
        assertTrue(upsert.fetch)
    }

    @Test
    fun services_gone_from_the_account_are_removed_but_manual_subscriptions_stay() {
        val local = mapOf(
            "geek-s1" to SubscriptionItem(url = "https://panel.example/sub/abc", lastUpdated = 1),
            "geek-old" to SubscriptionItem(url = "https://panel.example/sub/old"),
            "manual-guid" to SubscriptionItem(url = "https://someone.else/sub"),
            "default" to SubscriptionItem(remarks = "Default"),
        )
        val plan = SubscriptionPlan.plan(listOf(card()), local)
        assertEquals(listOf("geek-old"), plan.removals)
    }

    @Test
    fun a_service_without_a_link_or_id_is_skipped_and_its_old_copy_removed() {
        val local = mapOf("geek-s1" to SubscriptionItem(url = "https://panel.example/sub/abc"))
        val plan = SubscriptionPlan.plan(listOf(card(url = null), card(id = null), card(id = " ")), local)
        assertEquals(emptyList<SubscriptionPlan.Upsert>(), plan.upserts)
        assertEquals(listOf("geek-s1"), plan.removals)
    }

    @Test
    fun an_empty_account_removes_every_account_subscription() {
        val local = mapOf(
            "geek-a" to SubscriptionItem(),
            "geek-b" to SubscriptionItem(),
            "manual" to SubscriptionItem(),
        )
        assertEquals(listOf("geek-a", "geek-b"), SubscriptionPlan.plan(emptyList(), local).removals)
    }

    @Test
    fun the_name_falls_back_from_plan_to_product_to_panel_username() {
        assertEquals("ماهانه ۵۰ گیگ", SubscriptionPlan.remarksOf(card()))
        assertEquals("تانل", SubscriptionPlan.remarksOf(card(plan = " ")))
        assertEquals("geek_1", SubscriptionPlan.remarksOf(card(plan = null, product = "")))
        assertEquals("GeekVPN", SubscriptionPlan.remarksOf(card(plan = null, product = null, remote = null)))
    }
}
