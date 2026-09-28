package com.geekvpn.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockClockTest {

    @Test
    fun off_never_locks() {
        assertFalse(LockClock().needsUnlock(enabled = false, now = 0))
    }

    @Test
    fun locked_until_the_first_unlock() {
        val clock = LockClock(graceMs = 60_000)
        assertTrue(clock.needsUnlock(enabled = true, now = 1_000))
        clock.onUnlocked()
        assertFalse(clock.needsUnlock(enabled = true, now = 2_000))
    }

    @Test
    fun a_short_trip_away_does_not_lock_a_long_one_does() {
        val clock = LockClock(graceMs = 60_000)
        clock.onUnlocked()
        clock.onBackground(now = 10_000)
        assertFalse(clock.needsUnlock(enabled = true, now = 50_000))
        clock.onBackground(now = 100_000)
        assertTrue(clock.needsUnlock(enabled = true, now = 200_000))
        // Still locked on the next screen until unlocked again.
        assertTrue(clock.needsUnlock(enabled = true, now = 200_500))
    }
}
