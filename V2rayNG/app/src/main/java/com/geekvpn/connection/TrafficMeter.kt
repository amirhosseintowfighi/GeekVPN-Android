package com.geekvpn.connection

/**
 * Download and upload speed from two readings of a byte counter.
 *
 * The counter is `TrafficStats` for this app's UID: the core's outbound
 * sockets belong to it (the `:RunSoLibV2RayDaemon` process shares the UID),
 * so it counts what the tunnel carries without asking the daemon, which has
 * no channel for per-second stats to the UI.
 */
class TrafficMeter {
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastAt = 0L

    data class Speed(val downBytesPerSecond: Long, val upBytesPerSecond: Long)

    /** Null on the first reading, or when the platform reports no counter. */
    fun sample(rxBytes: Long, txBytes: Long, atMillis: Long): Speed? {
        if (rxBytes < 0 || txBytes < 0) return null
        val previousAt = lastAt
        val previousRx = lastRx
        val previousTx = lastTx
        lastRx = rxBytes
        lastTx = txBytes
        lastAt = atMillis
        if (previousRx < 0 || atMillis <= previousAt) return null
        val seconds = (atMillis - previousAt) / 1000.0
        // A counter reset (reboot, UID counters cleared) reads as zero, not negative.
        val down = ((rxBytes - previousRx).coerceAtLeast(0) / seconds).toLong()
        val up = ((txBytes - previousTx).coerceAtLeast(0) / seconds).toLong()
        return Speed(down, up)
    }

    fun reset() {
        lastRx = -1
        lastTx = -1
        lastAt = 0
    }

    companion object {
        /** "2.48 MB/s" split into number and unit, as Home-On.html shows it. */
        fun format(bytesPerSecond: Long): Pair<String, String> = when {
            bytesPerSecond >= 1_000_000 -> String.format(java.util.Locale.US, "%.2f", bytesPerSecond / 1_000_000.0) to "MB/s"
            bytesPerSecond >= 1_000 -> (bytesPerSecond / 1_000).toString() to "KB/s"
            else -> bytesPerSecond.toString() to "B/s"
        }
    }
}
