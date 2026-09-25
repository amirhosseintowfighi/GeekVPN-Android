package com.geekvpn.ui.home

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geekvpn.GeekGraph
import com.geekvpn.auth.Session
import com.geekvpn.auth.TelegramLink
import com.geekvpn.ui.account.AccountActions
import com.geekvpn.ui.account.AccountScreen
import com.geekvpn.ui.account.AccountViewModel
import com.geekvpn.ui.common.GeekHeader
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekBottomNav
import com.geekvpn.ui.components.GeekTab
import com.geekvpn.ui.login.LaunchActivity
import com.geekvpn.ui.services.ServicesActions
import com.geekvpn.ui.services.ServicesScreen
import com.geekvpn.ui.shop.ShopPlaceholder
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.AboutActivity
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import com.v2ray.ang.ui.subscription.SubEditActivity
import com.v2ray.ang.ui.subscription.SubSettingActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.launch

/** What covers the tab content: the servers page or the route sheet. */
private enum class Overlay { None, Servers, Route }

/**
 * GeekVPN's main screen: the four tabs of the design (خانه، سرویس‌ها،
 * فروشگاه، حساب) over the shared backdrop. v2rayNG's own screens stay
 * reachable from Account ("تنظیمات پیشرفته", "پروفایل‌ها").
 */
class HomeActivity : HelperBaseComponentActivity() {
    private val home: HomeViewModel by viewModels()
    private val account: AccountViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) home.connect() else showMessage(R.string.geek_home_err_vpn_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GeekGraph.syncOnLaunch()
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    home.events.collect { event ->
                        when (event) {
                            is HomeEvent.Message -> showMessage(event.text)
                            is HomeEvent.Text -> Toast.makeText(this@HomeActivity, event.text, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                launch {
                    // Signed out here, by the server, or from guest mode: back to the login screens.
                    GeekGraph.session.session.collect { session ->
                        if (session == Session.SignedOut) {
                            startActivity(Intent(this@HomeActivity, LaunchActivity::class.java))
                            finish()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        home.onForeground(true)
    }

    override fun onStop() {
        home.onForeground(false)
        super.onStop()
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by home.uiState.collectAsStateWithLifecycle()
            val accountState by account.uiState.collectAsStateWithLifecycle()
            val logoutAsked by account.logoutAsked.collectAsStateWithLifecycle()
            var tab by rememberSaveable { mutableStateOf(GeekTab.Home) }
            var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
            val signedIn = accountState.session is Session.SignedIn

            BackHandler(enabled = overlay != Overlay.None || tab != GeekTab.Home) {
                if (overlay != Overlay.None) overlay = Overlay.None else tab = GeekTab.Home
            }

            GeekBackdrop {
                if (overlay == Overlay.Servers) {
                    ServersScreen(
                        state = state,
                        onBack = { overlay = Overlay.None },
                        onSelect = home::selectServer,
                        onAutoServerChange = home::setAutoServer,
                        onTest = home::testServers,
                        onUpdate = home::refreshAccount,
                        updating = state.updating,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            // Room for the tab bar's dock above the system navigation bar.
                            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        if (tab == GeekTab.Home) {
                            GeekHeader(
                                balance = state.balance,
                                onWallet = if (signedIn) ({ tab = GeekTab.Account }) else null,
                            )
                        }
                        when (tab) {
                            GeekTab.Home -> HomeScreen(
                                state = state,
                                onConnect = ::requestConnect,
                                onDisconnect = home::disconnect,
                                onOpenServers = { overlay = Overlay.Servers },
                                onOpenRoute = { overlay = Overlay.Route },
                                onAutoServerChange = home::setAutoServer,
                                onChooseService = { tab = GeekTab.Services },
                            )
                            GeekTab.Services -> ServicesScreen(
                                state = state,
                                isSignedIn = signedIn,
                                actions = servicesActions(openShop = { tab = GeekTab.Shop }),
                            )
                            GeekTab.Shop -> ShopPlaceholder(
                                onOpenBot = if (BuildConfig.BOT_USERNAME.isNotEmpty()) ::openBot else null,
                            )
                            GeekTab.Account -> AccountScreen(
                                state = accountState,
                                route = state.route,
                                autoServer = state.autoServer,
                                logoutAsked = logoutAsked,
                                actions = accountActions(
                                    openServers = { overlay = Overlay.Servers },
                                    openRoute = { overlay = Overlay.Route },
                                ),
                                onAutoUpdate = account::setAutoUpdate,
                                onTheme = account::setTheme,
                                onAskLogout = account::askLogout,
                                onLogout = account::logout,
                            )
                        }
                    }
                    GeekBottomNav(
                        selected = tab,
                        onSelect = { tab = it },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    )
                }
                if (overlay == Overlay.Route) {
                    Box(Modifier.fillMaxSize()) {
                        RouteSheet(
                            current = state.route,
                            onSave = {
                                home.setRoute(it)
                                overlay = Overlay.None
                            },
                            onPerApp = { startActivity(Intent(this@HomeActivity, PerAppProxyActivity::class.java)) },
                            onDismiss = { overlay = Overlay.None },
                        )
                    }
                }
            }
        }
    }

    /** The glasses button: ask for the VPN permission first when the mode needs it. */
    private fun requestConnect() {
        if (!SettingsManager.isVpnMode()) {
            home.connect()
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) home.connect() else vpnPermission.launch(intent)
    }

    private fun servicesActions(openShop: () -> Unit) = object : ServicesActions {
        override fun onBuy() = openShop()
        override fun onAddSubscription() = startActivity(Intent(this@HomeActivity, SubEditActivity::class.java))
        override fun onImportClipboard() {
            val clipboard = getSystemService(ClipboardManager::class.java)
            val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@HomeActivity)?.toString()
            home.importConfigs(text.orEmpty())
        }
        override fun onUse(subscriptionId: String) = home.selectService(subscriptionId)
        override fun onConnect() = requestConnect()
        override fun onDisconnect() = home.disconnect()
        override fun onRefresh() = home.refreshAccount()
        override fun onCopy(url: String) {
            getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.geek_brand), url))
            showMessage(R.string.geek_services_copied)
        }
        override fun onUseManual(groupId: String) = home.selectGroup(groupId)
    }

    private fun accountActions(openServers: () -> Unit, openRoute: () -> Unit) = object : AccountActions {
        override fun onWallet() = openBot()
        override fun onServers() = openServers()
        override fun onRoute() = openRoute()
        override fun onAdvanced() = startActivity(Intent(this@HomeActivity, MainActivity::class.java))
        override fun onProfiles() = startActivity(Intent(this@HomeActivity, SubSettingActivity::class.java))
        override fun onSupport() = openBot()
        override fun onAbout() = startActivity(Intent(this@HomeActivity, AboutActivity::class.java))
        override fun onLogin() = GeekGraph.signOut()
    }

    /** The bot in the Telegram app, else its t.me page. */
    private fun openBot() {
        val link = "https://t.me/${BuildConfig.BOT_USERNAME}"
        for (uri in listOfNotNull(TelegramLink.appUri(link), link)) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
                return
            } catch (e: ActivityNotFoundException) {
                LogUtil.i(AppConfig.TAG, "Home: no app for ${uri.substringBefore(':')} links", e)
            }
        }
        showMessage(R.string.geek_login_err_no_telegram)
    }

    private fun showMessage(text: Int) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
