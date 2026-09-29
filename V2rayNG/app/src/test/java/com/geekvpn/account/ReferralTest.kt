package com.geekvpn.account

import com.geekvpn.api.ApiException
import com.geekvpn.api.ReferralSummary
import com.geekvpn.ui.referral.ReferralViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferralTest {

    @Test
    fun the_link_is_the_bots_start_parameter() {
        assertEquals("https://t.me/GeekVpnBot?start=ref_AB12", Referral.link("GeekVpnBot", "AB12"))
        assertEquals("AB12", Referral.link("", "AB12"))
    }

    @Test
    fun basis_points_become_a_percentage() {
        assertEquals(5.0, Referral.percent(500), 0.0)
        assertEquals(2.5, Referral.percent(250), 0.0)
    }

    @Test
    fun a_failed_load_can_be_retried() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var fail = true
        val vm = ReferralViewModel(
            load = {
                if (fail) throw ApiException(null, "offline")
                ReferralSummary("AB12", 3, 1, 50_000, 0, 20_000, 1_000, 500)
            },
            logFailure = {},
            scope = CoroutineScope(SupervisorJob() + dispatcher),
        )
        testScheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.failed)
        assertNull(vm.uiState.value.summary)

        fail = false
        vm.refresh()
        testScheduler.advanceUntilIdle()
        assertEquals("AB12", vm.uiState.value.summary?.code)
    }
}
