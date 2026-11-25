package com.geekvpn.ui.perapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geekvpn.perapp.AppProfile
import com.geekvpn.perapp.AppProfileList
import com.geekvpn.perapp.AppProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «پروفایل برنامه‌ها»: the saved selections. Applying one is the activity's
 * job, through v2rayNG's own per-app ViewModel (see `PerAppActivity`).
 */
class AppProfilesViewModel(private val store: () -> AppProfiles = AppProfiles::open) : ViewModel() {
    private val state = MutableStateFlow<List<AppProfile>>(emptyList())
    val profiles: StateFlow<List<AppProfile>> = state.asStateFlow()

    init {
        viewModelScope.launch { state.value = withContext(Dispatchers.IO) { store().load() } }
    }

    /** @return false when the name is empty or there are too many profiles already. */
    fun save(name: String, bypass: Boolean, packages: Set<String>): Boolean {
        val next = AppProfileList.save(state.value, name, bypass, packages) ?: return false
        commit(next)
        return true
    }

    fun remove(id: String) = commit(AppProfileList.remove(state.value, id))

    private fun commit(next: List<AppProfile>) {
        state.value = next
        viewModelScope.launch(Dispatchers.IO) { store().store(next) }
    }
}
