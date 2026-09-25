package com.geekvpn.smartconnect

import androidx.annotation.StringRes
import com.v2ray.ang.R

/** When a running connection counts as bad enough to move to another server. */
enum class FailoverThreshold(val key: String, val millis: Long, @param:StringRes val label: Int) {
    Off("off", -1, R.string.geek_failover_off),

    /** Only when the connection stops carrying traffic. */
    Lost("lost", 0, R.string.geek_failover_lost),
    Ms1000("1000", 1_000, R.string.geek_failover_1s),
    Ms2000("2000", 2_000, R.string.geek_failover_2s),
    Ms3000("3000", 3_000, R.string.geek_failover_3s),
    ;

    companion object {
        val DEFAULT = Ms2000

        fun of(key: String?): FailoverThreshold = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * Decides, from the running connection's delay checks, when to fail over.
 * Pure and clock-free (the caller passes `now`), so it can be tested.
 *
 * - A check is bad when it failed, or took longer than the threshold.
 * - [BAD_IN_A_ROW] bad checks in a row fail over; one slow answer does not.
 * - After a failover the next one waits a cooldown, doubled each time the
 *   connection turns bad again without a good check in between, so a network
 *   that is simply down does not make the app retest every few seconds.
 */
class FailoverPolicy(private val threshold: FailoverThreshold) {
    private var badInARow = 0
    private var cooldownMs = BASE_COOLDOWN_MS
    private var notBefore = 0L

    enum class Action { Keep, Failover }

    /** One check of the running connection: [delayMs] > 0 answered, anything else failed. */
    fun onCheck(delayMs: Long, now: Long): Action {
        if (threshold == FailoverThreshold.Off) return Action.Keep
        val bad = delayMs <= 0 || (threshold.millis > 0 && delayMs > threshold.millis)
        if (!bad) {
            badInARow = 0
            cooldownMs = BASE_COOLDOWN_MS
            return Action.Keep
        }
        badInARow++
        if (badInARow < BAD_IN_A_ROW || now < notBefore) return Action.Keep
        return Action.Failover
    }

    /** The failover ran (whether or not it found a better server). */
    fun onFailover(now: Long) {
        badInARow = 0
        notBefore = now + cooldownMs
        cooldownMs = (cooldownMs * 2).coerceAtMost(MAX_COOLDOWN_MS)
    }

    companion object {
        const val BAD_IN_A_ROW = 2
        const val BASE_COOLDOWN_MS = 2 * 60 * 1000L
        const val MAX_COOLDOWN_MS = 30 * 60 * 1000L
    }
}
