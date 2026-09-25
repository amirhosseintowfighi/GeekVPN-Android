package com.geekvpn.ui.advanced

import com.v2ray.ang.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedSettingsTest {

    private class MapStore(val map: MutableMap<String, Any> = mutableMapOf()) : SettingsStore {
        val writes = mutableListOf<String>()
        override fun bool(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun string(key: String, default: String) = map[key] as? String ?: default
        override fun put(key: String, value: Any) {
            map[key] = value
            writes += key
        }
    }

    private class Harness(scope: TestScope, val store: MapStore = MapStore(), root: Boolean = false) {
        private val dispatcher = StandardTestDispatcher(scope.testScheduler)
        val viewModel = AdvancedViewModel(
            store = store,
            io = dispatcher,
            requestRoot = { root },
            systemVpnAvailable = { true },
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )
        val events = mutableListOf<AdvancedEvent>()

        init {
            CoroutineScope(SupervisorJob() + dispatcher).launch { viewModel.events.toList(events) }
        }
    }

    private fun TestScope.loaded(store: MapStore = MapStore(), root: Boolean = false): Harness {
        val h = Harness(this, store, root)
        testScheduler.advanceUntilIdle()
        return h
    }

    @Test
    fun every_setting_loads_with_v2rayngs_default() = runTest {
        val state = loaded().viewModel.uiState.value
        assertTrue(state.loaded)
        assertEquals(AppConfig.VPN, state.values[AppConfig.PREF_MODE])
        assertEquals(true, state.values[AppConfig.PREF_USE_HEV_TUNNEL])
        assertEquals("8", state.values[AppConfig.PREF_MUX_CONCURRENCY])
        assertTrue(state.systemVpnSettings)
    }

    @Test
    fun stored_values_win_over_defaults() = runTest {
        val h = loaded(MapStore(mutableMapOf(AppConfig.PREF_MUX_ENABLED to true, AppConfig.PREF_MUX_CONCURRENCY to "4")))
        assertEquals("4", h.viewModel.uiState.value.values[AppConfig.PREF_MUX_CONCURRENCY])
        assertTrue(h.viewModel.uiState.value.enabled(AppConfig.PREF_MUX_CONCURRENCY))
    }

    @Test
    fun dependent_settings_follow_their_switch() = runTest {
        val h = loaded()
        assertFalse(h.viewModel.uiState.value.enabled(AppConfig.PREF_FAKE_DNS_ENABLED))
        assertTrue(h.viewModel.uiState.value.enabled(AppConfig.PREF_VPN_DNS))
        h.viewModel.setToggle(AppConfig.PREF_LOCAL_DNS_ENABLED, true)
        assertTrue(h.viewModel.uiState.value.enabled(AppConfig.PREF_FAKE_DNS_ENABLED))
        assertFalse(h.viewModel.uiState.value.enabled(AppConfig.PREF_VPN_DNS))
    }

    @Test
    fun a_disabled_setting_cannot_be_changed() = runTest {
        val h = loaded()
        h.viewModel.setText(AppConfig.PREF_MUX_CONCURRENCY, "2")
        testScheduler.advanceUntilIdle()
        assertTrue(h.store.writes.isEmpty())
    }

    @Test
    fun proxy_only_mode_disables_the_vpn_options() = runTest {
        val h = loaded()
        h.viewModel.setChoice(AppConfig.PREF_MODE, "Proxy only")
        val state = h.viewModel.uiState.value
        assertFalse(state.enabled(AppConfig.PREF_VPN_MTU))
        assertFalse(state.enabled(AppConfig.PREF_USE_HEV_TUNNEL))
        // Without the hev tunnel the local proxy is the customer's choice again.
        assertTrue(state.enabled(AppConfig.PREF_ENABLE_LOCAL_PROXY))
    }

    @Test
    fun turning_off_the_local_proxy_also_drops_the_http_proxy() = runTest {
        val h = loaded(MapStore(mutableMapOf(AppConfig.PREF_USE_HEV_TUNNEL to false, AppConfig.PREF_APPEND_HTTP_PROXY to true)))
        h.viewModel.setToggle(AppConfig.PREF_ENABLE_LOCAL_PROXY, false)
        testScheduler.advanceUntilIdle()
        assertEquals(false, h.store.map[AppConfig.PREF_ENABLE_LOCAL_PROXY])
        assertEquals(false, h.store.map[AppConfig.PREF_APPEND_HTTP_PROXY])
    }

    @Test
    fun the_hev_tunnel_turns_the_local_proxy_back_on() = runTest {
        val h = loaded(MapStore(mutableMapOf(AppConfig.PREF_USE_HEV_TUNNEL to false, AppConfig.PREF_ENABLE_LOCAL_PROXY to false)))
        h.viewModel.setToggle(AppConfig.PREF_USE_HEV_TUNNEL, true)
        testScheduler.advanceUntilIdle()
        assertEquals(true, h.store.map[AppConfig.PREF_ENABLE_LOCAL_PROXY])
    }

    @Test
    fun an_invalid_duration_is_refused_with_a_message() = runTest {
        val h = loaded()
        assertFalse(h.viewModel.setText(AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL, "soon"))
        assertTrue(h.viewModel.setText(AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL, " 5m "))
        testScheduler.advanceUntilIdle()
        assertEquals("5m", h.store.map[AppConfig.PREF_OBSERVATORY_LEAST_PING_INTERVAL])
        assertEquals(1, h.events.size)
    }

    @Test
    fun root_mode_needs_root() = runTest {
        val refused = loaded(root = false)
        refused.viewModel.setToggle(AppConfig.PREF_ROOT_MODE_ENABLE, true)
        testScheduler.advanceUntilIdle()
        assertEquals(false, refused.viewModel.uiState.value.values[AppConfig.PREF_ROOT_MODE_ENABLE])
        assertEquals(1, refused.events.size)

        val granted = loaded(root = true)
        granted.viewModel.setToggle(AppConfig.PREF_ROOT_MODE_ENABLE, true)
        testScheduler.advanceUntilIdle()
        assertEquals(true, granted.store.map[AppConfig.PREF_ROOT_MODE_ENABLE])
    }

    @Test
    fun setting_keys_are_unique() {
        val keys = AdvancedSettings.sections.flatMap { it.items }.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }
}
