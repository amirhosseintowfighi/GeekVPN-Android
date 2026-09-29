package com.geekvpn.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRegistrationTest {
    @Test
    fun `a token never sent is sent`() {
        assertTrue(PushRegistration.needsSend(null, "u1", "tok"))
    }

    @Test
    fun `the same token for the same account is not sent again`() {
        assertFalse(PushRegistration.needsSend(PushRegistration.entry("u1", "tok"), "u1", "tok"))
    }

    @Test
    fun `a rotated token is sent`() {
        assertTrue(PushRegistration.needsSend(PushRegistration.entry("u1", "old"), "u1", "new"))
    }

    @Test
    fun `another account on the same phone is sent`() {
        assertTrue(PushRegistration.needsSend(PushRegistration.entry("u1", "tok"), "u2", "tok"))
    }
}
