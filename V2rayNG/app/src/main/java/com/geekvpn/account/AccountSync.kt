package com.geekvpn.account

import com.geekvpn.api.GeekApi
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Mirrors the account's services into v2rayNG subscriptions (see
 * [SubscriptionPlan]) and downloads their servers. From there v2rayNG's own
 * subscription auto-update keeps them fresh.
 */
class AccountSync(
    private val api: GeekApi,
    private val store: AccountStore,
) {
    private val mutex = Mutex()

    data class Result(val services: Int, val fetched: Int, val fetchFailures: Int)

    /** Throws `ApiException` when the service list cannot be read; local data is then left alone. */
    suspend fun sync(): Result = mutex.withLock {
        val remote = api.subscriptions()
        store.saveServices(remote)
        // The balance is extra: a failure here must not undo the services.
        try {
            api.wallet().balance?.let(store::saveBalance)
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "AccountSync: wallet read failed", e)
        }
        withContext(Dispatchers.IO) {
            val local = MmkvManager.decodeSubscriptions().associate { it.guid to it.subscription }
            val plan = SubscriptionPlan.plan(remote, local)

            plan.removals.forEach { guid ->
                LogUtil.i(AppConfig.TAG, "AccountSync: removing $guid")
                SettingsManager.removeSubscriptionWithDefault(guid)
            }

            var fetched = 0
            var failures = 0
            plan.upserts.forEach { upsert ->
                MmkvManager.encodeSubscription(upsert.guid, upsert.item)
                if (upsert.item.enabled) {
                    SubscriptionUpdater.syncOne(subId = upsert.guid)
                } else {
                    SubscriptionUpdater.cancelOne(subId = upsert.guid)
                }
            }
            // Also refetch a service whose servers were deleted by hand in v2rayNG's list.
            val toFetch = plan.upserts.filter { it.fetch }.map { it.guid }.toSet() +
                local.keys.filter {
                    SubscriptionPlan.isAccountGuid(it) &&
                        it !in plan.removals &&
                        MmkvManager.decodeServerList(it).isEmpty()
                }
            toFetch.forEach { guid ->
                val item = MmkvManager.decodeSubscription(guid) ?: return@forEach
                if (!item.enabled) return@forEach
                val result = AngConfigManager.updateConfigViaSub(SubscriptionCache(guid, item))
                if (result.successCount > 0) fetched++ else failures++
            }
            LogUtil.i(
                AppConfig.TAG,
                "AccountSync: ${remote.size} services, ${plan.upserts.size} changed, " +
                    "${plan.removals.size} removed, $fetched fetched, $failures failed"
            )
            Result(remote.size, fetched, failures)
        }
    }

    /**
     * Signing out takes the account's services off the device; manual links
     * stay. Blocking, for OkHttp's authenticator thread, which cannot suspend.
     */
    fun removeAllNow() {
        store.clear()
        MmkvManager.decodeSubscriptions()
            .map { it.guid }
            .filter { SubscriptionPlan.isAccountGuid(it) }
            .forEach { SettingsManager.removeSubscriptionWithDefault(it) }
    }
}
