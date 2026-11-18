package com.geekvpn.ui.sites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.routing.SiteLists
import com.geekvpn.routing.SiteRules
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SitesUiState(
    val loaded: Boolean = false,
    val lists: SiteLists = SiteLists(),
    val input: String = "",
    /** What was typed is not a site. */
    val invalid: Boolean = false,
    /** Something changed; a running connection takes it on its next start. */
    val changed: Boolean = false,
)

/** "سایت‌های استثنا" ([SiteRules] over v2rayNG's routing rulesets). */
class SitesViewModel(
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val state = MutableStateFlow(SitesUiState())
    val uiState: StateFlow<SitesUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val lists = withContext(Dispatchers.IO) { SiteRules.read(MmkvManager.decodeRoutingRulesets()) }
            state.update { it.copy(loaded = true, lists = lists) }
        }
    }

    fun type(text: String) = state.update { it.copy(input = text.take(MAX_INPUT), invalid = false) }

    /** Adds what was typed to the direct list or the VPN list. */
    fun add(direct: Boolean) {
        val site = SiteRules.normalize(state.value.input)
        if (site == null) {
            state.update { it.copy(invalid = true) }
            return
        }
        save(SiteRules.add(state.value.lists, site, direct))
        state.update { it.copy(input = "") }
    }

    fun remove(site: String) = save(SiteRules.remove(state.value.lists, site))

    private fun save(lists: SiteLists) {
        state.update { it.copy(lists = lists, changed = true) }
        viewModelScope.launch(Dispatchers.IO) {
            MmkvManager.encodeRoutingRulesets(SiteRules.write(MmkvManager.decodeRoutingRulesets(), lists))
            // HomeActivity rebuilds a running connection with it in onStart.
            SettingsChangeManager.makeRestartService()
        }
    }

    companion object {
        private const val MAX_INPUT = 300

        fun factory() = viewModelFactory { initializer { SitesViewModel() } }
    }
}
