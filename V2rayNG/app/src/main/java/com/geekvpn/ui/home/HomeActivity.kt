package com.geekvpn.ui.home

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geekvpn.GeekGraph
import com.geekvpn.account.UsageNotifier
import com.geekvpn.autoconnect.AutoConnect
import com.geekvpn.push.Push
import com.geekvpn.auth.Session
import com.geekvpn.auth.TelegramLink
import com.geekvpn.scanner.CleanIp
import com.geekvpn.shop.Tier
import com.geekvpn.ui.about.AboutActivity
import com.geekvpn.ui.account.AccountActions
import com.geekvpn.ui.account.AccountScreen
import com.geekvpn.ui.account.AccountViewModel
import com.geekvpn.ui.advanced.AdvancedActivity
import com.geekvpn.ui.autoconnect.AutoConnectActivity
import com.geekvpn.ui.common.GeekHeader
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekBottomNav
import com.geekvpn.ui.components.GeekMotion
import com.geekvpn.ui.components.GeekTab
import com.geekvpn.ui.components.geekPage
import com.geekvpn.ui.links.LinkEditActivity
import com.geekvpn.ui.login.LaunchActivity
import com.geekvpn.ui.perapp.PerAppActivity
import com.geekvpn.ui.services.ServicesActions
import com.geekvpn.ui.services.ServicesScreen
import com.geekvpn.ui.referral.ReferralActivity
import com.geekvpn.ui.scanner.ScannerActions
import com.geekvpn.ui.scanner.ScannerEvent
import com.geekvpn.ui.scanner.ScannerScreen
import com.geekvpn.ui.scanner.ScannerViewModel
import com.geekvpn.ui.shop.CheckoutSheet
import com.geekvpn.ui.shop.DepositSheet
import com.geekvpn.ui.shop.ReceiptImage
import com.geekvpn.ui.shop.ShopActions
import com.geekvpn.ui.shop.ShopEvent
import com.geekvpn.ui.shop.ShopScreen
import com.geekvpn.ui.shop.ShopSheet
import com.geekvpn.ui.shop.ShopUiState
import com.geekvpn.ui.shop.ShopViewModel
import com.geekvpn.ui.shop.WalletSheet
import com.geekvpn.ui.speedtest.SpeedTestActivity
import com.geekvpn.ui.support.ReportActivity
import com.geekvpn.ui.support.TicketsActivity
import com.geekvpn.ui.theme.GeekTheme
import com.geekvpn.ui.update.UpdateActions
import com.geekvpn.ui.update.UpdateBanner
import com.geekvpn.ui.update.UpdateSheet
import com.geekvpn.update.AppUpdater
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What covers the tab content: the servers page or the route sheet. */
private enum class Overlay { None, Servers, Route, Scanner }

/**
 * GeekVPN's main screen: the four tabs of the design (خانه، سرویس‌ها،
 * فروشگاه، حساب) over the shared backdrop, with Servers and the scanner
 * opened on top of them.
 */
class HomeActivity : HelperBaseComponentActivity() {
    private val home: HomeViewModel by viewModels()
    private val account: AccountViewModel by viewModels()
    private val shop: ShopViewModel by viewModels()
    private val scanner: ScannerViewModel by viewModels()

    /** The open tab. Here rather than in composition so shop events can switch it. */
    private var tab by mutableStateOf(GeekTab.Home)

