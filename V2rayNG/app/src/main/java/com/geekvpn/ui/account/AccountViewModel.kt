package com.geekvpn.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geekvpn.GeekGraph
import com.geekvpn.auth.Session
import com.geekvpn.lock.AppLock
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.ui.compose.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** v2rayNG's theme setting ("0" auto, "1" light, "2" dark), as Account.html offers it. */
enum class ThemeChoice(val mode: String) {
    Auto("0"),
    Light("1"),
    Dark("2");

    companion object {
        fun of(mode: String): ThemeChoice = entries.firstOrNull { it.mode == mode } ?: Auto
    }
}

data class AccountUiState(
    val session: Session = Session.SignedOut,
    val balance: Long? = null,
    val autoUpdate: Boolean = true,
    val theme: ThemeChoice = ThemeChoice.Auto,
    val appLock: Boolean = false,
)

/** Account.html: who is signed in, the wallet balance, and GeekVPN's own settings. */
class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = GeekGraph.connectionPrefs
    private val autoUpdate = MutableStateFlow(prefs.autoUpdate)
    private val appLock = MutableStateFlow(prefs.appLock)

    val uiState: StateFlow<AccountUiState> = combine(
        GeekGraph.session.session,
        GeekGraph.accountStore.balance,
        autoUpdate,
        ThemeManager.themeMode,
        appLock,
    ) { session, balance, auto, theme, lock ->
        AccountUiState(session, balance, auto, ThemeChoice.of(theme), lock)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState(session = GeekGraph.session.session.value))

    private val _logoutAsked = MutableStateFlow(false)
    val logoutAsked: StateFlow<Boolean> = _logoutAsked.asStateFlow()

    fun setAutoUpdate(enabled: Boolean) {
        prefs.autoUpdate = enabled
        autoUpdate.value = enabled
    }

    /** False when the phone has no screen lock or biometrics to ask for. */
    fun setAppLock(enabled: Boolean): Boolean {
        if (enabled && !AppLock.available(getApplication())) return false
        AppLock.setEnabled(enabled)
        appLock.value = enabled
        return true
    }

    fun setTheme(choice: ThemeChoice) {
        ThemeManager.setThemeMode(choice.mode)
    }

    fun askLogout(ask: Boolean) {
        _logoutAsked.value = ask
    }

    /** The tunnel may be using one of the services about to be removed, so it goes first. */
    fun logout() {
        _logoutAsked.value = false
        LauncherManager.stopService(getApplication())
        GeekGraph.signOut()
    }
}
