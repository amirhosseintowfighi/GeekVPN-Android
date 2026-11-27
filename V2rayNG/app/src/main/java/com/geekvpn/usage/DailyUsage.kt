package com.geekvpn.usage

import android.net.TrafficStats
import android.os.Process
import com.geekvpn.GeekStorage
import com.tencent.mmkv.MMKV
import java.time.LocalDate

/** One day's traffic through the VPN on this phone. */
data class DayUsage(val day: LocalDate, val bytes: Long)

/**
 * "مصرف روزانه": what this phone sent and received through the VPN, per
 * local day. The backend keeps only a running total per service, so the
 * history is kept here: the VPN process adds its traffic while the core
 * runs ([Recorder]) and the app reads it. It is this phone's share, not the
 * service's total across devices.
 *
 * The core's sockets and the tunnel belong to the app's own UID, so the
 * UID's counters are the traffic to the server (a little more than the
 * payload, the protocol's overhead), which is what the quota counts too.
 */
class DailyUsage(private val storage: MMKV) {

    fun add(day: LocalDate, bytes: Long) {
        if (bytes <= 0) return
        val key = KEY_PREFIX + day
        storage.encode(key, storage.decodeLong(key, 0L) + bytes)
        prune(day)
    }

    /** The last [days] days up to [today], oldest first, with zeros for days without traffic. */
    fun lastDays(today: LocalDate, days: Int): List<DayUsage> =
        (days - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            DayUsage(day, storage.decodeLong(KEY_PREFIX + day, 0L))
        }

    /** Bytes booked from [from] to [to], both included. */
    fun total(from: LocalDate, to: LocalDate): Long {
        var sum = 0L
        var day = from
        while (!day.isAfter(to)) {
            sum += storage.decodeLong(KEY_PREFIX + day, 0L)
            day = day.plusDays(1)
        }
        return sum
    }

    /** «سقف مصرف» ([DataCap]); null when off. */
    var cap: DataCap?
        get() {
            val limit = storage.decodeLong(KEY_CAP_LIMIT, 0L)
            if (limit <= 0) return null
            val period = DataCap.Period.entries.firstOrNull { it.name == storage.decodeString(KEY_CAP_PERIOD) } ?: DataCap.Period.Month
            return DataCap(limit, period, storage.decodeBool(KEY_CAP_DISCONNECT, false))
        }
        set(value) {
            if (value == null) {
                storage.removeValuesForKeys(arrayOf(KEY_CAP_LIMIT, KEY_CAP_PERIOD, KEY_CAP_DISCONNECT))
            } else {
                storage.encode(KEY_CAP_LIMIT, value.limitBytes)
                storage.encode(KEY_CAP_PERIOD, value.period.name)
                storage.encode(KEY_CAP_DISCONNECT, value.disconnect)
            }
        }

    /** Used so far in the cap's current period. */
    fun usedInPeriod(cap: DataCap, today: LocalDate): Long = total(DataCapPolicy.periodStart(cap.period, today), today)

    /** The cap notifications already posted ([DataCapPolicy.Decision.sentKeys]). */
    var capSent: Set<String>
        get() = storage.decodeStringSet(KEY_CAP_SENT).orEmpty()
        set(value) {
            storage.encode(KEY_CAP_SENT, value)
        }

    private fun prune(today: LocalDate) {
        val oldest = today.minusDays(KEEP_DAYS.toLong())
        storage.allKeys()?.forEach { key ->
            if (!key.startsWith(KEY_PREFIX)) return@forEach
            val day = runCatching { LocalDate.parse(key.removePrefix(KEY_PREFIX)) }.getOrNull()
            if (day == null || day.isBefore(oldest)) storage.removeValueForKey(key)
        }
    }

    /**
     * Samples the UID's counters and books what moved since the last sample
     * to today. The first sample only sets the baseline; a counter that went
     * backwards (reset) books nothing.
     */
    class Recorder(
        private val book: (LocalDate, Long) -> Unit,
        private val today: () -> LocalDate = LocalDate::now,
    ) {
        private var last: Long? = null

        fun sample() = sample(uidTotal())

        @Synchronized
        fun sample(total: Long) {
            if (total < 0) return
            val previous = last
            last = total
            if (previous != null && total > previous) book(today(), total - previous)
        }

        private fun uidTotal(): Long {
            val uid = Process.myUid()
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            return if (rx < 0 || tx < 0) -1 else rx + tx
        }
    }

    companion object {
        private const val STORE_ID = "GEEK_USAGE"
        private const val KEY_PREFIX = "day|"
        const val KEEP_DAYS = 45
        private const val KEY_CAP_LIMIT = "cap_limit"
        private const val KEY_CAP_PERIOD = "cap_period"
        private const val KEY_CAP_DISCONNECT = "cap_disconnect"
        private const val KEY_CAP_SENT = "cap_sent"

        fun open(): DailyUsage = DailyUsage(GeekStorage.open(STORE_ID))
    }
}
