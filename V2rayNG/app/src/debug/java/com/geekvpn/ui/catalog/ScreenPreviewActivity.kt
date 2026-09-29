package com.geekvpn.ui.catalog

import com.geekvpn.ui.update.UpdateActions
import com.geekvpn.ui.update.UpdateBanner
import com.geekvpn.ui.update.UpdateSheet
import com.geekvpn.update.UpdateState
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.geekvpn.connection.RouteMode
import com.geekvpn.scanner.CleanIp
import com.geekvpn.shop.Tier
import com.geekvpn.ui.account.AccountActions
import com.geekvpn.ui.account.AccountScreen
import com.geekvpn.ui.autoconnect.AutoConnectActions
import com.geekvpn.ui.autoconnect.AutoConnectScreen
import com.geekvpn.ui.autoconnect.AutoConnectUiState
import com.geekvpn.ui.common.GeekHeader
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekBottomNav
import com.geekvpn.ui.components.GeekTab
import com.geekvpn.ui.home.HomeScreen
import com.geekvpn.ui.home.RouteSheet
import com.geekvpn.ui.home.ServersScreen
import com.geekvpn.ui.login.LinkPurpose
import com.geekvpn.ui.login.SyncingScreen
import com.geekvpn.ui.login.UsernameScreen
import com.geekvpn.ui.login.WaitingScreen
import com.geekvpn.ui.scanner.ScannerActions
import com.geekvpn.ui.scanner.ScannerScreen
import com.geekvpn.ui.services.ServicesActions
import com.geekvpn.ui.services.ServicesScreen
import com.geekvpn.ui.shop.CheckoutSheet
import com.geekvpn.ui.shop.DepositSheet
import com.geekvpn.ui.shop.ShopActions
import com.geekvpn.ui.shop.ShopScreen
import com.geekvpn.ui.shop.ShopSheet
import com.geekvpn.ui.shop.WalletSheet
import com.geekvpn.support.TicketTopic
import com.geekvpn.ui.support.TicketsActions
import com.geekvpn.ui.support.TicketsScreen
import com.geekvpn.api.ReferralSummary
import com.geekvpn.speedtest.SpeedResult
import com.geekvpn.ui.referral.ReferralScreen
import com.geekvpn.ui.referral.ReferralUiState
import com.geekvpn.ui.speedtest.SpeedTestScreen
import com.geekvpn.ui.speedtest.SpeedTestUiState
import com.geekvpn.lock.LockScreen
import com.geekvpn.ui.theme.GeekTheme
import com.geekvpn.ui.usage.UsageScreen
import com.geekvpn.ui.usage.UsageSource
import com.geekvpn.ui.usage.UsageUiState
import com.geekvpn.usage.DayUsage
import com.v2ray.ang.ui.base.BaseComponentActivity

/**
 * Debug-only: one screen with sample data, for design review and the CI
 * screenshots of states the emulator cannot reach on its own (the waiting
 * screen needs the API, which GitHub's runners do not reach; the tabs need an
 * account with services). Buttons do nothing here.
 *
 *   am start -n <pkg>/com.geekvpn.ui.catalog.ScreenPreviewActivity --es screen waiting --ez dark false
 */
class ScreenPreviewActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        val dark = intent.getBooleanExtra(CatalogActivity.EXTRA_DARK, false)
        // A fixed 4:32 left, so screenshots are comparable run to run.
        val expiresAt = remember { System.currentTimeMillis() + 272_000L }
        GeekTheme(darkTheme = dark) {
            when (val screen = intent.getStringExtra(EXTRA_SCREEN).orEmpty()) {
                SCREEN_SYNCING -> SyncingScreen()
                SCREEN_CREATE -> WaitingScreen(LinkPurpose.CreateAccount, expiresAt, onReopen = {}, onCancel = {})
                SCREEN_USERNAME -> UsernameScreen(busy = false, onSubmit = { _, _ -> }, onBack = {})
                SCREEN_LOCK -> GeekBackdrop { LockScreen(onUnlock = {}) }
                SCREEN_USAGE -> GeekBackdrop {
                    val today = java.time.LocalDate.of(2026, 9, 28)
                    val sample = listOf(310L, 820L, 145L, 0L, 1260L, 530L, 690L)
                    val service = UsageSource("sample", "سرویس ۳۰ روزه")
                    UsageScreen(
                        UsageUiState(
                            7,
                            sample.mapIndexed { i, mib -> DayUsage(today.minusDays((6 - i).toLong()), mib * 1024 * 1024) },
                            sources = listOf(UsageSource.Phone, service),
                            source = service,
                        ),
                        onRange = {},
                        onSource = {},
                        onBack = {},
                    )
                }
                SCREEN_REFERRAL -> GeekBackdrop {
                    ReferralScreen(
                        ReferralUiState(ReferralSummary("GEEK42", 7, 3, 186_000, 24_000, 20_000, 1_000, 500)),
                        onCopy = {},
                        onShare = {},
                        onRetry = {},
                        onBack = {},
                    )
                }
                SCREEN_SPEED -> GeekBackdrop {
                    SpeedTestScreen(
                        SpeedTestUiState(throughVpn = true, result = SpeedResult(pingMs = 184, downloadMbps = 42.7, uploadMbps = 11.3)),
                        onToggle = {},
                        onBack = {},
                    )
                }
                SCREEN_AUTO_CONNECT -> GeekBackdrop {
                    AutoConnectScreen(
                        AutoConnectUiState(loaded = true, startOnBoot = true, autoOnWifi = true, currentWifi = "wifi:1", trustedCount = 1, canStartInBackground = false),
                        PreviewAutoConnectActions,
                    )
                }
                SCREEN_TICKETS, SCREEN_TICKET_THREAD -> GeekBackdrop {
                    TicketsScreen(if (screen == SCREEN_TICKETS) PreviewSamples.tickets else PreviewSamples.ticketThread, PreviewTicketsActions)
                }
                in TAB_SCREENS -> TabPreview(screen)
                else -> WaitingScreen(LinkPurpose.SignIn, expiresAt, onReopen = {}, onCancel = {})
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_CREATE = "create"
        const val SCREEN_SYNCING = "syncing"
        const val SCREEN_USERNAME = "username"
        const val SCREEN_TICKETS = "tickets"
        const val SCREEN_AUTO_CONNECT = "auto-connect"
        const val SCREEN_SPEED = "speed"
        const val SCREEN_REFERRAL = "referral"
        const val SCREEN_USAGE = "usage"
        const val SCREEN_LOCK = "lock"
        const val SCREEN_TICKET_THREAD = "ticket-thread"
        val TAB_SCREENS = setOf(
            "home-off", "home-on", "home-empty", "servers", "route", "services", "account",
            "shop", "shop-guest", "checkout", "wallet", "deposit", "scanner", "scanner-running",
            "home-finding-ip", "home-attempt", "update", "update-downloading",
        )
    }
}

