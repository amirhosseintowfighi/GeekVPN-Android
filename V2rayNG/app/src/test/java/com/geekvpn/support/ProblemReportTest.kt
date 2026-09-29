package com.geekvpn.support

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProblemReportTest {
    private val facts = ReportFacts(
        appVersion = "1.3.0",
        versionCode = 42,
        android = "14",
        sdk = 34,
        device = "Samsung SM-A546E",
        network = "mobile 43235",
        mode = "vpn",
        route = "smart",
        autoServer = true,
        connected = false,
        core = "Lib v40, Xray-core v26.9.9",
        config = "vless ws none :2086 (tunnel)",
        lastFailure = "failed to dial cdn4.example.com (104.18.32.47:2086) for 2645c3a8-1111-4c2d-9e3a-90b61c2d5fe9",
        lastFailureMinutesAgo = 3,
    )

    @Test
    fun server_addresses_ids_and_links_never_leave_the_phone() {
        val report = ProblemReport.compose(
            "وصل نمی‌شود",
            facts,
            listOf(
                "E/com.geekvpn.app: import vless://2645c3a8-1111-4c2d-9e3a-90b61c2d5fe9@cdn4.example.com:2086?type=ws#x",
                "W/GoLog: reality pbk Z7d8xQ2mK4vN9pL1rT6yU3wE5aS0dF8gH2jK4lZ7 to 2001:db8::1",
            ),
        )

        for (secret in listOf("cdn4.example.com", "104.18.32.47", "2645c3a8", "vless://", "Z7d8xQ2mK4vN9pL1", "2001:db8::1")) {
            assertFalse("$secret leaked", report.contains(secret))
        }
        assertTrue(report.startsWith("وصل نمی‌شود"))
        assertTrue(report.contains("config: vless ws none :2086 (tunnel)"))
        assertTrue(report.contains("last failure (3 min ago): failed to dial <host> (<ip>:2086) for <uuid>"))
    }

    @Test
    fun code_names_and_clock_times_stay_readable() {
        assertEquals(
            "at com.geekvpn.api.GeekApi\$Refresh.authenticate(GeekApi.kt:230) at 12:34:56",
            ProblemReport.redact("at com.geekvpn.api.GeekApi\$Refresh.authenticate(GeekApi.kt:230) at 12:34:56"),
        )
        assertEquals(
            "java.io.IOException: open geoip-only-cn-private.dat",
            ProblemReport.redact("java.io.IOException: open geoip-only-cn-private.dat"),
        )
    }

    @Test
    fun the_report_fits_the_ticket_and_keeps_the_newest_log_lines() {
        val log = (1..500).map { "I/com.geekvpn.app: line $it" }
        val report = ProblemReport.compose("x".repeat(20), facts, log)

        assertTrue(report.length <= ProblemReport.MAX_CHARS)
        assertTrue(report.endsWith("line 500"))
        assertFalse(report.contains("line 1\n"))
    }
}
