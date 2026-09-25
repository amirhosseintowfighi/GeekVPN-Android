package com.geekvpn.shop

import com.geekvpn.api.StorePlan
import com.geekvpn.api.StoreProduct
import com.geekvpn.api.Storefront
import kotlin.math.abs
import kotlin.math.roundToInt

/** The product tiers the shop switches between, in the order they are shown. */
enum class Tier(val key: String) {
    Direct("direct"),
    Tunnel("tunnel"),
    Elite("elite"),
    ;

    companion object {
        fun of(key: String?): Tier? = entries.firstOrNull { it.key == key }
    }
}

/** A validity shown as "۳ ماهه" when it is whole months, else as days. */
data class DurationLabel(val count: Int, val months: Boolean)

/**
 * Turns the storefront into the design's pickers: a tier switch, then the
 * durations of that tier's product, then the volumes sold for the chosen
 * duration. Each (duration, volume) pair is one real plan; nothing is priced
 * here, the server's quote is the price.
 */
object ShopCatalog {

    /** Tiers with at least one product that has a plan to sell. */
    fun tiers(storefront: Storefront): List<Tier> {
        val present = products(storefront).mapNotNull { Tier.of(it.tier) }.toSet()
        return Tier.entries.filter { it in present }
    }

    /** Where the tier switch starts: the tier of a featured product, else the first. */
    fun defaultTier(storefront: Storefront): Tier? {
        val tiers = tiers(storefront)
        return tiers.firstOrNull { productFor(storefront, it)?.isFeatured == true } ?: tiers.firstOrNull()
    }

    /** The tier's product: the featured one, else the first the server listed. */
    fun productFor(storefront: Storefront, tier: Tier): StoreProduct? =
        products(storefront)
            .filter { Tier.of(it.tier) == tier }
            .sortedByDescending { it.isFeatured == true }
            .firstOrNull()

    /** Plans that can actually be bought: an id, a length and a price. */
    fun plansOf(product: StoreProduct?): List<StorePlan> =
        product?.plans.orEmpty().filter { !it.planId.isNullOrBlank() && (it.durationDays ?: 0) > 0 && it.price != null }

    fun durations(plans: List<StorePlan>): List<Int> = plans.mapNotNull { it.durationDays }.distinct().sorted()

    /** The plans of one duration, smallest volume first and unlimited last. */
    fun volumes(plans: List<StorePlan>, duration: Int): List<StorePlan> =
        plans.filter { it.durationDays == duration }.sortedWith(compareBy(nullsLast<Int>()) { it.quotaGib })

    /** Where the pickers start: the featured plan, else the smallest of the shortest duration. */
    fun defaultPlan(plans: List<StorePlan>): StorePlan? =
        plans.firstOrNull { it.isFeatured == true }
            ?: durations(plans).firstOrNull()?.let { volumes(plans, it).firstOrNull() }

    /**
     * The plan after picking another duration: the same volume when that
     * duration sells it, else the nearest one, so the customer's choice of
     * size survives a change of length.
     */
    fun withDuration(plans: List<StorePlan>, current: StorePlan?, duration: Int): StorePlan? {
        val options = volumes(plans, duration)
        val wanted = current?.quotaGib
        return options.firstOrNull { it.quotaGib == wanted }
            ?: if (wanted == null) {
                options.lastOrNull()
            } else {
                options.filter { it.quotaGib != null }.minByOrNull { abs(it.quotaGib!! - wanted) } ?: options.firstOrNull()
            }
    }

    /** The saving the storefront advertises for a plan, in whole percent; null when none. */
    fun discountPercent(plan: StorePlan?): Int? {
        val price = plan?.price ?: return null
        val was = plan.compareAtPrice ?: return null
        if (was <= price || was <= 0) return null
        return ((was - price) * 100.0 / was).roundToInt().takeIf { it > 0 }
    }

    fun durationLabel(days: Int): DurationLabel =
        if (days >= 30 && days % 30 == 0) DurationLabel(days / 30, months = true) else DurationLabel(days, months = false)

    private fun products(storefront: Storefront): List<StoreProduct> =
        storefront.categories.orEmpty().flatMap { it.products.orEmpty() }.filter { plansOf(it).isNotEmpty() }
}