/** The four tabs, the servers page and the route sheet with [PreviewSamples]. */
@Composable
private fun TabPreview(screen: String) {
    val none: () -> Unit = {}
    GeekBackdrop {
        if (screen == "servers") {
            ServersScreen(
                state = PreviewSamples.homeOff,
                onBack = none,
                onSelect = {},
                onAutoServerChange = {},
                onTest = none,
                onUpdate = none,
                updating = false,
                onCleanIp = none,
            )
            return@GeekBackdrop
        }
        if (screen == "scanner" || screen == "scanner-running") {
            ScannerScreen(
                state = if (screen == "scanner") PreviewSamples.scanner else PreviewSamples.scannerRunning,
                actions = PreviewScannerActions,
                now = PreviewSamples.scannerNow,
            )
            return@GeekBackdrop
        }
        val tab = when (screen) {
            "services" -> GeekTab.Services
            "account" -> GeekTab.Account
            "shop", "shop-guest", "checkout", "wallet", "deposit" -> GeekTab.Shop
            else -> GeekTab.Home
        }
        val shop = when (screen) {
            "shop-guest" -> PreviewSamples.shopGuest
            "checkout" -> PreviewSamples.checkout
            "wallet" -> PreviewSamples.wallet
            "deposit" -> PreviewSamples.deposit
            else -> PreviewSamples.shop
        }
        val update = when (screen) {
            "update" -> UpdateState.Available(PreviewSamples.updateOffer)
            "update-downloading" -> UpdateState.Downloading(PreviewSamples.updateOffer, 0.42f)
            else -> null
        }
        val state = when (screen) {
            "home-on" -> PreviewSamples.homeOn
            "home-empty" -> PreviewSamples.homeEmpty
            "home-finding-ip" -> PreviewSamples.homeFindingIp
            "home-attempt" -> PreviewSamples.homeAttempt
            else -> PreviewSamples.homeOff
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (tab == GeekTab.Home) GeekHeader(balance = state.balance, onWallet = none)
            if (update != null) UpdateBanner(update.offer!!, onOpen = none, onClose = none)
            when (tab) {
                GeekTab.Home -> HomeScreen(
                    state = state,
                    onConnect = none,
                    onDisconnect = none,
                    onOpenServers = none,
                    onOpenRoute = none,
                    onAutoServerChange = {},
                    onChooseService = none,
                )
                GeekTab.Services -> ServicesScreen(state = state, isSignedIn = true, actions = PreviewActions)
                GeekTab.Shop -> ShopScreen(state = shop, actions = PreviewShopActions)
                GeekTab.Account -> AccountScreen(
                    state = PreviewSamples.account,
                    route = state.route,
                    autoServer = state.autoServer,
                    logoutAsked = false,
                    actions = PreviewActions,
                    showCleanIp = true,
                    update = UpdateState.UpToDate,
                    onAutoUpdate = {},
                    onTheme = {},
                    onAskLogout = {},
                    onLogout = none,
                )
            }
        }
        GeekBottomNav(selected = tab, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        when (val sheet = shop.sheet) {
            is ShopSheet.Checkout -> Box(Modifier.fillMaxSize()) {
                CheckoutSheet(sheet, PreviewSamples.shopCheckoutOptions, shop.balance, busy = false, onChoose = {}, onDismiss = none)
            }
            ShopSheet.Wallet -> Box(Modifier.fillMaxSize()) {
                WalletSheet(shop.balance, shop.wallet, busy = false, onTopup = {}, onPending = {}, onDismiss = none)
            }
            is ShopSheet.Deposit -> Box(Modifier.fillMaxSize()) {
                DepositSheet(sheet.info, uploading = false, onCopy = { _, _ -> }, onSendReceipt = none, onDismiss = none)
            }
            null -> Unit
        }
        if (update != null) {
            Box(Modifier.fillMaxSize()) {
                UpdateSheet(update, needsPermission = false, actions = PreviewUpdateActions)
            }
        }
        if (screen == "route") {
            Box(Modifier.fillMaxSize()) {
                RouteSheet(current = RouteMode.Smart, onSave = {}, onPerApp = none, onDismiss = none)
            }
        }
    }
}

/** Buttons do nothing in a preview. */
private object PreviewActions : ServicesActions, AccountActions {
    override fun onBuy() = Unit
    override fun onAddSubscription() = Unit
    override fun onImportClipboard() = Unit
    override fun onUse(subscriptionId: String) = Unit
    override fun onConnect() = Unit
    override fun onDisconnect() = Unit
    override fun onRefresh() = Unit
    override fun onRenew(subscriptionId: String) = Unit
    override fun onCopy(url: String) = Unit
    override fun onShare(url: String) = Unit
    override fun onUseManual(groupId: String) = Unit
    override fun onEditManual(groupId: String) = Unit
    override fun onWallet() = Unit
    override fun onServers() = Unit
    override fun onRoute() = Unit
    override fun onCleanIp() = Unit
    override fun onAdvanced() = Unit
    override fun onSupport() = Unit
    override fun onAbout() = Unit
    override fun onLogin() = Unit
    override fun onUpdate() = Unit
    override fun onReport() = Unit
    override fun onTickets() = Unit
    override fun onAutoConnect() = Unit
    override fun onSpeedTest() = Unit
    override fun onReferral() = Unit
    override fun onUsage() = Unit
}

private object PreviewAutoConnectActions : AutoConnectActions {
    override fun onBack() = Unit
    override fun onStartOnBoot(on: Boolean) = Unit
    override fun onAutoOnWifi(on: Boolean) = Unit
    override fun onTrustCurrent(trusted: Boolean) = Unit
    override fun onClearTrusted() = Unit
    override fun onBatterySettings() = Unit
    override fun onVpnSettings() = Unit
}

private object PreviewTicketsActions : TicketsActions {
    override fun onBack() = Unit
    override fun onRetry() = Unit
    override fun onOpen(ticketId: String) = Unit
    override fun onNew() = Unit
    override fun onReply(text: String) = Unit
    override fun onSendReply() = Unit
    override fun onTopic(topic: TicketTopic) = Unit
    override fun onSubject(text: String) = Unit
    override fun onBody(text: String) = Unit
    override fun onSubmit() = Unit
}

private object PreviewUpdateActions : UpdateActions {
    override fun onDownload() = Unit
    override fun onCancel() = Unit
    override fun onInstall() = Unit
    override fun onLater() = Unit
    override fun onDismiss() = Unit
}

private object PreviewShopActions : ShopActions {
    override fun onWallet() = Unit
    override fun onLogin() = Unit
    override fun onRetry() = Unit
    override fun onTier(tier: Tier) = Unit
    override fun onDuration(days: Int) = Unit
    override fun onPlan(planId: String) = Unit
    override fun onApplyCoupon(code: String) = Unit
    override fun onClearCoupon() = Unit
    override fun onCancelRenew() = Unit
    override fun onPay() = Unit
    override fun onTrial() = Unit
}

private object PreviewScannerActions : ScannerActions {
    override fun onBack() = Unit
    override fun onStart() = Unit
    override fun onStop() = Unit
    override fun onDownloadTest(enabled: Boolean) = Unit
    override fun onUse(ip: CleanIp) = Unit
    override fun onRevert() = Unit
}
