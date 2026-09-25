package com.geekvpn.account

import com.geekvpn.api.SubscriptionCard
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last copy of the account the server gave us: its services (with the
 * quota, expiry and tier v2rayNG's subscription rows have no room for) and
 * the wallet balance. Written by [AccountSync]; the screens read it, so they
 * show something at once, offline included.
 */
class AccountStore(
    private val storage: MMKV,
    private val gson: Gson = Gson(),
) {
    private val servicesState = MutableStateFlow(loadServices())
    val services: StateFlow<List<SubscriptionCard>> = servicesState.asStateFlow()

    private val balanceState = MutableStateFlow(
        if (storage.containsKey(KEY_BALANCE)) storage.decodeLong(KEY_BALANCE) else null
    )

    /** Whole tomans; null until the first successful read. */
    val balance: StateFlow<Long?> = balanceState.asStateFlow()

    fun saveServices(cards: List<SubscriptionCard>) {
        storage.encode(KEY_SERVICES, gson.toJson(cards))
        servicesState.value = cards
    }

    fun saveBalance(tomans: Long) {
        storage.encode(KEY_BALANCE, tomans)
        balanceState.value = tomans
    }

    /** The service behind a v2rayNG subscription GUID, when it is one of the account's. */
    fun serviceFor(guid: String?): SubscriptionCard? {
        if (guid == null || !SubscriptionPlan.isAccountGuid(guid)) return null
        return services.value.firstOrNull { it.subscriptionId != null && SubscriptionPlan.guidOf(it.subscriptionId) == guid }
    }

    fun clear() {
        storage.removeValuesForKeys(arrayOf(KEY_SERVICES, KEY_BALANCE))
        servicesState.value = emptyList()
        balanceState.value = null
    }

    private fun loadServices(): List<SubscriptionCard> {
        val json = storage.decodeString(KEY_SERVICES) ?: return emptyList()
        return try {
            gson.fromJson<List<SubscriptionCard>>(json, object : TypeToken<List<SubscriptionCard>>() {}.type)
                .orEmpty()
        } catch (_: JsonParseException) {
            emptyList()
        }
    }

    private companion object {
        const val KEY_SERVICES = "services"
        const val KEY_BALANCE = "wallet_balance"
    }
}
