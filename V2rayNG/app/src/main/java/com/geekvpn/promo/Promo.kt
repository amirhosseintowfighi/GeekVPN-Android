package com.geekvpn.promo

import com.geekvpn.GeekStorage
import com.geekvpn.api.AppPromo
import com.geekvpn.api.GeekApi
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The offer banner the operator runs from the admin panel's settings
 * (`GET /api/app/promo`): shown on Home and in the shop until it ends or the
 * customer closes it. Closing hides that offer only; a new one shows again.
 */
class Promos(private val api: GeekApi, private val store: MMKV) {
    private val current = MutableStateFlow<AppPromo?>(null)

    /** The offer to show, or null (none running, or closed). */
    val shown: StateFlow<AppPromo?> = current.asStateFlow()
    private var fetchedAt = 0L

    /** Asks the server, at most every [REFRESH_MS]; a failure keeps what was there. */
    suspend fun refresh(now: Long = System.currentTimeMillis()) {
        if (now - fetchedAt < REFRESH_MS) return
        val promo = try {
            api.promo().promo
        } catch (e: java.io.IOException) {
            LogUtil.w(AppConfig.TAG, "Promo: fetching the offer failed", e)
            return
        }
        fetchedAt = now
        current.value = promo?.takeIf { visible(it, dismissed()) }
    }

    fun dismiss(promo: AppPromo) {
        store.encode(KEY_DISMISSED, (dismissed() + key(promo)).toMutableSet())
        current.value = null
    }

    private fun dismissed(): Set<String> = store.decodeStringSet(KEY_DISMISSED).orEmpty()

    companion object {
        private const val KEY_DISMISSED = "dismissed"
        private const val REFRESH_MS = 30 * 60 * 1000L

        fun open(api: GeekApi) = Promos(api, GeekStorage.open("GEEK_PROMO"))

        /** What identifies one offer: the operator changing any of these is a new offer. */
        fun key(promo: AppPromo) = listOf(promo.titleFa, promo.couponCode, promo.until).joinToString("|") { it.orEmpty() }

        /** Pure, for the tests. */
        fun visible(promo: AppPromo, dismissed: Set<String>): Boolean =
            !promo.titleFa.isNullOrBlank() && key(promo) !in dismissed
    }
}
