package com.geekvpn.widget

import com.geekvpn.account.SubscriptionPlan
import com.geekvpn.api.SubscriptionCard
import com.geekvpn.connection.ServiceStatus
import java.time.Instant

/** Which service the home-screen widget shows. Pure, for the tests. */
object WidgetInfo {
    /**
     * The service behind the selected server when it is an active account
     * service; otherwise the first active one; null when there is none.
     */
    fun pick(cards: List<SubscriptionCard>, selectedSubscriptionGuid: String?, now: Instant): ServiceStatus? {
        val active = cards.mapNotNull { ServiceStatus.of(it, now, SubscriptionPlan.remarksOf(it)) }.filter { it.active }
        return active.firstOrNull { SubscriptionPlan.guidOf(it.subscriptionId) == selectedSubscriptionGuid }
            ?: active.firstOrNull()
    }
}
