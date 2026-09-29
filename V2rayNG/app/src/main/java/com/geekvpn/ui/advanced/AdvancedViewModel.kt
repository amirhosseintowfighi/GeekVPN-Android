package com.geekvpn.ui.advanced

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.v2ray.ang.R
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.root.RootManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the settings live: v2rayNG's MMKV in the app, a map in tests. */
interface SettingsStore {
    fun bool(key: String, default: Boolean): Boolean
    fun string(key: String, default: String): String
    fun put(key: String, value: Any)
}

/** v2rayNG's own storage, with its "a setting changed" signal so a running connection is rebuilt. */
object MmkvSettingsStore : SettingsStore {
    override fun bool(key: String, default: Boolean) = MmkvManager.decodeSettingsBool(key, default)

    override fun string(key: String, default: String) = MmkvManager.decodeSettingsString(key, default) ?: default

    override fun put(key: String, value: Any) {
        when (value) {
            is Boolean -> MmkvManager.encodeSettings(key, value)
            is String -> MmkvManager.encodeSettings(key, value)
            else -> error("unsupported setting type for $key")
        }
        SettingsChangeManager.notifySettingChanged(key)
    }
}

data class AdvancedUiState(
    /** Every setting's current value (Boolean or String), by key; empty until loaded. */
    val values: Map<String, Any> = emptyMap(),
    val systemVpnSettings: Boolean = false,
    /** A root check is running. */
    val busy: Boolean = false,
) {
    val loaded: Boolean get() = values.isNotEmpty()

    fun rules(): AdvancedSettings.Values = AdvancedSettings.Values { values.getValue(it.key) }

    fun enabled(key: String): Boolean = loaded && AdvancedSettings.enabled(key, rules())
}

sealed interface AdvancedEvent {
    data class Message(@param:StringRes val text: Int) : AdvancedEvent
}

class AdvancedViewModel(
    private val store: SettingsStore,
    private val io: CoroutineDispatcher,
    /** Asks for root; true when granted. */
    private val requestRoot: suspend () -> Boolean,
    /** Whether the phone has a system VPN settings page to open. */
    private val systemVpnAvailable: () -> Boolean,
    /** The ViewModel's scope; tests pass one on their scheduler. */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val state = MutableStateFlow(AdvancedUiState())
    val uiState: StateFlow<AdvancedUiState> = state.asStateFlow()

    private val eventChannel = Channel<AdvancedEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val values = withContext(io) { AdvancedSettings.items.values.mapNotNull { item -> read(item)?.let { item.key to it } }.toMap() }
            val systemVpn = withContext(io) { systemVpnAvailable() }
            state.update { it.copy(values = values, systemVpnSettings = systemVpn) }
        }
    }

    fun setToggle(key: String, on: Boolean) {
        val item = AdvancedSettings.items[key] as? AdvancedItem.Toggle ?: return
        if (!state.value.enabled(key)) return
        if (on && item.needsRoot) {
            viewModelScope.launch {
                state.update { it.copy(busy = true) }
                val granted = requestRoot()
                state.update { it.copy(busy = false) }
                if (granted) apply(key, true) else eventChannel.send(AdvancedEvent.Message(R.string.toast_root_required))
            }
            return
        }
        apply(key, on)
    }

    fun setChoice(key: String, value: String) {
        if (AdvancedSettings.items[key] !is AdvancedItem.Choice || !state.value.enabled(key)) return
        apply(key, value)
    }

    /** Stores a text setting; false (and a message) when the input is not valid for it. */
    fun setText(key: String, input: String): Boolean {
        val item = AdvancedSettings.items[key] as? AdvancedItem.Text ?: return false
        if (!state.value.enabled(key)) return false
        val value = AdvancedSettings.checked(item, input)
        if (value == null) {
            eventChannel.trySend(
                AdvancedEvent.Message(
                    if (item.check == AdvancedItem.Check.PositiveInt) R.string.toast_invalid_observatory_sampling
                    else R.string.toast_invalid_observatory_duration
                )
            )
            return false
        }
        apply(key, value)
        return true
    }

    private fun apply(key: String, value: Any) {
        val current = state.value
        if (!current.loaded) return
        val changes = AdvancedSettings.changes(key, value, current.rules())
        state.update { it.copy(values = it.values + changes) }
        viewModelScope.launch(io) { changes.forEach { (k, v) -> store.put(k, v) } }
    }

    private fun read(item: AdvancedItem): Any? = when (item) {
        is AdvancedItem.Toggle -> store.bool(item.key, item.default)
        is AdvancedItem.Text -> store.string(item.key, item.default)
        is AdvancedItem.Choice -> store.string(item.key, item.default)
        is AdvancedItem.Link -> null
    }

    companion object {
        fun factory(application: Application) = viewModelFactory {
            initializer {
                AdvancedViewModel(
                    store = MmkvSettingsStore,
                    io = Dispatchers.IO,
                    requestRoot = { RootManager.refresh() },
                    // Android shows the VPN page, not a direct switch for always-on VPN.
                    systemVpnAvailable = {
                        Intent(Settings.ACTION_VPN_SETTINGS).resolveActivity(application.packageManager) != null
                    },
                )
            }
        }
    }
}
