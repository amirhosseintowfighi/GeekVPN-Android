package com.geekvpn.account

import com.geekvpn.api.ApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshOutcomeTest {
    private fun failure(status: Int?, badBody: Boolean = false) =
        ApiFailureKind.of(ApiException(status, "test", badBody = badBody))

    @Test
    fun each_failure_names_its_cause() {
        assertEquals(ApiFailureKind.Network, failure(null))
        assertEquals(ApiFailureKind.BadResponse, failure(null, badBody = true))
        assertEquals(ApiFailureKind.Session, failure(401))
        assertEquals(ApiFailureKind.Session, failure(403))
        assertEquals(ApiFailureKind.Busy, failure(429))
        assertEquals(ApiFailureKind.Server, failure(500))
        assertEquals(ApiFailureKind.Server, failure(502))
        assertEquals(ApiFailureKind.Other, failure(404))
    }

    @Test
    fun a_quiet_refresh_speaks_only_about_problems() {
        assertFalse(RefreshOutcome(failure = null, fetched = 2).worthSaying)
        assertTrue(RefreshOutcome(failure = null, fetched = 1, fetchFailures = 1).worthSaying)
        assertTrue(RefreshOutcome(failure = ApiFailureKind.Network, fetched = 2).worthSaying)
    }
}
