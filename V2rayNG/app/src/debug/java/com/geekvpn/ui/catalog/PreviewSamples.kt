package com.geekvpn.ui.catalog

import com.geekvpn.api.AppUser
import com.geekvpn.api.PaymentCard
import com.geekvpn.api.PaymentMethodOption
import com.geekvpn.api.PaymentView
import com.geekvpn.api.Quote
import com.geekvpn.api.StoreCategory
import com.geekvpn.api.StorePlan
import com.geekvpn.api.StoreProduct
import com.geekvpn.api.Storefront
import com.geekvpn.api.TrialOffer
import com.geekvpn.api.WalletTransaction
import com.geekvpn.auth.Session
import com.geekvpn.connection.ConnectionPhase
import com.geekvpn.connection.ExitIp
import com.geekvpn.connection.RouteMode
import com.geekvpn.connection.ServiceStatus
import com.geekvpn.connection.TrafficMeter
import com.geekvpn.scanner.CdnTarget
import com.geekvpn.scanner.CleanIp
import com.geekvpn.scanner.CleanIpTarget
import com.geekvpn.scanner.IpOverride
import com.geekvpn.scanner.NetworkIdentity
import com.geekvpn.scanner.ScanRecord
import com.geekvpn.scanner.ScanState
import com.geekvpn.shop.ShopCatalog
import com.geekvpn.shop.Tier
import com.geekvpn.smartconnect.SmartStage
import com.geekvpn.ui.account.AccountUiState
import com.geekvpn.ui.account.ThemeChoice
import com.geekvpn.ui.home.HomeUiState
import com.geekvpn.ui.home.ManualGroup
import com.geekvpn.ui.home.ServerRow
import com.geekvpn.ui.scanner.ScannerUiState
import com.geekvpn.ui.shop.CheckoutPurpose
import com.geekvpn.ui.shop.DepositInfo
import com.geekvpn.ui.shop.ShopSheet
import com.geekvpn.ui.shop.ShopUiState
import com.geekvpn.ui.shop.WalletUiState
import com.v2ray.ang.R

/** Sample data for [ScreenPreviewActivity]: the design's own numbers where it has them. */
internal object PreviewSamples {
    private val servers = listOf(
        ServerRow("s1", "Germany · Frankfurt", "DE", 121),
        ServerRow("s2", "Netherlands · Amsterdam", "NL", 142),
        ServerRow("s3", "United States", "US", 168),
        ServerRow("s4", "United Kingdom", "GB", 233),
        ServerRow("s5", "France", "FR", 410),
        ServerRow("s6", "Turkey", "TR", -1),
    )

    private val service = ServiceStatus(
        subscriptionId = "fc34f6c7-2b9e-4a55-9d7e-3a1f0e2b8c11",
        title = "۴۰ گیگ · یک‌ماهه",
        active = true,
        quotaGib = 40.0,
        usedGib = 12.4,
        daysLeft = 28,
        tier = "tunnel",
        subscriptionUrl = "https://example.invalid/sub/preview",
    )

    private val expired = service.copy(
        subscriptionId = "0a9e11d2-77aa-4f5e-8c1b-5c2d3e4f5a6b",
        title = "۲۰ گیگ · یک‌ماهه",
        active = false,
        usedGib = 20.0,
        quotaGib = 20.0,
        daysLeft = 0,
        tier = "direct",
    )

    val homeOff = HomeUiState(
        phase = ConnectionPhase.Off,
        services = listOf(service, expired),
        activeService = service,
        groupId = "geek-${service.subscriptionId}",
        servers = servers,
        selected = servers.first(),
        route = RouteMode.Smart,
        balance = 185_000,
        exitIp = ExitIp("5.160.12.34", "IR"),
        manualGroups = listOf(ManualGroup("m1", "My own link", 4)),
    )

    val homeEmpty = HomeUiState(balance = 0, exitIp = ExitIp("5.160.12.34", "IR"))

    /** Smart connect's short scan, then its second connection attempt. */
    val homeFindingIp = homeOff.copy(phase = ConnectionPhase.Testing, stage = SmartStage.FindingIp)
    val homeAttempt = homeOff.copy(phase = ConnectionPhase.Connecting, stage = SmartStage.Connecting(2, 3))

    val homeOn = homeOff.copy(
        phase = ConnectionPhase.On,
        connectedSince = System.currentTimeMillis() - (12 * 60 + 48) * 1000L,
        exitIp = ExitIp("185.220.101.7", "DE"),
        speed = TrafficMeter.Speed(2_480_000, 312_000),
    )

    val account = AccountUiState(
        session = Session.SignedIn(AppUser("u1", 1011788123, "کاربر ۱۰۱۱۷۸۸۱۲۳", null, "fa", "R1", null)),
        balance = 185_000,
        autoUpdate = true,
        theme = ThemeChoice.Auto,
    )

