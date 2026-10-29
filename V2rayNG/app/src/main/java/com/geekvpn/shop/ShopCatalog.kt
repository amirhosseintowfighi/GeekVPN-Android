package com.geekvpn.shop

import com.geekvpn.api.StorePlan
import com.geekvpn.api.StoreProduct
import com.geekvpn.api.Storefront
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One choice on the shop's "service type" switch: a category of the bot's
 * shop, named as the bot names it. [tier] is the tier of its products, kept
 * so a renewal can still find its category on a server that does not send
 * the category name.
 */
data class ServiceKind(val id: String, val name: String, val tier: String?)

/** A validity shown as "۳ ماهه" when it is whole months, else as days. */
data class DurationLabel(val count: Int, val months: Boolean)

/**
 * Turns the storefront into the design's pickers: a service-type switch, then
 * the durations sold in it, then the volumes sold for the chosen duration.
 * Each (duration, volume) pair is one real plan; nothing is priced here, the
 * server's quote is the price.
 *
 * The service type is the bot's category, and every product in it adds its
 * plans. It was the product tier, labelled with the model name, and showed
 * only one product of each tier - so a shop whose volumes are separate
 * products ("10GB", "20GB", "100GB") of one tier offered just one volume.
 */
object ShopCatalog {

    /** Categories with something to sell, in the server's order. */
    fun kinds(storefront: Storefront): List<ServiceKind> =
        storefront.categories.orEmpty().mapNotNull { category ->
            val id = category.categoryId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val selling = category.products.orEmpty().filter { plansOf(it).isNotEmpty() }
            if (selling.isEmpty()) return@mapNotNull null
            ServiceKind(id, category.nameFa?.takeIf { it.isNotBlank() } ?: id, selling.first().tier)
        }

    /** Where the switch starts: the category of a featured product, else the first. */
    fun defaultKind(storefront: Storefront): ServiceKind? {
        val kinds = kinds(storefront)
        val featured = categoryOf(storefront) { it.isFeatured == true }
        return kinds.firstOrNull { it.id == featured } ?: kinds.firstOrNull()
    }

    /** Every buyable plan of every product in the category. */
    fun plansFor(storefront: Storefront, kind: ServiceKind): List<StorePlan> =
        storefront.categories.orEmpty()
            .filter { it.categoryId == kind.id }
            .flatMap { it.products.orEmpty() }
            .flatMap { plansOf(it) }

    /** The category a renewed service belongs to: by its name, else by its tier. */
    fun kindFor(storefront: Storefront, categoryName: String?, tier: String?): ServiceKind? {
        val kinds = kinds(storefront)
        return kinds.firstOrNull { !categoryName.isNullOrBlank() && it.name == categoryName }
            ?: kinds.firstOrNull { tier != null && it.tier == tier }
    }

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

    /**
     * The smallest monthly volume a customer may type for one term's plans,
     * or null where the term sells nothing by volume. The server prices the
     * typed volume between the term's nearest plans and refuses below this.
     */
    fun customFloor(volumes: List<StorePlan>): Int? =
        volumes.mapNotNull { plan ->
            val quota = plan.quotaGib?.takeIf { it > 0 } ?: return@mapNotNull null
            val days = plan.durationDays ?: return@mapNotNull null
            (quota * 30 + days - 1) / days
        }.minOrNull()

    /** The plan that names the term (and kind) of a typed volume to the server. */
    fun customAnchor(volumes: List<StorePlan>): StorePlan? = volumes.firstOrNull { (it.quotaGib ?: 0) > 0 }

    /** The term's allowance for a monthly volume: the server's own rule. */
    fun termGib(monthlyGib: Int, days: Int): Int = monthlyGib * days / 30

    const val MAX_CUSTOM_GIB = 10_000

    fun durationLabel(days: Int): DurationLabel =
        if (days >= 30 && days % 30 == 0) DurationLabel(days / 30, months = true) else DurationLabel(days, months = false)

    private fun categoryOf(storefront: Storefront, wanted: (StoreProduct) -> Boolean): String? =
        storefront.categories.orEmpty().firstOrNull { category ->
            category.products.orEmpty().any { wanted(it) && plansOf(it).isNotEmpty() }
        }?.categoryId
}
