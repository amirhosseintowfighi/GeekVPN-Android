package com.geekvpn.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geekvpn.GeekGraph
import com.geekvpn.api.ApiException
import com.geekvpn.auth.Session
import com.geekvpn.lock.AppLock
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.ui.compose.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    /** Support answers not yet read, across the customer's tickets. */
    val unreadTickets: Int = 0,
)

/** Account.html: who is signed in, the wallet balance, and GeekVPN's own settings. */
class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = GeekGraph.connectionPrefs
    private val autoUpdate = MutableStateFlow(prefs.autoUpdate)
    private val appLock = MutableStateFlow(prefs.appLock)
    private val unreadTickets = MutableStateFlow(0)

    val uiState: StateFlow<AccountUiState> = combine(
        GeekGraph.session.session,
        GeekGraph.accountStore.balance,
        autoUpdate,
        ThemeManager.themeMode,
        appLock,
    ) { session, balance, auto, theme, lock ->
        AccountUiState(session, balance, auto, ThemeChoice.of(theme), lock)
    }.combine(unreadTickets) { state, unread -> state.copy(unreadTickets = unread) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUiState(session = GeekGraph.session.session.value))

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

    /** Counts unread support answers for the «تیکت‌های من» row; quiet on failure. */
    fun refreshTickets() {
        if (GeekGraph.session.session.value !is Session.SignedIn) {
            unreadTickets.value = 0
            return
        }
        viewModelScope.launch {
            unreadTickets.value = try {
                GeekGraph.api.tickets().sumOf { (it.unreadCount ?: 0).coerceAtLeast(0) }
            } catch (e: ApiException) {
                LogUtil.i(AppConfig.TAG, "Account: ticket count unavailable (${e.status})", e)
                unreadTickets.value
            }
        }
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