    private fun plan(product: String, days: Int, gib: Int?, price: Long, was: Long? = null, featured: Boolean = false) =
        StorePlan("$product-$days-$gib", product, null, days, price, was, gib, 2, null, featured)

    /** Shop.html's grid: one to four months, ten to five hundred gigabytes. */
    private val tunnelPlans = listOf(30, 60, 90, 120).flatMap { days ->
        val months = days / 30
        listOf(10, 20, 40, 60, 100, 200, 500).map { gib ->
            val price = (gib * 3_500L + 40_000L) * months
            plan("tunnel", days, gib, price, was = if (gib == 40) price * 5 / 4 else null, featured = days == 30 && gib == 40)
        }
    }

    private val storefront = Storefront(
        categories = listOf(
            StoreCategory(
                "c1",
                "VPN",
                listOf(
                    StoreProduct("direct", "direct", "گیک مستقیم", null, false, listOf(plan("direct", 30, 50, 90_000))),
                    StoreProduct("tunnel", "tunnel", "گیک تونل", null, true, tunnelPlans),
                    StoreProduct("elite", "elite", "گیک ویژه", null, false, listOf(plan("elite", 30, 100, 450_000))),
                ),
            ),
        ),
        walletBalance = 185_000,
        isFirstPurchase = true,
    )

    val shop: ShopUiState = run {
        val plans = ShopCatalog.plansOf(ShopCatalog.productFor(storefront, Tier.Tunnel))
        val chosen = ShopCatalog.defaultPlan(plans)!!
        ShopUiState(
            signedIn = true,
            tiers = ShopCatalog.tiers(storefront),
            tier = Tier.Tunnel,
            durations = ShopCatalog.durations(plans),
            duration = chosen.durationDays,
            volumes = ShopCatalog.volumes(plans, chosen.durationDays!!),
            plan = chosen,
            quote = Quote(chosen.planId, chosen.compareAtPrice, chosen.price, null, 20, chosen.compareAtPrice, null),
            trial = TrialOffer(available = true, trafficMib = 50, durationDays = 2),
            methods = listOf(PaymentMethodOption("card", "کارت به کارت"), PaymentMethodOption("zarinpal", "درگاه زرین‌پال")),
            balance = 185_000,
        )
    }

    val shopGuest = ShopUiState(signedIn = false)

    /** What `ShopViewModel.optionsFor` gives a purchase with [shop]'s methods. */
    val shopCheckoutOptions = listOf(PaymentMethodOption("wallet", null)) + shop.methods

    val checkout = shop.copy(sheet = ShopSheet.Checkout(CheckoutPurpose.Purchase, shop.total ?: 0))

    val wallet = shop.copy(
        sheet = ShopSheet.Wallet,
        wallet = WalletUiState(
            pending = listOf(
                PaymentView("p1", "1405-000731", 100_073, "card", "awaiting_proof", "2026-09-24T10:12:00Z", null, PaymentCard("6037991122334455", "امیرحسین", "ملی", null)),
            ),
            transactions = listOf(
                WalletTransaction("t1", "topup", 200_000, "2026-09-20T08:00:00Z", "شارژ کیف پول"),
                WalletTransaction("t2", "purchase", 175_000, "2026-09-18T19:30:00Z", "گیک تونل ۴۰ گیگ"),
            ),
        ),
    )

    val deposit = shop.copy(
        sheet = ShopSheet.Deposit(
            DepositInfo(
                paymentId = "p1",
                amount = 100_073,
                cardNumber = "6037991122334455",
                cardHolder = "امیرحسین توفیقی",
                bank = "بانک ملی",
                reviewNote = "معمولاً کمتر از ۱۵ دقیقه بررسی می‌شود.",
            )
        ),
    )

    /** A fixed "now", two hours after the kept scan, so "last scan" reads the same every run. */
    val scannerNow = 1_790_000_000_000L

    private val cleanTarget = CleanIpTarget(
        guid = "g1",
        title = "Germany · Frankfurt",
        target = CdnTarget(sni = "de1.cdn.geekvpn.example", host = "de1.cdn.geekvpn.example", port = 443),
    )

    private val cleanIps = listOf(
        CleanIp("104.18.32.47", 443, 38, 212, 6.4, 0, "FRA"),
        CleanIp("172.64.155.20", 443, 41, 236, 9.8, 0, "FRA"),
        CleanIp("104.21.48.133", 443, 55, 298, 14.2, 0, "AMS"),
    )

    val scanner = ScannerUiState(
        target = cleanTarget,
        network = NetworkIdentity("mobile:43235", NetworkIdentity.Kind.Mobile, R.string.geek_operator_irancell),
        record = ScanRecord(cleanIps, scannerNow - 2 * 60 * 60 * 1000L),
        override = IpOverride("104.18.32.47", scannerNow - 2 * 60 * 60 * 1000L, 212),
    )

    val scannerRunning = scanner.copy(
        scan = ScanState(running = true, guid = "g1", tested = 118, total = 300, results = cleanIps.take(2)),
    )
}
