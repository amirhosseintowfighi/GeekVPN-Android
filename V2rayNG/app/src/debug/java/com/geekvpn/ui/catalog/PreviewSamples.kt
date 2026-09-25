package com.geekvpn.ui.catalog

import com.geekvpn.api.AppUser
import com.geekvpn.auth.Session
import com.geekvpn.connection.ConnectionPhase
import com.geekvpn.connection.ExitIp
import com.geekvpn.connection.RouteMode
import com.geekvpn.connection.ServiceStatus
import com.geekvpn.connection.TrafficMeter
import com.geekvpn.ui.account.AccountUiState
import com.geekvpn.ui.account.ThemeChoice
import com.geekvpn.ui.home.HomeUiState
import com.geekvpn.ui.home.ManualGroup
import com.geekvpn.ui.home.ServerRow

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
}