    /** The receipt for the open card-to-card payment; no storage permission needed. */
    private val receiptPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            shop.uploadReceipt { withContext(Dispatchers.IO) { ReceiptImage.read(contentResolver, uri) } }
        }
    }

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) home.connect() else showMessage(R.string.geek_home_err_vpn_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(STATE_TAB)?.let { saved -> GeekTab.entries.firstOrNull { it.name == saved }?.let { tab = it } }
        GeekGraph.syncOnLaunch()
        handlePaymentReturn(intent)
        handleRenew(intent)
        handleConnect(intent)
        UsageNotifier.schedule(this)
        // A force-stop drops the Wi-Fi callback; opening the app puts it back.
        lifecycleScope.launch(Dispatchers.IO) { AutoConnect.sync(applicationContext) }
        Push.init(applicationContext)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    home.events.collect { event ->
                        when (event) {
                            is HomeEvent.Message -> showMessage(event.text)
                            is HomeEvent.Text -> Toast.makeText(this@HomeActivity, event.text, Toast.LENGTH_LONG).show()
                            is HomeEvent.Failed -> Toast.makeText(
                                this@HomeActivity,
                                getString(R.string.geek_smart_failed, event.attempts),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
                launch { shop.events.collect { onShopEvent(it) } }
                launch {
                    scanner.events.collect { event ->
                        when (event) {
                            // The override is read when the config is built: a live connection needs rebuilding.
                            ScannerEvent.AddressChanged -> home.reconnectIfRunning()
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
        // v2rayNG's "a setting changed" signal: rebuild a running connection with it.
        if (SettingsChangeManager.consumeRestartService()) home.reconnectIfRunning()
        // Back from a gateway's page without its link: refresh anyway.
        shop.onReturn(null)
        // Not on Google Play: this is how a customer learns of a new version.
        AppUpdater.check()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePaymentReturn(intent)
        handleRenew(intent)
        handleConnect(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, tab.name)
    }

    /** From a "running out" notification: that service's renewal, in the shop. */
    private fun handleRenew(intent: Intent?) {
        val subscriptionId = intent?.getStringExtra(EXTRA_RENEW) ?: return
        intent.removeExtra(EXTRA_RENEW)
        servicesActions(openShop = { tab = GeekTab.Shop }).onRenew(subscriptionId)
    }

    /** From the tile, widget or a shortcut with «سرور: خودکار» on: smart connect (`QuickConnect`). */
    private fun handleConnect(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_CONNECT, false) != true) return
        intent.removeExtra(EXTRA_CONNECT)
        tab = GeekTab.Home
        lifecycleScope.launch {
            // A cold start reads the servers asynchronously.
            home.awaitServers()
            requestConnect()
        }
    }

    private fun handlePaymentReturn(intent: Intent?) {
        val result = intent?.getStringExtra(EXTRA_PAYMENT_RESULT) ?: return
        intent.removeExtra(EXTRA_PAYMENT_RESULT)
        tab = GeekTab.Shop
        shop.onReturn(result)
    }

    private fun onShopEvent(event: ShopEvent) {
        when (event) {
            is ShopEvent.Message -> showMessage(event.text)
            is ShopEvent.Text -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
            is ShopEvent.OpenUrl -> openGateway(event.url)
            ShopEvent.Purchased -> {
                tab = GeekTab.Services
                home.refreshAccount(announce = false)
            }
            ShopEvent.AccountChanged -> home.refreshAccount(announce = false)
        }
    }

    /** A bank's page in a Custom Tab; the browser when there is none. */
    private fun openGateway(url: String) {
        val uri = url.toUri()
        if (uri.scheme != "https") {
            LogUtil.w(AppConfig.TAG, "Shop: refusing a gateway link that is not https")
            showMessage(R.string.geek_shop_err_generic)
            return
        }
        try {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, uri)
        } catch (e: ActivityNotFoundException) {
            LogUtil.i(AppConfig.TAG, "Shop: no Custom Tabs browser, opening the gateway directly", e)
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: ActivityNotFoundException) {
                LogUtil.w(AppConfig.TAG, "Shop: no browser for the gateway", e2)
                showMessage(R.string.geek_shop_err_browser)
            }
        }
    }

    private fun copy(text: String, message: Int) {
        getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(getString(R.string.geek_brand), text))
        showMessage(message)
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
            val shopState by shop.uiState.collectAsStateWithLifecycle()
            var overlay by rememberSaveable { mutableStateOf(Overlay.None) }
            // Where the scanner's back button goes: Servers, or the tab it was opened from.
            var scannerBack by rememberSaveable { mutableStateOf(Overlay.None) }
            val scannerState by scanner.uiState.collectAsStateWithLifecycle()
            val openScanner: (() -> Unit)? = state.cleanIp?.let { target ->
                {
                    scanner.open(target)
                    scannerBack = overlay
                    overlay = Overlay.Scanner
                }
            }
            val signedIn = accountState.session is Session.SignedIn
            val update by AppUpdater.state.collectAsStateWithLifecycle()
            var updateOpen by rememberSaveable { mutableStateOf(false) }
            var dismissedVersion by remember { mutableStateOf(AppUpdater.dismissedVersion()) }
            // Install tapped before GeekVPN may install apps: say what to allow.
            var installNeedsPermission by remember { mutableStateOf(false) }
            val bannerOffer = update.offer?.takeIf { it.required || it.versionName != dismissedVersion }

            BackHandler(enabled = shopState.sheet != null || updateOpen || overlay != Overlay.None || tab != GeekTab.Home) {
                when {
                    updateOpen -> updateOpen = false
                    shopState.sheet != null -> shop.closeSheet()
                    overlay == Overlay.Scanner -> overlay = scannerBack
                    overlay != Overlay.None -> overlay = Overlay.None
                    else -> tab = GeekTab.Home
                }
            }
            LaunchedEffect(tab, accountState.session) {
                if (tab == GeekTab.Shop) shop.load()
            }

            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            // The Route sheet opens over the tabs; only Servers and Scanner replace them.
            val page = if (overlay == Overlay.Route) Overlay.None else overlay
            GeekBackdrop {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = { geekPage(forward = targetState.ordinal > initialState.ordinal, rtl = rtl) },
                    modifier = Modifier.fillMaxSize(),
                    label = "page",
                ) { shownPage ->
                    Box(Modifier.fillMaxSize()) {
                        if (shownPage == Overlay.Servers) {
                            ServersScreen(
                                state = state,
                                onBack = { overlay = Overlay.None },
                                onSelect = home::selectServer,
                                onAutoServerChange = home::setAutoServer,
                                onTest = home::testServers,
                                onUpdate = home::refreshAccount,
                                updating = state.updating,
                                onCleanIp = openScanner,
                                onFailoverChange = home::setFailover,
                                onFavorite = home::toggleFavorite,
                            )
                        } else if (shownPage == Overlay.Scanner) {
                            ScannerScreen(
                                state = scannerState,
                                actions = object : ScannerActions {
                                    override fun onBack() {
                                        overlay = scannerBack
                                    }
                                    override fun onStart() = scanner.start()
                                    override fun onStop() = scanner.stop()
                                    override fun onDownloadTest(enabled: Boolean) = scanner.setDownloadTest(enabled)
                                    override fun onUse(ip: CleanIp) = scanner.use(ip)
                                    override fun onRevert() = scanner.revert()
                                },
                            )
                        } else {
                            AnimatedContent(
                                targetState = tab,
                                transitionSpec = { geekPage(forward = targetState.ordinal > initialState.ordinal, rtl = rtl) },
                                modifier = Modifier.fillMaxSize(),
                                label = "tab",
                            ) { shownTab ->
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
                                    if (shownTab == GeekTab.Home) {
                                        GeekHeader(
                                            balance = state.balance,
                                            onWallet = if (signedIn) shop::openWallet else null,
                                        )
                                        AnimatedVisibility(bannerOffer != null, enter = GeekMotion.Reveal, exit = GeekMotion.Conceal) {
                                            bannerOffer?.let { offer ->
                                                UpdateBanner(
                                                    offer = offer,
                                                    onOpen = { updateOpen = true },
                                                    onClose = {
                                                        AppUpdater.dismiss(offer)
                                                        dismissedVersion = offer.versionName
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    when (shownTab) {
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
                                        GeekTab.Shop -> ShopScreen(state = shopState, actions = shopActions)
                                        GeekTab.Account -> AccountScreen(
                                            state = accountState,
                                            route = state.route,
                                            autoServer = state.autoServer,
                                            logoutAsked = logoutAsked,
                                            actions = accountActions(
                                                openServers = { overlay = Overlay.Servers },
                                                openRoute = { overlay = Overlay.Route },
                                                openCleanIp = { openScanner?.invoke() },
                                                openUpdate = {
                                                    if (update.offer != null) updateOpen = true else AppUpdater.check(force = true)
                                                },
                                            ),
                                            showCleanIp = openScanner != null,
                                            update = if (AppUpdater.enabled) update else null,
                                            onAutoUpdate = account::setAutoUpdate,
                                            onTheme = account::setTheme,
                                            onAskLogout = account::askLogout,
                                            onLogout = account::logout,
                                        )
                                    }
                                }
                            }
                            GeekBottomNav(
                                selected = tab,
                                onSelect = { tab = it },
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            )
                        }
                    }
                }
                ShopSheetHost(shopState)
                if (updateOpen && update.offer != null) {
                    Box(Modifier.fillMaxSize()) {
                        UpdateSheet(
                            state = update,
                            needsPermission = installNeedsPermission,
                            actions = object : UpdateActions {
                                override fun onDownload() = AppUpdater.download(this@HomeActivity)
                                override fun onCancel() = AppUpdater.cancelDownload()
                                override fun onInstall() {
                                    installNeedsPermission = !AppUpdater.install(this@HomeActivity)
                                }
                                override fun onLater() {
                                    update.offer?.let { offer ->
                                        AppUpdater.dismiss(offer)
                                        dismissedVersion = offer.versionName
                                    }
                                    updateOpen = false
                                }
                                override fun onDismiss() {
                                    updateOpen = false
                                }
                            },
                        )
                    }
                }
                if (overlay == Overlay.Route) {
                    Box(Modifier.fillMaxSize()) {
                        RouteSheet(
                            current = state.route,
                            onSave = {
                                home.setRoute(it)
                                overlay = Overlay.None
                            },
                            onPerApp = { startActivity(Intent(this@HomeActivity, PerAppActivity::class.java)) },
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

    /** Whatever payment sheet the shop has open, over every tab. */
    @Composable
    private fun ShopSheetHost(state: ShopUiState) {
        val sheet = state.sheet ?: return
        Box(Modifier.fillMaxSize()) {
            when (sheet) {
                is ShopSheet.Checkout -> CheckoutSheet(
                    sheet = sheet,
                    options = shop.optionsFor(sheet.purpose),
                    balance = state.balance,
                    busy = state.busy,
                    onChoose = shop::choose,
                    onDismiss = shop::closeSheet,
                )
                ShopSheet.Wallet -> WalletSheet(
                    balance = state.balance,
                    wallet = state.wallet,
                    busy = state.busy,
                    onTopup = shop::topup,
                    onPending = shop::openPending,
                    onDismiss = shop::closeSheet,
                )
                is ShopSheet.Deposit -> DepositSheet(
                    info = sheet.info,
                    uploading = state.uploading,
                    onCopy = ::copy,
                    onSendReceipt = {
                        receiptPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onDismiss = shop::closeSheet,
                )
            }
        }
    }

    private val shopActions = object : ShopActions {
        override fun onWallet() = shop.openWallet()
        override fun onLogin() = GeekGraph.signOut()
        override fun onRetry() = shop.load(force = true)
        override fun onTier(tier: Tier) = shop.selectTier(tier)
        override fun onDuration(days: Int) = shop.selectDuration(days)
        override fun onPlan(planId: String) = shop.selectPlan(planId)
        override fun onApplyCoupon(code: String) = shop.applyCoupon(code)
        override fun onClearCoupon() = shop.clearCoupon()
        override fun onCancelRenew() = shop.cancelRenew()
        override fun onPay() = shop.pay()
        override fun onTrial() = shop.claimTrial()
    }

    private fun servicesActions(openShop: () -> Unit) = object : ServicesActions {
        override fun onBuy() = openShop()
        override fun onRenew(subscriptionId: String) {
            val card = GeekGraph.accountStore.services.value.firstOrNull { it.subscriptionId == subscriptionId }
            val title = card?.productNameFa ?: card?.planNameFa ?: getString(R.string.geek_brand)
            shop.renew(subscriptionId, title, card?.tier)
            openShop()
        }
        override fun onAddSubscription() = startActivity(LinkEditActivity.intent(this@HomeActivity))
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
        override fun onEditManual(groupId: String) = startActivity(LinkEditActivity.intent(this@HomeActivity, groupId))
    }

    private fun accountActions(
        openServers: () -> Unit,
        openRoute: () -> Unit,
        openCleanIp: () -> Unit,
        openUpdate: () -> Unit,
    ) = object : AccountActions {
        override fun onWallet() = shop.openWallet()
        override fun onServers() = openServers()
        override fun onRoute() = openRoute()
        override fun onCleanIp() = openCleanIp()
        override fun onAdvanced() = startActivity(Intent(this@HomeActivity, AdvancedActivity::class.java))
        override fun onSupport() = openBot()
        override fun onAbout() = startActivity(Intent(this@HomeActivity, AboutActivity::class.java))
        override fun onUpdate() = openUpdate()
        override fun onReport() = startActivity(Intent(this@HomeActivity, ReportActivity::class.java))
        override fun onTickets() = startActivity(Intent(this@HomeActivity, TicketsActivity::class.java))
        override fun onAutoConnect() = startActivity(Intent(this@HomeActivity, AutoConnectActivity::class.java))
        override fun onSpeedTest() = startActivity(Intent(this@HomeActivity, SpeedTestActivity::class.java))
        override fun onReferral() = startActivity(Intent(this@HomeActivity, ReferralActivity::class.java))
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

    companion object {
        /** Set by `PaymentReturnActivity`: ok | pending | failed | unknown. */
        const val EXTRA_PAYMENT_RESULT = "com.geekvpn.extra.PAYMENT_RESULT"

        /** Set by `UsageNotifier`: the subscription whose renewal to open. */
        const val EXTRA_RENEW = "com.geekvpn.extra.RENEW"

        /** Set by `QuickConnect`: start smart connect once Home is up. */
        const val EXTRA_CONNECT = "com.geekvpn.extra.CONNECT"
        private const val STATE_TAB = "geek_tab"
    }
}
