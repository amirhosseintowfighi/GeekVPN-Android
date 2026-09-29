package com.geekvpn.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.GeekGraph
import com.geekvpn.api.AutoRenewResponse
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** One service's "renew from my wallet" switch, as the server last said. */
data class AutoRenewState(
    val enabled: Boolean,
    val available: Boolean,
    /** renewed | insufficient_funds | unavailable | pending, or null. */
    val lastResult: String? = null,
    val busy: Boolean = false,
)

/** `GET/PUT /api/miniapp/subscriptions/{id}/auto-renew`. */
interface AutoRenewPort {
    suspend fun get(subscriptionId: String): AutoRenewResponse
    suspend fun set(subscriptionId: String, enabled: Boolean): AutoRenewResponse
}

/**
 * The auto-renew switches on the service cards (the worker on the server does
 * the renewing). Loaded per service when the Services tab shows; a switch
 * flips at once and goes back if the server refuses.
 */
class AutoRenewViewModel(
    private val port: AutoRenewPort,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val state = MutableStateFlow<Map<String, AutoRenewState>>(emptyMap())
    val switches: StateFlow<Map<String, AutoRenewState>> = state.asStateFlow()

    private val failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A switch the server did not take; the screen says so. */
    val failed: SharedFlow<Unit> = failures

    /** Reads the switches of [subscriptionIds]; a reload refreshes the last result too. */
    fun load(subscriptionIds: List<String>) {
        subscriptionIds.distinct().forEach { id ->
            if (state.value[id]?.busy == true) return@forEach
            viewModelScope.launch {
                try {
                    val answer = port.get(id)
                    state.update { it + (id to answer.toState()) }
                } catch (e: IOException) {
                    // No switch rather than a wrong one; the next visit asks again.
                    LogUtil.w(AppConfig.TAG, "AutoRenew: reading $id failed", e)
                }
            }
        }
    }

    fun set(subscriptionId: String, enabled: Boolean) {
        val before = state.value[subscriptionId] ?: return
        if (before.busy || !before.available) return
        state.update { it + (subscriptionId to before.copy(enabled = enabled, busy = true)) }
        viewModelScope.launch {
            try {
                val answer = port.set(subscriptionId, enabled)
                state.update { it + (subscriptionId to answer.toState()) }
            } catch (e: IOException) {
                LogUtil.w(AppConfig.TAG, "AutoRenew: switching $subscriptionId failed", e)
                state.update { it + (subscriptionId to before) }
                failures.tryEmit(Unit)
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                AutoRenewViewModel(
                    object : AutoRenewPort {
                        override suspend fun get(subscriptionId: String) = GeekGraph.api.autoRenew(subscriptionId)
                        override suspend fun set(subscriptionId: String, enabled: Boolean) =
                            GeekGraph.api.setAutoRenew(subscriptionId, enabled)
                    },
                )
            }
        }
    }
}

private fun AutoRenewResponse.toState() =
    AutoRenewState(enabled = enabled == true, available = available == true, lastResult = lastResult)
